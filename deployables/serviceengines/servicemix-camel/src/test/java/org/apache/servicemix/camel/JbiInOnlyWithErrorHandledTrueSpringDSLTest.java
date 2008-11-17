/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.camel;

import java.util.List;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.xml.namespace.QName;

import org.apache.servicemix.MessageExchangeListener;
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.components.util.ComponentSupport;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.apache.servicemix.tck.ReceiverComponent;

/**
 * Tests on handling fault messages with the Camel Exception handler
 */
public class JbiInOnlyWithErrorHandledTrueSpringDSLTest extends SpringJbiTestSupport {

    private static final QName TEST_SERVICE = new QName("urn:test", "error-handled-true");

    private ReceiverComponent receiver;
    private ReceiverComponent deadLetter;

    protected void setUp() throws Exception {
        receiver = new ReceiverComponent();
        deadLetter = new ReceiverComponent();

        super.setUp();
    }

    public void testErrorHandledByExceptionClause() throws Exception {
        ServiceMixClient smxClient = getServicemixClient();

        MessageExchange[] exchanges = new MessageExchange[] {smxClient.createInOnlyExchange(), smxClient.createRobustInOnlyExchange()};
        for (MessageExchange exchange : exchanges) {
            exchange.setService(TEST_SERVICE);
    
            smxClient.send(exchange);
    
            exchange = smxClient.receive();
            assertEquals(ExchangeStatus.DONE, exchange.getStatus());
        }
    
        receiver.getMessageList().assertMessagesReceived(2);
        deadLetter.getMessageList().assertMessagesReceived(0); 
    }

    @Override
    protected String getServiceUnitName() {
        return "su8";
    }

    @Override
    protected void appendJbiActivationSpecs(List<ActivationSpec> activationSpecList) {
        activationSpecList.add(createActivationSpec(new ReturnNullPointerExceptionErrorComponent(), 
                                                    new QName("urn:test", "npe-error-service")));

        activationSpecList.add(createActivationSpec(receiver, new QName("urn:test", "receiver-service")));
        activationSpecList.add(createActivationSpec(deadLetter, new QName("urn:test", "deadLetter-service")));
    }

    protected static class ReturnNullPointerExceptionErrorComponent extends ComponentSupport implements MessageExchangeListener {
        public void onMessageExchange(MessageExchange exchange) throws MessagingException {
            if (exchange.getStatus() == ExchangeStatus.ACTIVE) {
                fail(exchange, new NullPointerException());
            }
        }
    }
}
