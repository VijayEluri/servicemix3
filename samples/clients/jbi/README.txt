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

Welcome to the ServiceMix JBI Client Example
============================================

This example shows how to code a basic JBI client to connect to a remote ServiceMix instance.

First start a ServiceMix server (if not already started) by running
    bin/servicemix
in the root dir of this distribution.

To run this sample, launch the following commands:
    mvn install exec:java
    
By default, the client tries to send a message to the wsdl-first-cxfse-su deployed by the cxf-wsdl-first sample.