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
package org.apache.servicemix.bpe;

import java.io.File;
import java.net.URI;
import java.net.URL;

import junit.framework.TestCase;

import org.apache.servicemix.common.ServiceUnit;

public class BPEDeployerTest extends TestCase {

    public void testDeploy() throws Exception {
        URL url = getClass().getClassLoader().getResource("loanbroker/loanbroker.bpel");
        File path = new File(new URI(url.toString()));
        path = path.getParentFile();

        BPEComponent bpe = new BPEComponent();
        ((BPELifeCycle) bpe.getLifeCycle()).doInit();
        BPEDeployer deployer = new BPEDeployer(bpe);
        assertTrue(deployer.canDeploy("loanbroker", path.getAbsolutePath()));

        ServiceUnit su = deployer.deploy("loanbroker", path.getAbsolutePath());
        assertNotNull(su);
    }

}
