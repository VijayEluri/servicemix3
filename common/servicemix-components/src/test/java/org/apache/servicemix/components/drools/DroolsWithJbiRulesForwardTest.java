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
package org.apache.servicemix.components.drools;

import javax.xml.namespace.QName;

import org.springframework.context.support.AbstractXmlApplicationContext;

/**
 * @version $Revision$
 */
public class DroolsWithJbiRulesForwardTest extends DroolsTest {

    public void testFiringRules() throws Exception {
        QName service = new QName("http://servicemix.org/cheese/", "droolsRouter");

        sendMessages(service, 3);
        assertMessagesReceived(4);
    }

    protected AbstractXmlApplicationContext createBeanFactory() {
        return new org.apache.xbean.spring.context.ClassPathXmlApplicationContext("org/apache/servicemix/components/drools/jbi-example-forward.xml");
    }

}
