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
package org.apache.servicemix.jbi.management.task;

import org.apache.servicemix.jbi.management.ManagementContextMBean;
import org.apache.servicemix.jbi.management.task.JbiTask;

import javax.jbi.management.DeploymentServiceMBean;
import javax.jbi.management.InstallationServiceMBean;


/**
 *
 * JbiTaskTest
 * @version $Revision$
 */
public class JbiTaskTest extends JbiTaskSupport {
   

    private JbiTask jbiTask;
    /*
     * @see TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();        
        jbiTask = new JbiTask(){};
        jbiTask.init();
    }

    /*
     * @see TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        jbiTask.close();
        super.tearDown();
    }
    public void testGetInstallationService() throws Exception {
        InstallationServiceMBean mbean = jbiTask.getInstallationService();
        assertNotNull(mbean);
    }

    public void testGetDeploymentService() throws Exception {
        DeploymentServiceMBean mbean = jbiTask.getDeploymentService();
        assertNotNull(mbean);
        mbean.getDeployedServiceAssemblies();
    }
    
    public void testGetManagementContext() throws Exception {
        ManagementContextMBean mbean = jbiTask.getManagementContext();
        assertNotNull(mbean);
    }
}
