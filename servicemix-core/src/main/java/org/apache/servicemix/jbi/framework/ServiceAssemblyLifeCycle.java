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
package org.apache.servicemix.jbi.framework;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.jbi.management.DeploymentException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ObjectName;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.jbi.deployment.Connection;
import org.apache.servicemix.jbi.deployment.Consumes;
import org.apache.servicemix.jbi.deployment.Descriptor;
import org.apache.servicemix.jbi.deployment.ServiceAssembly;
import org.apache.servicemix.jbi.management.AttributeInfoHelper;
import org.apache.servicemix.jbi.management.MBeanInfoProvider;
import org.apache.servicemix.jbi.management.OperationInfoHelper;
import org.apache.servicemix.jbi.nmr.Broker;
import org.apache.servicemix.jbi.util.XmlPersistenceSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * ComponentConnector is used internally for message routing
 * 
 * @version $Revision$
 */
public class ServiceAssemblyLifeCycle implements ServiceAssemblyMBean, MBeanInfoProvider {

    private static final Log log = LogFactory.getLog(ServiceAssemblyLifeCycle.class);

    private ServiceAssembly serviceAssembly;

    private String currentState = SHUTDOWN;

    private File stateFile;

    private ServiceUnitLifeCycle[] sus;
    
    private Registry registry;

    private PropertyChangeListener listener;
    
    /**
     * Construct a LifeCycle
     * 
     * @param sa
     * @param stateFile
     */
    ServiceAssemblyLifeCycle(ServiceAssembly sa, 
                             String[] suKeys, 
                             File stateFile,
                             Registry registry) {
        this.serviceAssembly = sa;
        this.stateFile = stateFile;
        this.registry = registry;
        this.sus = new ServiceUnitLifeCycle[suKeys.length];
        for (int i = 0; i < suKeys.length; i++) {
            this.sus[i] = registry.getServiceUnit(suKeys[i]);
        }
        
    }

    /**
     * Start a Service Assembly and put it in the STARTED state.
     *
     * @return Result/Status of this operation.
     * @throws Exception
     */
    public String start() throws Exception {
        return start(true);
    }
    
    public String start(boolean writeState) throws Exception {
        log.info("Starting service assembly: " + getName());
        // Start connections
        startConnections();
        // Start service units
        List componentFailures = new ArrayList();
        for (int i = 0; i < sus.length; i++) {
            if (sus[i].isShutDown()) {
                try {
                    sus[i].init();
                } catch (DeploymentException e) {
                    componentFailures.add(getComponentFailure(e, "start", sus[i].getComponentName()));
                }
            }
        }
        for (int i = 0; i < sus.length; i++) {
            if (sus[i].isStopped()) {
                try {
                    sus[i].start();
                } catch (DeploymentException e) {
                    componentFailures.add(getComponentFailure(e, "start", sus[i].getComponentName()));
                }
            }
        }
        if (componentFailures.size() == 0) {
            log.info("Started Service Assembly: " + getName());
            currentState = STARTED;
            if (writeState) {
                writeRunningState();
            }
            return ManagementSupport.createSuccessMessage("start");
        } else {
            throw ManagementSupport.failure("start", componentFailures);
        }
    }

    /**
     * Stops the service assembly and puts it in STOPPED state.
     * 
     * @return Result/Status of this operation.
     * @throws Exception 
     */
    public String stop() throws Exception {
        return stop(true);
    }
    
    public String stop(boolean writeState) throws Exception {
        log.info("Stopping service assembly: " + getName());
        // Stop connections
        stopConnections();
        // Stop service units
        List componentFailures = new ArrayList();
        for (int i = 0; i < sus.length; i++) {
            if (sus[i].isStarted()) {
                try {
                    sus[i].stop();
                } catch (DeploymentException e) {
                    componentFailures.add(getComponentFailure(e, "stop", sus[i].getComponentName()));
                }
            }
        }
        if (componentFailures.size() == 0) {
            log.info("Stopped Service Assembly: " + getName());
            currentState = STOPPED;
            if (writeState) {
                writeRunningState();
            }
            return ManagementSupport.createSuccessMessage("stop");
        } else {
            throw ManagementSupport.failure("stop", componentFailures);
        }
    }

    /**
     * Shutdown the service assembly and puts it in SHUTDOWN state.
     * 
     * @return Result/Status of this operation.
     * @throws Exception 
     */
    public String shutDown() throws Exception {
        return shutDown(true);
    }
    
