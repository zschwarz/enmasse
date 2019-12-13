/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.bases.clients;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressBuilder;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.systemtest.Endpoint;
import io.enmasse.systemtest.UserCredentials;
import io.enmasse.systemtest.bases.TestBase;
import io.enmasse.systemtest.bases.shared.ITestBaseShared;
import io.enmasse.systemtest.broker.ArtemisManagement;
import io.enmasse.systemtest.messagingclients.AbstractClient;
import io.enmasse.systemtest.messagingclients.ClientArgument;
import io.enmasse.systemtest.messagingclients.ClientType;
import io.enmasse.systemtest.messagingclients.ExternalClients;
import io.enmasse.systemtest.messagingclients.ExternalMessagingClient;
import io.enmasse.systemtest.model.address.AddressType;
import io.enmasse.systemtest.model.addressspace.AddressSpaceType;
import io.enmasse.systemtest.utils.AddressSpaceUtils;
import io.enmasse.systemtest.utils.AddressUtils;
import io.enmasse.systemtest.utils.TestUtils;
import io.enmasse.systemtest.utils.UserUtils;
import io.enmasse.user.model.v1.Operation;
import io.enmasse.user.model.v1.User;
import io.enmasse.user.model.v1.UserAuthorizationBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExternalClients
public abstract class ClientTestBase extends TestBase implements ITestBaseShared {
    protected Path logPath = null;
    private List<AbstractClient> clients;

    @BeforeEach
    public void setUpClientBase(TestInfo info) {
        clients = new ArrayList<>();
        String clientFolder = "clients_tests";
        logPath = environment.testLogDir().resolve(
                Paths.get(
                        clientFolder,
                        info.getTestClass().get().getName(),
                        info.getDisplayName()));
    }

    @AfterEach
    public void teardownClient() {
        if (clients != null) {
            clients.forEach(AbstractClient::stop);
            clients.clear();
        }
    }

    private Endpoint getMessagingRoute(AddressSpace addressSpace, boolean websocket) throws Exception {
        if (addressSpace.getSpec().getType().equals(AddressSpaceType.STANDARD.toString()) && websocket) {
            Endpoint messagingEndpoint = AddressSpaceUtils.getEndpointByName(addressSpace, "messaging-wss");
            if (TestUtils.resolvable(messagingEndpoint)) {
                return messagingEndpoint;
            } else {
                return kubernetes.getEndpoint("messaging-" + AddressSpaceUtils.getAddressSpaceInfraUuid(addressSpace), addressSpace.getMetadata().getNamespace(), "https");
            }
        } else {
            return getMessagingRoute(addressSpace);
        }
    }

    protected void doBasicMessageTest(AbstractClient sender, AbstractClient receiver) throws Exception {
        doBasicMessageTest(sender, receiver, false);
    }

