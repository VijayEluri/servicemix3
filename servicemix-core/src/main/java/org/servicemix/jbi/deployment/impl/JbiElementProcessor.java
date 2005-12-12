/** 
 * 
 * Copyright 2005 LogicBlaze, Inc. http://www.logicblaze.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 * 
 **/
package org.servicemix.jbi.deployment.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.servicemix.jbi.config.spring.ElementProcessor;
import org.servicemix.jbi.config.spring.ElementProcessorSupport;
import org.servicemix.jbi.deployment.Descriptor;
import org.servicemix.jbi.util.DOMUtil;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @version $Revision$
 */
public class JbiElementProcessor extends ElementProcessorSupport implements ElementProcessor {
    private static final transient Log log = LogFactory.getLog(JbiElementProcessor.class);

    private static final JbiNamespaceProcessor compositeProcessor = new JbiNamespaceProcessor();


    public void processElement(Element element, BeanDefinitionReader beanDefinitionReader) {
        Document document = element.getOwnerDocument();
        Element beans = document.createElement("beans");
        document.removeChild(element);
        document.appendChild(beans);

        Element bean = addBeanElement(beans, Descriptor.class.getName());
        DOMUtil.copyAttributes(element, beans);
        DOMUtil.moveContent(element, bean);

        // lets remove the default namespace just in case
        beans.removeAttribute("xmlns");

        String id = bean.getAttribute("id");
        if (id == null || id.length() == 0) {
            bean.setAttribute("id", "jbi");
        }
        bean.setAttribute("singleton", "true");
                
        moveToProperyListElement(bean, "path-element", "pathElements");
        moveToProperyListElement(bean, "service-unit", "serviceUnits");        

        processChildren(compositeProcessor, bean, beanDefinitionReader);
        
        moveToProperyListElement(bean, "service-unit", "serviceUnits");

        logXmlGenerated(log, "Adding new beans", beans);
    }

    /**
     * Recursively moves all elements of the given name into a <property><list> element so they can be added
     * to a collection or array by Spring
     *
     * @param bean         the root bean
     * @param elementName  the element name to move into a property list
     * @param propertyName the name of the property to use.
     */
    protected void moveToProperyListElement(Element bean, String elementName, String propertyName) {
        Element property = null;
        Element to = null;
        Node node = bean.getFirstChild();
        while (node != null) {
            Node current = node;
            node = node.getNextSibling();
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                if (elementName.equals(current.getNodeName())) {
                    bean.removeChild(current);
                    if (to == null) {
                        property = addPropertyElement(bean, propertyName);
                        to = addElement(property, "list");

                        // lets detach the to element for now
                        bean.removeChild(property);
                    }
                    to.appendChild(current);
                }
                else {
                    moveToProperyListElement((Element) current, elementName, propertyName);
                }
            }
        }
        if (property != null) {
            bean.appendChild(property);
        }
    }
}
