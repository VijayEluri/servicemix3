/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.common;

import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.apache.servicemix.jbi.util.DOMUtil;

import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

/**
 * A default implementation of a resolved endpoint
 */
public class ResolvedEndpoint implements ServiceEndpoint {

    private DocumentFragment reference;
    private String endpointName;
    private QName serviceName;
    private QName[] interfaces = null;


    public ResolvedEndpoint(DocumentFragment reference, String epName, QName serviceName) {
        this.reference = reference;
        this.endpointName = epName;
        this.serviceName = serviceName;
    }


    public ResolvedEndpoint(DocumentFragment reference, String epName, QName serviceName, QName[] interfaces) {
        this.reference = reference;
        this.endpointName = epName;
        this.serviceName = serviceName;
        this.interfaces = interfaces;
    }

    public DocumentFragment getAsReference(QName operationName) {
        return reference;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public QName getServiceName() {
        return serviceName;
    }

    public QName[] getInterfaces() {
        return interfaces;
    }

    public static ServiceEndpoint resolveEndpoint(DocumentFragment epr, String eprUri, String eprName, QName serviceName, String uriPrefix) {
        if (epr.getChildNodes().getLength() == 1) {
            Node child = epr.getFirstChild();
            if (child instanceof Element) {
                Element elem = (Element) child;
                String nsUri = elem.getNamespaceURI();
                String name = elem.getLocalName();
                // Check simple endpoints
                if (eprUri.equals(nsUri) && eprName.equals(name)) {
                    return new ResolvedEndpoint(epr, DOMUtil.getElementText(elem), serviceName);
                // Check WSA endpoints
                } else {
                    NodeList nl = elem.getElementsByTagNameNS("http://www.w3.org/2005/08/addressing", "Address");
                    if (nl.getLength() == 1) {
                        Element address = (Element) nl.item(0);
                        String uri = DOMUtil.getElementText(address);
                        if (uri != null && uriPrefix != null) {
                            uri = uri.trim();
                            if (uri.startsWith(uriPrefix)) {
                                return new ResolvedEndpoint(epr, uri, serviceName);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

}
