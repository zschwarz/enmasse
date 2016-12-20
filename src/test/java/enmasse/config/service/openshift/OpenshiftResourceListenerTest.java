/*
 * Copyright 2016 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package enmasse.config.service.openshift;

import enmasse.config.service.TestResource;
import enmasse.config.service.model.Subscriber;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OpenshiftResourceListenerTest {

    @Captor
    private ArgumentCaptor<Message> messageCaptor;

    @Test
    public void testSubscribing() throws IOException {
        MessageEncoder encoder = set -> {
            Message message = Message.Factory.create();
            message.setBody(new AmqpValue("test"));
            return message;
        };
        OpenshiftResourceListener listener = new OpenshiftResourceListener(encoder);
        Subscriber mockSub = mock(Subscriber.class);
        listener.subscribe(mockSub);
        listener.resourcesUpdated(Collections.singleton(new TestResource("t1", Collections.singletonMap("key1", "value1"), "v1")));

        verify(mockSub).resourcesUpdated(messageCaptor.capture());

        Message message = messageCaptor.getValue();
        assertThat(((AmqpValue)message.getBody()).getValue(), is("test"));

        clearInvocations(mockSub);
        listener.resourcesUpdated(Collections.singleton(new TestResource("t2", Collections.singletonMap("key1", "value1"), "v2")));
        verify(mockSub).resourcesUpdated(messageCaptor.capture());
        message = messageCaptor.getValue();
        assertThat(((AmqpValue)message.getBody()).getValue(), is("test"));

        clearInvocations(mockSub);
        listener.resourcesUpdated(Collections.singleton(new TestResource("t2", Collections.singletonMap("key1", "value1"), "v2")));
        verifyZeroInteractions(mockSub);
    }
}
