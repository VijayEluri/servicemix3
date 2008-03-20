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
package org.apache.servicemix.jbi.deployment;

import junit.framework.TestCase;

import org.apache.servicemix.jbi.jaxp.SourceTransformer;

/**
 * @version $Revision: 426415 $
 */
public class ServiceUnitTest extends TestCase {

    protected SourceTransformer transformer = new SourceTransformer();

    public void testParse() throws Exception {

        // lets force the JBI container to be constructed first
        Descriptor root = DescriptorFactory.buildDescriptor(getClass().getResource("serviceUnit.xml"));
        assertNotNull("Unable to parse descriptor", root);

        Services services = root.getServices();
        Consumes[] consumes = services.getConsumes();
        assertNotNull("consumes are null", consumes);
        assertEquals("consumes size", 1, consumes.length);
        
    }

}