    public String shutDown(boolean writeState) throws Exception {
        log.info("Shutting down service assembly: " + getName());
        List componentFailures = new ArrayList();
        for (int i = 0; i < sus.length; i++) {
            if (sus[i].isStarted()) {
                try {
                    sus[i].stop();
                } catch (DeploymentException e) {
                    componentFailures.add(getComponentFailure(e, "shutDown", sus[i].getComponentName()));
                }
            }
        }
        for (int i = 0; i < sus.length; i++) {
            if (sus[i].isStopped()) {
                try {
                    sus[i].shutDown();
                } catch (DeploymentException e) {
                    componentFailures.add(getComponentFailure(e, "shutDown", sus[i].getComponentName()));
                }
            }
        }
        if (componentFailures.size() == 0) {
            log.info("Shutdown Service Assembly: " + getName());
            currentState = SHUTDOWN;
            if (writeState) {
                writeRunningState();
            }
            return ManagementSupport.createSuccessMessage("shutDown");
        } else {
            throw ManagementSupport.failure("shutDown", componentFailures);
        }
    }

    /**
     * @return the currentState as a String
     */
    public String getCurrentState() {
        return currentState;
    }

    boolean isShutDown() {
        return currentState.equals(SHUTDOWN);
    }

    boolean isStopped() {
        return currentState.equals(STOPPED);
    }

    boolean isStarted() {
        return currentState.equals(STARTED);
    }

    /**
     * @return the name of the ServiceAssembly
     */
    public String getName() {
        return serviceAssembly.getIdentification().getName();
    }

    /**
     * 
     * @return the description of the ServiceAssembly
     */
    public String getDescription() {
        return serviceAssembly.getIdentification().getDescription();
    }

    /**
     * @return the ServiceAssembly
     */
    public ServiceAssembly getServiceAssembly() {
        return serviceAssembly;
    }
    
    public String getDescriptor() {
        File saDir = registry.getEnvironmentContext().getSARootDirectory(getName());
        return AutoDeploymentService.getDescriptorAsText(saDir);
    }

    /**
     * @return string representation of this
     */
    public String toString() {
        return "ServiceAssemblyLifeCycle[name=" + getName() + ",state=" + getCurrentState() + "]";
    }

    /**
     * write the current running state of the Component to disk
     */
    void writeRunningState() {
        try {
            String currentState = getCurrentState();
            Properties props = new Properties();
            props.setProperty("state", currentState);
            XmlPersistenceSupport.write(stateFile, props);
        } catch (IOException e) {
            log.error("Failed to write current running state for ServiceAssembly: " + getName(), e);
        }
    }

    /**
     * get the current running state from disk
     */
    String getRunningStateFromStore() {
        try {
            if (stateFile.exists()) {
                Properties props = (Properties) XmlPersistenceSupport.read(stateFile);
                return props.getProperty("state", SHUTDOWN);
            }
        } catch (Exception e) {
            log.error("Failed to read current running state for ServiceAssembly: " + getName(), e);
        }
        return null;
    }
    
    /**
     * Restore this service assembly to its state at shutdown.
     * @throws Exception
     */
    void restore() throws Exception {
        String state = getRunningStateFromStore();
        if (STARTED.equals(state)) {
            start();
        } else {
            stop();
            if (SHUTDOWN.equals(state)) {
                shutDown();
            }
        }
    }

    public ServiceUnitLifeCycle[] getDeployedSUs() {
        return sus;
    }
    
    protected void startConnections() {
        if (serviceAssembly.getConnections() == null ||
            serviceAssembly.getConnections().getConnections() == null) {
            return;
        }
        Connection[] connections = serviceAssembly.getConnections().getConnections();
        Broker broker = registry.getContainer().getBroker();
        for (int i = 0; i < connections.length; i++) {
            if (connections[i].getConsumer().getInterfaceName() != null) {
                QName fromItf = connections[i].getConsumer().getInterfaceName();
                QName toSvc = connections[i].getProvider().getServiceName();
                String toEp = connections[i].getProvider().getEndpointName();
                broker.registerInterfaceConnection(fromItf, toSvc, toEp);
            } else {
                QName fromSvc = connections[i].getConsumer().getServiceName();
                String fromEp = connections[i].getConsumer().getEndpointName();
                QName toSvc = connections[i].getProvider().getServiceName();
                String toEp = connections[i].getProvider().getEndpointName();
                String link = getLinkType(fromSvc, fromEp);
                broker.registerEndpointConnection(fromSvc, fromEp, toSvc, toEp, link);
            }
        }
    }
    