    protected void doBasicMessageTest(AbstractClient sender, AbstractClient receiver, boolean websocket) throws Exception {
        clients.addAll(Arrays.asList(sender, receiver));
        int expectedMsgCount = 10;

        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "message-basic" + ClientType.getAddressName(sender)))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("message-basic" + ClientType.getAddressName(sender))
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        resourcesManager.setAddresses(dest);

        ExternalMessagingClient senderClient = new ExternalMessagingClient()
                .withClientEngine(sender)
                .withMessagingRoute(getMessagingRoute(getSharedAddressSpace(), websocket).toString())
                .withAddress(dest)
                .withCount(expectedMsgCount)
                .withMessageBody("msg no. %d")
                .withTimeout(30)
                .withAditionalArgument(ClientArgument.CONN_WEB_SOCKET, websocket);

        ExternalMessagingClient receiverClient = new ExternalMessagingClient()
                .withClientEngine(receiver)
                .withMessagingRoute(getMessagingRoute(getSharedAddressSpace(), websocket).toString())
                .withAddress(dest)
                .withCount(expectedMsgCount)
                .withTimeout(30)
                .withAditionalArgument(ClientArgument.CONN_WEB_SOCKET, websocket);


        assertTrue(senderClient.run(), "Sender failed, expected return code 0");
        assertTrue(receiverClient.run(), "Receiver failed, expected return code 0");

        assertEquals(expectedMsgCount, senderClient.getMessages().size(),
                String.format("Expected %d sent messages", expectedMsgCount));
        assertEquals(expectedMsgCount, receiverClient.getMessages().size(),
                String.format("Expected %d received messages", expectedMsgCount));
    }

    protected void doRoundRobinReceiverTest(ArtemisManagement artemisManagement, AbstractClient sender, AbstractClient receiver, AbstractClient receiver2)
            throws Exception {
        clients.addAll(Arrays.asList(sender, receiver, receiver2));
        int expectedMsgCount = 10;

        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "round-robin" + ClientType.getAddressName(sender)))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("round-robin" + ClientType.getAddressName(sender))
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        resourcesManager.setAddresses(dest);

        ExternalMessagingClient senderClient = new ExternalMessagingClient()
                .withClientEngine(sender)
                .withMessagingRoute(getMessagingRoute(getSharedAddressSpace()).toString())
                .withAddress(dest)
                .withCount(expectedMsgCount)
                .withMessageBody("msg no. %d")
                .withTimeout(30);

        ExternalMessagingClient receiverClient1 = new ExternalMessagingClient()
                .withClientEngine(receiver)
                .withMessagingRoute(getMessagingRoute(getSharedAddressSpace()).toString())
                .withAddress(dest)
                .withCount(expectedMsgCount / 2)
                .withTimeout(150);

        ExternalMessagingClient receiverClient2 = new ExternalMessagingClient()
                .withClientEngine(receiver2)
                .withMessagingRoute(getMessagingRoute(getSharedAddressSpace()).toString())
                .withAddress(dest)
                .withCount(expectedMsgCount / 2)
                .withTimeout(150);

        Future<Boolean> recResult = receiverClient1.runAsync();
        Future<Boolean> rec2Result = receiverClient2.runAsync();

        if (AddressSpaceUtils.isBrokered(getSharedAddressSpace())) {
            waitForSubscribers(artemisManagement, getSharedAddressSpace(), dest.getSpec().getAddress(), 2);
        } else {
            waitForSubscribersConsole(getSharedAddressSpace(), dest, 2);
        }

        assertTrue(senderClient.run(), "Sender failed, expected return code 0");
        assertTrue(recResult.get(), "Receiver failed, expected return code 0");
        assertTrue(rec2Result.get(), "Receiver failed, expected return code 0");

        assertEquals(expectedMsgCount, senderClient.getMessages().size(),
                String.format("Expected %d sent messages", expectedMsgCount));

        assertAll(
                () -> assertEquals(expectedMsgCount / 2, receiverClient1.getMessages().size(),
                        String.format("Expected %d received messages", expectedMsgCount / 2)),
                () -> assertEquals(expectedMsgCount / 2, receiverClient2.getMessages().size(),
                        String.format("Expected %d sent messages", expectedMsgCount / 2)));
    }

    protected void doTopicSubscribeTest(ArtemisManagement artemisManagement, AbstractClient sender, AbstractClient subscriber, AbstractClient subscriber2,
                                        boolean hasTopicPrefix) throws Exception {
        clients.addAll(Arrays.asList(sender, subscriber, subscriber2));
        int expectedMsgCount = 10;

        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "topic-sub" + ClientType.getAddressName(sender)))
                .endMetadata()
                .withNewSpec()
                .withType("topic")
                .withAddress("topic-sub" + ClientType.getAddressName(sender))
                .withPlan(getDefaultPlan(AddressType.TOPIC))
                .endSpec()
                .build();
        resourcesManager.setAddresses(dest);

        arguments.put(ClientArgument.BROKER, getMessagingRoute(getSharedAddressSpace()).toString());
        arguments.put(ClientArgument.ADDRESS, AddressUtils.getTopicPrefix(hasTopicPrefix) + dest.getSpec().getAddress());
        arguments.put(ClientArgument.COUNT, Integer.toString(expectedMsgCount));
        arguments.put(ClientArgument.MSG_CONTENT, "msg no. %d");
        arguments.put(ClientArgument.TIMEOUT, "150");

        ExternalMessagingClient senderClient = new ExternalMessagingClient()
                .withClientEngine(sender)
                .withMessagingRoute(getMessagingRoute(getSharedAddressSpace()).toString())
                .withAddress(dest)
                .withCount(expectedMsgCount)
                .withMessageBody("msg no. %d")
                .withTimeout(30);

        ExternalMessagingClient receiverClient1 = new ExternalMessagingClient()
                .withClientEngine(subscriber)
                .withMessagingRoute(getMessagingRoute(getSharedAddressSpace()).toString())
                .withAddress(dest)
                .withCount(expectedMsgCount)
                .withTimeout(150);

        ExternalMessagingClient receiverClient2 = new ExternalMessagingClient()
                .withClientEngine(subscriber2)
                .withMessagingRoute(getMessagingRoute(getSharedAddressSpace()).toString())
                .withAddress(dest)
                .withCount(expectedMsgCount)
                .withTimeout(150);

        sender.setArguments(arguments);
        arguments.remove(ClientArgument.MSG_CONTENT);
        subscriber.setArguments(arguments);
        subscriber2.setArguments(arguments);

        Future<Boolean> recResult = subscriber.runAsync();
        Future<Boolean> recResult2 = subscriber2.runAsync();

        if (AddressSpaceUtils.isBrokered(getSharedAddressSpace())) {
            waitForSubscribers(artemisManagement, getSharedAddressSpace(), dest.getSpec().getAddress(), 2);
        } else {
            waitForSubscribersConsole(getSharedAddressSpace(), dest, 2);
        }

        assertAll(
                () -> assertTrue(sender.run(), "Producer failed, expected return code 0"),
                () -> assertEquals(expectedMsgCount, sender.getMessages().size(),
                        String.format("Expected %d sent messages", expectedMsgCount)));
        assertAll(
                () -> assertTrue(recResult.get(), "Subscriber failed, expected return code 0"),
                () -> assertTrue(recResult2.get(), "Subscriber failed, expected return code 0"),
                () -> assertEquals(expectedMsgCount, subscriber.getMessages().size(),
                        String.format("Expected %d received messages", expectedMsgCount)),
                () -> assertEquals(expectedMsgCount, subscriber2.getMessages().size(),
                        String.format("Expected %d received messages", expectedMsgCount)));
    }

    protected void doMessageBrowseTest(AbstractClient sender, AbstractClient receiver_browse, AbstractClient receiver_receive)
            throws Exception {
        clients.addAll(Arrays.asList(sender, receiver_browse, receiver_receive));
        int expectedMsgCount = 10;

        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "browse" + ClientType.getAddressName(sender)))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("browse" + ClientType.getAddressName(sender))
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        resourcesManager.setAddresses(dest);

        arguments.put(ClientArgument.BROKER, getMessagingRoute(getSharedAddressSpace()).toString());
        arguments.put(ClientArgument.ADDRESS, dest.getSpec().getAddress());
        arguments.put(ClientArgument.COUNT, Integer.toString(expectedMsgCount));
        arguments.put(ClientArgument.MSG_CONTENT, "msg no. %d");

        sender.setArguments(arguments);
        arguments.remove(ClientArgument.MSG_CONTENT);

        arguments.put(ClientArgument.RECV_BROWSE, "true");
        receiver_browse.setArguments(arguments);

        arguments.put(ClientArgument.RECV_BROWSE, "false");
        receiver_receive.setArguments(arguments);

        assertAll(
                () -> assertTrue(sender.run(), "Sender failed, expected return code 0"),
                () -> assertEquals(expectedMsgCount, sender.getMessages().size(),
                        String.format("Expected %d sent messages", expectedMsgCount)));
        assertAll(
                () -> assertTrue(receiver_browse.run(), "Browse receiver failed, expected return code 0"),
                () -> assertTrue(receiver_receive.run(), "Receiver failed, expected return code 0"),
                () -> assertEquals(expectedMsgCount, receiver_browse.getMessages().size(),
                        String.format("Expected %d browsed messages", expectedMsgCount)),
                () -> assertEquals(expectedMsgCount, receiver_receive.getMessages().size(),
                        String.format("Expected %d received messages", expectedMsgCount)));
    }

    protected void doDrainQueueTest(AbstractClient sender, AbstractClient receiver) throws Exception {
        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "drain" + ClientType.getAddressName(sender)))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("drain" + ClientType.getAddressName(sender))
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        resourcesManager.setAddresses(dest);

        clients.addAll(Arrays.asList(sender, receiver));
        int expectedMsgCount = 50;

        arguments.put(ClientArgument.BROKER, getMessagingRoute(getSharedAddressSpace()).toString());
        arguments.put(ClientArgument.ADDRESS, dest.getSpec().getAddress());
        arguments.put(ClientArgument.COUNT, Integer.toString(expectedMsgCount));
        arguments.put(ClientArgument.MSG_CONTENT, "msg no. %d");

        sender.setArguments(arguments);
        arguments.remove(ClientArgument.MSG_CONTENT);

        arguments.put(ClientArgument.COUNT, "0");
        arguments.put(ClientArgument.TIMEOUT, "10");  // In seconds, maximum time the consumer waits for a single message
        receiver.setArguments(arguments);

        assertTrue(sender.run(), "Sender failed, expected return code 0");
        assertTrue(receiver.run(), "Drain receiver failed, expected return code 0");

        assertEquals(expectedMsgCount, sender.getMessages().size(),
                String.format("Expected %d sent messages", expectedMsgCount));
        assertEquals(expectedMsgCount, receiver.getMessages().size(),
                String.format("Expected %d received messages", expectedMsgCount));
    }

    protected void doMessageSelectorQueueTest(AbstractClient sender, AbstractClient receiver) throws Exception {
        int expectedMsgCount = 10;

        clients.addAll(Arrays.asList(sender, receiver));
        Address queue = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "selector-queue" + ClientType.getAddressName(sender)))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("selector-queue" + ClientType.getAddressName(sender))
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        resourcesManager.setAddresses(queue);

        arguments.put(ClientArgument.BROKER, getMessagingRoute(getSharedAddressSpace()).toString());
        arguments.put(ClientArgument.COUNT, Integer.toString(expectedMsgCount));
        arguments.put(ClientArgument.ADDRESS, queue.getSpec().getAddress());
        arguments.put(ClientArgument.MSG_PROPERTY, "colour~red");
        arguments.put(ClientArgument.MSG_PROPERTY, "number~12.65");
        arguments.put(ClientArgument.MSG_PROPERTY, "a~true");
        arguments.put(ClientArgument.MSG_PROPERTY, "b~false");
        arguments.put(ClientArgument.MSG_CONTENT, "msg no. %d");

        //send messages
        sender.setArguments(arguments);
        assertTrue(sender.run(), "Sender failed, expected return code 0");
        assertEquals(expectedMsgCount, sender.getMessages().size(),
                String.format("Expected %d sent messages", expectedMsgCount));

        arguments.remove(ClientArgument.MSG_PROPERTY);
        arguments.remove(ClientArgument.MSG_CONTENT);
        arguments.put(ClientArgument.RECV_BROWSE, "true");
        arguments.put(ClientArgument.COUNT, "0");

        //receiver with selector colour = red
        arguments.put(ClientArgument.SELECTOR, "colour = 'red'");
        receiver.setArguments(arguments);
        final Executable executable = () -> assertEquals(expectedMsgCount, receiver.getMessages().size(),
                String.format("Expected %d received messages 'colour = red'", expectedMsgCount));
        assertAll(
                () -> assertTrue(receiver.run(), "Receiver 'colour = red' failed, expected return code 0"),
                executable);

        //receiver with selector number > 12.5
        arguments.put(ClientArgument.SELECTOR, "number > 12.5");
        receiver.setArguments(arguments);
        assertAll(
                () -> assertTrue(receiver.run(), "Receiver 'number > 12.5' failed, expected return code 0"),
                executable);


        //receiver with selector a AND b
        arguments.put(ClientArgument.SELECTOR, "a AND b");
        receiver.setArguments(arguments);
        assertAll(
                () -> assertTrue(receiver.run(), "Receiver 'a AND b' failed, expected return code 0"),
                () -> assertEquals(0, receiver.getMessages().size(),
                        String.format("Expected %d received messages 'a AND b'", 0)));

        //receiver with selector a OR b
        arguments.put(ClientArgument.RECV_BROWSE, "false");
        arguments.put(ClientArgument.SELECTOR, "a OR b");
        receiver.setArguments(arguments);
        assertAll(
                () -> assertTrue(receiver.run(), "Receiver 'a OR b' failed, expected return code 0"),
                () -> assertEquals(expectedMsgCount, receiver.getMessages().size(),
                        String.format("Expected %d received messages 'a OR b'", expectedMsgCount)));
    }

    protected void doMessageSelectorTopicTest(ArtemisManagement artemisManagement, AbstractClient sender, AbstractClient sender2,
                                              AbstractClient subscriber, AbstractClient subscriber2, boolean hasTopicPrefix) throws Exception {
        clients.addAll(Arrays.asList(sender, sender2, subscriber, subscriber2));
        int expectedMsgCount = 5;

        Address topic = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "selector-topic" + ClientType.getAddressName(sender)))
                .endMetadata()
                .withNewSpec()
                .withType("topic")
                .withAddress("selector-topic" + ClientType.getAddressName(sender))
                .withPlan(getDefaultPlan(AddressType.TOPIC))
                .endSpec()
                .build();
        resourcesManager.setAddresses(topic);

        arguments.put(ClientArgument.BROKER, getMessagingRoute(getSharedAddressSpace()).toString());
        arguments.put(ClientArgument.COUNT, Integer.toString(expectedMsgCount));
        arguments.put(ClientArgument.ADDRESS, AddressUtils.getTopicPrefix(hasTopicPrefix) + topic.getSpec().getAddress());
        arguments.put(ClientArgument.MSG_PROPERTY, "colour~red");
        arguments.put(ClientArgument.MSG_PROPERTY, "number~12.65");
        arguments.put(ClientArgument.MSG_PROPERTY, "a~true");
        arguments.put(ClientArgument.MSG_PROPERTY, "b~false");
        arguments.put(ClientArgument.TIMEOUT, "150");
        arguments.put(ClientArgument.MSG_CONTENT, "msg no. %d");

        //set up senders
        sender.setArguments(arguments);

        arguments.remove(ClientArgument.MSG_PROPERTY);
        arguments.put(ClientArgument.MSG_PROPERTY, "colour~blue");
        arguments.put(ClientArgument.MSG_PROPERTY, "number~11.65");

        sender2.setArguments(arguments);

        arguments.remove(ClientArgument.MSG_PROPERTY);
        arguments.remove(ClientArgument.MSG_CONTENT);

        //set up subscriber1
        arguments.put(ClientArgument.SELECTOR, "colour = 'red' AND a");
        subscriber.setArguments(arguments);

        //set up subscriber2
        arguments.put(ClientArgument.SELECTOR, "number < 12.5");
        subscriber2.setArguments(arguments);

        Future<Boolean> result1 = subscriber.runAsync();
        Future<Boolean> result2 = subscriber2.runAsync();

        if (AddressSpaceUtils.isBrokered(getSharedAddressSpace())) {
            waitForSubscribers(artemisManagement, getSharedAddressSpace(), topic.getSpec().getAddress(), 2);
        } else {
            waitForSubscribersConsole(getSharedAddressSpace(), topic, 2);
        }

        assertTrue(sender.run(), "Sender failed, expected return code 0");
        assertTrue(sender2.run(), "Sender2 failed, expected return code 0");
        assertTrue(result1.get(), "Receiver 'colour = red' failed, expected return code 0");
        assertTrue(result2.get(), "Receiver 'number < 12.5' failed, expected return code 0");

        assertEquals(expectedMsgCount, sender.getMessages().size(),
                String.format("Expected %d sent messages", expectedMsgCount));
        assertEquals(expectedMsgCount, sender2.getMessages().size(),
                String.format("Expected %d sent messages", expectedMsgCount));
        assertEquals(expectedMsgCount, subscriber.getMessages().size(),
                String.format("Expected %d received messages 'colour = red' AND a", expectedMsgCount));
        assertEquals(expectedMsgCount, subscriber2.getMessages().size(),
                String.format("Expected %d received messages 'number < 12.5'", expectedMsgCount));
    }

    protected void doTestUserPermissions(AbstractClient sender, AbstractClient receiver) throws Exception {
        int expectedMsgCount = 5;
        UserCredentials publishCred = new UserCredentials("publisher", "publish");
        UserCredentials consumCred = new UserCredentials("consumer", "consume");
        createPublisherAndConsumer(publishCred, consumCred);

        Address dest = new AddressBuilder()
                .withNewMetadata()
                .withNamespace(getSharedAddressSpace().getMetadata().getNamespace())
                .withName(AddressUtils.generateAddressMetadataName(getSharedAddressSpace(), "message-basic" + ClientType.getAddressName(sender)))
                .endMetadata()
                .withNewSpec()
                .withType("queue")
                .withAddress("message-basic" + ClientType.getAddressName(sender))
                .withPlan(getDefaultPlan(AddressType.QUEUE))
                .endSpec()
                .build();
        resourcesManager.setAddresses(dest);

        arguments.put(ClientArgument.BROKER, getMessagingRoute(getSharedAddressSpace(), false).toString());
        arguments.put(ClientArgument.ADDRESS, dest.getSpec().getAddress());
        arguments.put(ClientArgument.COUNT, Integer.toString(expectedMsgCount));
        arguments.put(ClientArgument.MSG_CONTENT, "msg no. %d");
        arguments.put(ClientArgument.TIMEOUT, "30");
        replaceCredArgs(consumCred);
        sender.setArguments(arguments);
        arguments.remove(ClientArgument.MSG_CONTENT);
        replaceCredArgs(publishCred);
        receiver.setArguments(arguments);

        assertAll(
                () -> assertFalse(sender.run(), "Sender failed. Specified user is not allowed to write"),
                () -> assertFalse(receiver.run(), "Receiver failed. Specified user is not allowed to read"));
        replaceCredArgs(publishCred);
        arguments.put(ClientArgument.MSG_CONTENT, "msg no. %d");
        sender.setArguments(arguments);
        arguments.remove(ClientArgument.MSG_CONTENT);
        replaceCredArgs(consumCred);
        receiver.setArguments(arguments);

        assertTrue(sender.run(), "Sender failed, expected return code 0");
        assertTrue(receiver.run(), "Receiver failed, expected return code 0");

        assertEquals(expectedMsgCount, sender.getMessages().size(),
                String.format("Expected %d sent messages", expectedMsgCount));
        assertEquals(expectedMsgCount, receiver.getMessages().size(),
                String.format("Expected %d received messages", expectedMsgCount));
    }

    private void createPublisherAndConsumer(UserCredentials publishCred, UserCredentials consumCred) throws Exception {
        User publisher = (UserUtils.createUserResource(publishCred)
                .editSpec()
                .withAuthorization(Collections.singletonList(new UserAuthorizationBuilder()
                        .withAddresses("*")
                        .withOperations(Operation.send)
                        .build()))
                .endSpec()
                .done());

        User consumer = (UserUtils.createUserResource(consumCred)
                .editSpec()
                .withAuthorization(Collections.singletonList(new UserAuthorizationBuilder()
                        .withAddresses("*")
                        .withOperations(Operation.recv)
                        .build()))
                .endSpec()
                .done());

        resourcesManager.createOrUpdateUser(getSharedAddressSpace(), publisher);
        resourcesManager.createOrUpdateUser(getSharedAddressSpace(), consumer);
    }

}
