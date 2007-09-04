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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.xml.namespace.QName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.apache.servicemix.jbi.resolver.URIResolver;
import org.apache.servicemix.tck.SenderComponent;

/**
 * @version $Revision: 563665 $
 */
public class SendFromCamelToJbiAndBackToCamelTest extends JbiTestSupport {
    protected SenderComponent senderComponent = new SenderComponent();

    public void testCamelInvokingJbi() throws Exception {
        senderComponent.sendMessages(1);

        SedaEndpoint receiverEndpoint = (SedaEndpoint) camelContext
                .getEndpoint("seda:receiver");

        BlockingQueue<SedaEndpoint.Entry> queue = receiverEndpoint.getQueue();
        SedaEndpoint.Entry entry = queue.poll(5, TimeUnit.SECONDS);

        assertNotNull(
                "Camel Receiver queue should have received an exchange by now",
                entry);

        log.debug("Receiver got exchange: " + entry.getExchange() + " with body: "
                + entry.getExchange().getIn().getBody());
    }

    @Override
    protected RouteBuilder createRoutes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no routes required
            }
        };
    }

    @Override
    protected void appendJbiActivationSpecs(
            List<ActivationSpec> activationSpecList) {
        this.startEndpointUri = "seda:receiver";

        ActivationSpec activationSpec = new ActivationSpec();
        activationSpec.setId("jbiSender");
        activationSpec.setService(new QName("serviceNamespace", "serviceA"));
        activationSpec.setEndpoint("endpointA");

        // lets setup the sender to talk directly to camel
        senderComponent.setResolver(new URIResolver("camel:seda:receiver"));
        activationSpec.setComponent(senderComponent);

        activationSpecList.add(activationSpec);
    }

    @Override
    protected void tearDown() throws Exception {
        camelContext.stop();
    }
}
