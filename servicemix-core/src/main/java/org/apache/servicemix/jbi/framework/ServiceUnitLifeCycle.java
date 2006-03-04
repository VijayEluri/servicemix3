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

import javax.jbi.component.Component;
import javax.jbi.component.ServiceUnitManager;
import javax.jbi.management.DeploymentException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.jbi.deployment.ServiceUnit;
import org.apache.servicemix.jbi.management.AttributeInfoHelper;
import org.apache.servicemix.jbi.management.MBeanInfoProvider;
import org.apache.servicemix.jbi.management.OperationInfoHelper;

public class ServiceUnitLifeCycle implements ServiceUnitMBean, MBeanInfoProvider {

    private static final Log log = LogFactory.getLog(ServiceUnitLifeCycle.class);

    private ServiceUnit serviceUnit;

    private String currentState = SHUTDOWN;
    
    private String serviceAssembly;
    
    private Registry registry;

    private PropertyChangeListener listener;
    
    ServiceUnitLifeCycle(ServiceUnit serviceUnit, 
                                String serviceAssembly,
                                Registry registry) {
        this.serviceUnit = serviceUnit;
        this.serviceAssembly = serviceAssembly;
        this.registry = registry;
    }

    /**
     * Initialize the service unit.
     * @throws DeploymentException 
     */
    public void init() throws DeploymentException {
        log.info("Initializing service unit: " + getName());
        checkComponentStarted("init");
        ServiceUnitManager sum = getServiceUnitManager();
        File path = getServiceUnitRootPath();
        sum.init(getName(), path.getAbsolutePath());
        currentState = STOPPED;
    }
    
    /**
     * Start the service unit.
     * @throws DeploymentException 
     */
    public void start() throws DeploymentException {
        log.info("Starting service unit: " + getName());
        checkComponentStarted("start");
        ServiceUnitManager sum = getServiceUnitManager();
        sum.start(getName());
        currentState = STARTED;
    }

    /**
     * Stop the service unit. This suspends current messaging activities.
     * @throws DeploymentException 
     */
    public void stop() throws DeploymentException {
        log.info("Stopping service unit: " + getName());
        checkComponentStarted("stop");
        ServiceUnitManager sum = getServiceUnitManager();
        sum.stop(getName());
        currentState = STOPPED;
    }

    /**
     * Shut down the service unit. 
     * This releases resources, preparatory to uninstallation.
     * @throws DeploymentException 
     */
    public void shutDown() throws DeploymentException {
        log.info("Shutting down service unit: " + getName());
        checkComponentStartedOrStopped("shutDown");
        ServiceUnitManager sum = getServiceUnitManager();
        sum.shutDown(getName());
        currentState = SHUTDOWN;
    }

    /**
     * @return the currentState as a String
     */
    public String getCurrentState() {
        return currentState;
    }

    public boolean isShutDown() {
        return currentState.equals(SHUTDOWN);
    }

    public boolean isStopped() {
        return currentState.equals(STOPPED);
    }

    public boolean isStarted() {
        return currentState.equals(STARTED);
    }
    
    /**
     * @return the name of the ServiceAssembly
     */
    public String getName() {
        return serviceUnit.getIdentification().getName();
    }

    /**
     * @return the description of the ServiceAssembly
     */
    public String getDescription() {
        return serviceUnit.getIdentification().getDescription();
    }
    
    public String getComponentName() {
        return serviceUnit.getTarget().getComponentName();
    }

    public String getServiceAssembly() {
        return serviceAssembly;
    }

    public String getDescriptor() {
        File suDir = getServiceUnitRootPath();
        return AutoDeploymentService.getDescriptorAsText(suDir);
    }

    protected void checkComponentStarted(String task) throws DeploymentException {
        String componentName = getComponentName();
        String suName = getName();
        LocalComponentConnector lcc = registry.getLocalComponentConnector(componentName);
        if (lcc == null) {
            throw ManagementSupport.componentFailure("deploy", componentName, "Target component " + componentName + " for service unit " + suName + " is not installed");
        }
        if (!lcc.getComponentMBean().isStarted()) {
            throw ManagementSupport.componentFailure("deploy", componentName, "Target component " + componentName + " for service unit " + suName + " is not started");
        }
        if (lcc.getServiceUnitManager() == null) {
            throw ManagementSupport.componentFailure("deploy", componentName, "Target component " + componentName + " for service unit " + suName + " does not accept deployments");
        }
    }
    
    protected void checkComponentStartedOrStopped(String task) throws DeploymentException {
        String componentName = getComponentName();
        String suName = getName();
        LocalComponentConnector lcc = registry.getLocalComponentConnector(componentName);
        if (lcc == null) {
            throw ManagementSupport.componentFailure("deploy", componentName, "Target component " + componentName + " for service unit " + suName + " is not installed");
        }
        if (!lcc.getComponentMBean().isStarted() && !lcc.getComponentMBean().isStopped()) {
            throw ManagementSupport.componentFailure("deploy", componentName, "Target component " + componentName + " for service unit " + suName + " is not started");
        }
        if (lcc.getServiceUnitManager() == null) {
            throw ManagementSupport.componentFailure("deploy", componentName, "Target component " + componentName + " for service unit " + suName + " does not accept deployments");
        }
    }
    
    protected File getServiceUnitRootPath() {
        String componentName = getComponentName();
        String suName = getName();
        File targetDir = registry.getEnvironmentContext().getServiceUnitDirectory(componentName, suName);
        return targetDir;
    }
    
    protected ServiceUnitManager getServiceUnitManager() {
        Component component = registry.getComponent(getComponentName());
        LocalComponentConnector lcc = registry.getComponentConnector(component);
        return lcc.getServiceUnitManager();
    }

    public MBeanAttributeInfo[] getAttributeInfos() throws JMException {
        AttributeInfoHelper helper = new AttributeInfoHelper();
        helper.addAttribute(getObjectToManage(), "currentState", "current state of the service unit");
        helper.addAttribute(getObjectToManage(), "name", "name of the service unit");
        helper.addAttribute(getObjectToManage(), "componentName", "component name of the service unit");
        helper.addAttribute(getObjectToManage(), "serviceAssembly", "service assembly name of the service unit");
        helper.addAttribute(getObjectToManage(), "description", "description of the service unit");
        return helper.getAttributeInfos();
    }

    public MBeanOperationInfo[] getOperationInfos() throws JMException {
        OperationInfoHelper helper = new OperationInfoHelper();
        helper.addOperation(getObjectToManage(), "getDescriptor", "retrieve the jbi descriptor for this unit");
        return helper.getOperationInfos();
    }

    public Object getObjectToManage() {
        return this;
    }

    public String getType() {
        return "ServiceUnit";
    }

    public String getSubType() {
        return getComponentName();
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

    public String getKey() {
        return getComponentName() + "/" + getName();
    }

}
