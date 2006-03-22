/*
 * Copyright 2005-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.jms;

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.servicemix.common.ExchangeProcessor;

public abstract class AbstractJmsProcessor implements ExchangeProcessor {

    public static final String STYLE_QUEUE = "queue";
    public static final String STYLE_TOPIC = "topic";

    protected JmsEndpoint endpoint;
    protected Connection connection;

    public AbstractJmsProcessor(JmsEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void start() throws Exception {
        Hashtable props = new Hashtable();
        if (endpoint.getInitialContextFactory() != null && endpoint.getJndiProviderURL() != null) {
            props.put(Context.INITIAL_CONTEXT_FACTORY, endpoint.getInitialContextFactory());
            props.put(Context.PROVIDER_URL, endpoint.getJndiProviderURL());
        }
        InitialContext ctx = new InitialContext(props);
        try {
            ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup(endpoint.getJndiConnectionFactoryName());
            connection = connectionFactory.createConnection();
            connection.start();
            doStart(ctx);
        } catch (Exception e) {
            try {
                stop();
            } catch (Exception inner) {
                // TODO: log
            }
            throw e;
        } finally {
            ctx.close();
        }
    }


    protected void doStart(InitialContext ctx) throws Exception {
    }

    public void stop() throws Exception {
        try {
            doStop();
            if (connection != null) {
                connection.close();
            }
        } finally {
            connection = null;
        }
    }

    protected void doStop() throws Exception {
    }

}
