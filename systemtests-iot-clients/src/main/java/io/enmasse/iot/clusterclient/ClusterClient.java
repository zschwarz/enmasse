/*
 * Copyright 2016-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.clusterclient;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.proton.*;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ClusterClient extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(ClusterClient.class);

    private final String messagingHost;
    private final int messagingPort;
    private final String username;
    private final String password;
    private final String serverCert;
    private final String address;
    private ProtonConnection connection;
    private int counter;

    public ClusterClient(String messagingHost, int messagingPort, String username, String password, String serverCert, String address) {
        this.messagingHost = messagingHost;
        this.messagingPort = messagingPort;
        this.username = username;
        this.password = password;
        this.serverCert = serverCert;
        this.address = address;
    }

    @Override
    public void start(Future<Void> startPromise) {
        counter = 0;
        ProtonClient alarmClient = ProtonClient.create(vertx);
        ProtonClientOptions options = new ProtonClientOptions();
        if (serverCert != null) {
            options.setPemTrustOptions(new PemTrustOptions()
                    .addCertValue(Buffer.buffer(serverCert)))
                    .setSsl(true)
                    .setHostnameVerificationAlgorithm("");
        }
        alarmClient.connect(options, messagingHost, messagingPort, username, password, connection -> {
            if (connection.succeeded()) {
                log.info("Connected to {}:{}", messagingHost, messagingPort);
                ProtonConnection connectionHandle = connection.result();
                connectionHandle.open();

                ProtonReceiver receiver = connectionHandle.createReceiver(address);
                receiver.handler(this::handleMessages);
                receiver.openHandler(link -> {
                    if (link.succeeded()) {
                        startPromise.complete();
                    } else {
                        log.info("Error attaching to {}", address, link.cause());
                        startPromise.fail(link.cause());
                    }
                });
                receiver.open();
                this.connection = connectionHandle;
                sendCommand(counter);
            } else {
                log.info("Error connecting to {}:{}", messagingHost, messagingPort);
                startPromise.fail(connection.cause());
            }
        });
    }

    private void handleMessages(ProtonDelivery delivery, Message message) {
        log.info("Received message");
        log.info(message.getBody().toString());
        sendCommand(++counter);
    }

    private void sendCommand(int id) {
        ProtonSender sender = connection.createSender(address);

        Message message = Message.Factory.create();

        message.setAddress(address);
        message.setBody(new AmqpValue(String.format("Message no. {}", id)));

        sender.openHandler(link -> {
            if (link.succeeded()) {
                log.info("Sending control to {}: {}", address, id);
                sender.send(message, delivery -> {
                    log.info("... sent {}", new String(delivery.getTag()));
                    sender.close();
                });
            } else {
                log.info("Error sending control message");
            }
        });
        sender.closeHandler(link -> sender.close());
        sender.open();
    }

    public static void main(String[] args) throws Exception {
        Properties properties = loadProperties("config.properties");
        AppCredentials appCredentials = AppCredentials.create();

        String address = properties.getProperty("address.name", "examples");

        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new ClusterClient(appCredentials.getHostname(), appCredentials.getPort(), appCredentials.getUsername(), appCredentials.getPassword(), appCredentials.getX509Certificate(), address));
    }

    private static Properties loadProperties(String resource) throws IOException {
        Properties properties = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream stream = loader.getResourceAsStream(resource);
        properties.load(stream);
        return properties;
    }
}
