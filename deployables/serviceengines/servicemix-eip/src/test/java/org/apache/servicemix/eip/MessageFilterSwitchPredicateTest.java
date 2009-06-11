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
package org.apache.servicemix.eip;

import java.net.URL;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.eip.patterns.MessageFilter;
import org.apache.servicemix.eip.support.SwitchPredicate;
import org.apache.servicemix.tck.ReceiverComponent;
import org.springframework.core.io.FileSystemResource;

// Test for SwitchPredicate methods.  Switch can be turned on/off with a
// property file, a property on the message exchange, or a system property.
public class MessageFilterSwitchPredicateTest extends AbstractEIPTest {
    
    private static Log log = LogFactory.getLog(MessageFilterSwitchPredicateTest.class);
    protected MessageFilter messageFilter;
    protected SwitchPredicate predicate;

    protected void setUp() throws Exception {
        super.setUp();

        messageFilter = new MessageFilter();
        predicate = new SwitchPredicate();

    }

    protected void tearDown() throws Exception {
        messageFilter = null;
        predicate = null;

        super.tearDown();
    }

    // Test for switch predicate on/off when set on the message exchange.
    public void testPropertyOnExchange() throws Exception {
        InOnly me = client.createInOnlyExchange();
        me.setProperty("on", Boolean.FALSE);
        predicate.setFromExchange(true);
        predicate.setPropertyName("on");

        messageFilter.setFilter(predicate);
        messageFilter.setTarget(createServiceExchangeTarget(new QName("target")));
        configurePattern(messageFilter);
        activateComponent(messageFilter, "messageFilter");
        ReceiverComponent rec = activateReceiver("target");
        me.setService(new QName("messageFilter"));

        // Message exchange turned off - message should NOT reach the target.
        me.getInMessage().setContent(createSource("<orderIn><widget>5</widget></orderIn>"));
        me.getInMessage().setProperty("on", Boolean.FALSE);
        log.info("Before client.sendSync me.getProperty(\"on\") is: " + me.getProperty("on"));
        client.sendSync(me);
        log.info("After client.sendSync me.getProperty(\"on\" is: " + me.getProperty("on"));
        assertEquals("Message exchange status should be DONE", ExchangeStatus.DONE, me.getStatus());

        rec.getMessageList().assertMessagesReceived(0);

/*
 * Comment this part of the test out until SMXCOMP-553 is fixed.        
        // Message exchange turned on - message should reach the target.
        me = client.createInOnlyExchange();
        me.setProperty("on", Boolean.TRUE);
        predicate.setFromExchange(true);
        predicate.setPropertyName("on");
        
        messageFilter.setFilter(predicate);
        configurePattern(messageFilter);
        me.setService(new QName("messageFilter"));
        
        me.getInMessage().setContent(createSource("<orderIn><widget>5</widget></orderIn>"));
        me.getInMessage().setProperty("on", Boolean.TRUE);
        log.info("Before client.sendSync me.getProperty(\"on\") is: " + me.getProperty("on"));
        client.sendSync(me);
        log.info("After client.sendSync me.getProperty(\"on\" is: " + me.getProperty("on"));
        assertEquals("Message exchange status should be DONE", ExchangeStatus.DONE, me.getStatus());
        
        rec.getMessageList().assertMessagesReceived(1);
*/
    }

    // Test switch predicate on and off when property value comes from a file.
    public void testPropertyFromFile() throws Exception {
        InOnly me = client.createInOnlyExchange();

        URL fileUrl = getClass().getResource("switch-off.properties");
        FileSystemResource propFile = new FileSystemResource(fileUrl.getFile());
        predicate.setPropertyResource(propFile);
        predicate.setPropertyName("on");

        // Message exchange turned off - message should not reach the target.
        messageFilter.setFilter(predicate);
        messageFilter.setTarget(createServiceExchangeTarget(new QName("target")));
        configurePattern(messageFilter);
        activateComponent(messageFilter, "messageFilter");
        ReceiverComponent rec = activateReceiver("target");
        me.setService(new QName("messageFilter"));

        me.getInMessage().setContent(createSource("<orderIn><gadget>10</gadget></orderIn>"));
        client.sendSync(me);
        assertEquals("Message exchange status should be DONE", ExchangeStatus.DONE, me.getStatus());

        rec.getMessageList().assertMessagesReceived(0);

        // Message exchange turned on - message should reach the target.
        me = client.createInOnlyExchange();
        fileUrl = getClass().getResource("switch.properties");
        propFile = new FileSystemResource(fileUrl.getFile());
        predicate.setPropertyResource(propFile);
        predicate.setPropertyName("on");

        messageFilter.setFilter(predicate);
        me.setService(new QName("messageFilter"));

        me.getInMessage().setContent(createSource("<orderIn><widget>5</widget></orderIn>"));
        client.sendSync(me);
        assertEquals("Message exchange status should be DONE", ExchangeStatus.DONE, me.getStatus());

        rec.getMessageList().assertMessagesReceived(1);

    }

    // Test switch predicate on and off when property is a system property.
    public void testSystemProperty() throws Exception {
        System.setProperty("on", "false");

        InOnly me = client.createInOnlyExchange();

        predicate.setPropertyName("on");

        // Message exchange turned off - message should not reach the target.
        messageFilter.setFilter(predicate);
        messageFilter.setTarget(createServiceExchangeTarget(new QName("target")));
        configurePattern(messageFilter);
        activateComponent(messageFilter, "messageFilter");
        ReceiverComponent rec = activateReceiver("target");
        me.setService(new QName("messageFilter"));

        me.getInMessage().setContent(createSource("<orderIn><gadget>10</gadget></orderIn>"));
        client.sendSync(me);
        assertEquals("Message exchange status should be DONE", ExchangeStatus.DONE, me.getStatus());

        rec.getMessageList().assertMessagesReceived(0);

        // Need to clear the previously set System property.
        System.clearProperty("on");
        System.setProperty("on", "true");

        me = client.createInOnlyExchange();

        predicate.setPropertyName("on");

        // Message exchange turned on - message should reach the target.
        messageFilter.setFilter(predicate);
        messageFilter.setTarget(createServiceExchangeTarget(new QName("target")));
        configurePattern(messageFilter);
        me.setService(new QName("messageFilter"));

        me.getInMessage().setContent(createSource("<orderIn><gadget>10</gadget></orderIn>"));
        client.sendSync(me);
        assertEquals("Message exchange status should be DONE", ExchangeStatus.DONE, me.getStatus());

        rec.getMessageList().assertMessagesReceived(1);
    }
}
