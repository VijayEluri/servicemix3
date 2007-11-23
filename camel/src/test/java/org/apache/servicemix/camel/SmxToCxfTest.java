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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.servicemix.nmr.api.NMR;
import org.apache.servicemix.nmr.core.ServiceMix;
import org.w3c.dom.Document;



public class SmxToCxfTest extends ContextTestSupport {
    protected static final String ROUTER_ADDRESS = "http://localhost:9000/router";
    protected static final String SERVICE_ADDRESS = "local://smx/helloworld";
    protected static final String SERVICE_CLASS = "serviceClass=org.apache.servicemix.camel.HelloService";
    
    private String routerEndpointURI = "cxf://" + ROUTER_ADDRESS + "?" + SERVICE_CLASS + "&dataFormat=POJO";
    private String serviceEndpointURI = "cxf://" + SERVICE_ADDRESS + "?" + SERVICE_CLASS + "&dataFormat=POJO";
    
    private ServerImpl server;
    private CamelContext camelContext;
    private ServiceMixComponent smxComponent;
    private NMR nmr;
    
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();        
                
        startService();
    }
    
    protected void startService() {
        //start a service
        ServerFactoryBean svrBean = new ServerFactoryBean();

        svrBean.setAddress(SERVICE_ADDRESS);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
        svrBean.setBus(CXFBusFactory.getDefaultBus());

        server = (ServerImpl)svrBean.create();
        server.start();
    }
    
    @Override
    protected void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }
  
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
            	from(routerEndpointURI).to("smx:testEndpoint");// like what do in binding component
            	from("smx:testEndpoint").to(serviceEndpointURI);// like what do in se
            }
        };
    }
    
    protected CamelContext createCamelContext() throws Exception {
    	camelContext = new DefaultCamelContext();
    	
    	smxComponent = new ServiceMixComponent();
    	nmr = new ServiceMix();
    	((ServiceMix)nmr).init();
    	smxComponent.setNmr(nmr);
    	camelContext.addComponent("smx", smxComponent);
        return camelContext;
    }

    
    public void testInvokingServiceFromCXFClient() throws Exception {  
        Bus bus = BusFactory.getDefaultBus();
        
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(ROUTER_ADDRESS);        
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(bus);        
        
        //this test works if patch for CAMEL-243 get applied
        /*HelloService client = (HelloService) proxyFactory.create();
        String result = client.echo("hello world");
        assertEquals("we should get the right answer from router", "hello world echo", result);*/
        
                
    }
    
    class PojoInvokeDispatchProcessor implements Processor {
        public void process(Exchange exchange) throws Exception {
            System.out.println("PojoInvokeDispatchProcessor " + exchange.getIn().getBody());
            
        }
    }
}