    protected String getLinkType(QName svc, String ep) {
        for (int i = 0; i < sus.length; i++) {
            Descriptor d = AutoDeploymentService.buildDescriptor(sus[i].getServiceUnitRootPath());
            if (d != null && d.getServices() != null && d.getServices().getConsumes() != null) {
                Consumes[] consumes = d.getServices().getConsumes();
                for (int j = 0; j < consumes.length; j++) {
                    if (svc.equals(consumes[j].getServiceName()) &&
                        ep.equals(consumes[j].getEndpointName())) {
                        return consumes[j].getLinkType();
                    }
                }
            }
        }
        return null;
    }
    
    protected void stopConnections() {
        if (serviceAssembly.getConnections() == null ||
                serviceAssembly.getConnections().getConnections() == null) {
                return;
            }
            Connection[] connections = serviceAssembly.getConnections().getConnections();
            Broker broker = registry.getContainer().getBroker();
            for (int i = 0; i < connections.length; i++) {
                if (connections[i].getConsumer().getInterfaceName() != null) {
                    QName fromItf = connections[i].getConsumer().getInterfaceName();
                    QName toSvc = connections[i].getProvider().getServiceName();
                    String toEp = connections[i].getProvider().getEndpointName();
                    broker.unregisterInterfaceConnection(fromItf, toSvc, toEp);
                } else {
                    QName fromSvc = connections[i].getConsumer().getServiceName();
                    String fromEp = connections[i].getConsumer().getEndpointName();
                    QName toSvc = connections[i].getProvider().getServiceName();
                    String toEp = connections[i].getProvider().getEndpointName();
                    broker.unregisterEndpointConnection(fromSvc, fromEp, toSvc, toEp);
                }
            }
    }

    protected Element getComponentFailure(Exception exception, String task, String component) {
        Element result = null;
        String resultMsg = exception.getMessage();
        try {
            Document doc = parse(resultMsg);
            result = getElement(doc, "component-task-result");
        } catch (Exception e) {
            log.warn("Could not parse result exception", e);
        }
        if (result == null) {
            result = ManagementSupport.createComponentFailure(
                    task, component,
                    "Unable to parse result string", exception);
        }
        return result;
    }
     
    protected Document parse(String result) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(result)));
    }
    
    protected Element getElement(Document doc, String name) {
        NodeList l = doc.getElementsByTagNameNS("http://java.sun.com/xml/ns/jbi/management-message", name);
        Element e = (Element) l.item(0);
        return e;
    }

    public MBeanAttributeInfo[] getAttributeInfos() throws JMException {
        AttributeInfoHelper helper = new AttributeInfoHelper();
        helper.addAttribute(getObjectToManage(), "currentState", "current state of the assembly");
        helper.addAttribute(getObjectToManage(), "name", "name of the assembly");
        helper.addAttribute(getObjectToManage(), "description", "description of the assembly");
        helper.addAttribute(getObjectToManage(), "serviceUnits", "list of service units contained in this assembly");
        return helper.getAttributeInfos();
    }

    public MBeanOperationInfo[] getOperationInfos() throws JMException {
        OperationInfoHelper helper = new OperationInfoHelper();
        helper.addOperation(getObjectToManage(), "start", "start the assembly");
        helper.addOperation(getObjectToManage(), "stop", "stop the assembly");
        helper.addOperation(getObjectToManage(), "shutDown", "shutdown the assembly");
        helper.addOperation(getObjectToManage(), "getDescriptor", "retrieve the jbi descriptor for this assembly");
        return helper.getOperationInfos();
    }

    public Object getObjectToManage() {
        return this;
    }

    public String getType() {
        return "ServiceAssembly";
    }

    public String getSubType() {
        return null;
    }

    public void setPropertyChangeListener(PropertyChangeListener listener) {
        this.listener = listener;
    }

    protected void firePropertyChanged(String name,Object oldValue, Object newValue){
        PropertyChangeListener l = listener;
        if (l != null){
            PropertyChangeEvent event = new PropertyChangeEvent(this,name,oldValue,newValue);
            l.propertyChange(event);
        }
    }

    public ObjectName[] getServiceUnits() {
        // TODO Auto-generated method stub
        return null;
    }
}
