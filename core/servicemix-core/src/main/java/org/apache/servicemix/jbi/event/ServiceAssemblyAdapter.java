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
package org.apache.servicemix.jbi.event;

/**
 * An abstract adapter class for receiving service assemblies events.
 * The methods in this class are empty. This class exists as a
 * convenience for creating listener objects.
 * 
 * @see ServiceAssemblyEvent
 * @see ServiceAssemblyListener
 * 
 * @author gnodet
 */
public class ServiceAssemblyAdapter implements ServiceAssemblyListener {

    public void assemblyDeployed(ServiceAssemblyEvent event) {
    }

    public void assemblyStarted(ServiceAssemblyEvent event) {
    }

    public void assemblyStopped(ServiceAssemblyEvent event) {
    }

    public void assemblyShutDown(ServiceAssemblyEvent event) {
    }

    public void assemblyUndeployed(ServiceAssemblyEvent event) {
    }

}
