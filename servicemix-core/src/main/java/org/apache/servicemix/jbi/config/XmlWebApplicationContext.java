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
package org.apache.servicemix.jbi.config;

import java.util.Arrays;

import org.apache.servicemix.jbi.config.spring.XBeanProcessor;

/**
 * Application context for ServiceMix 1.x xml syntax compatibility
 */ 
public class XmlWebApplicationContext
    extends org.apache.xbean.spring.context.XmlWebApplicationContext {

  public XmlWebApplicationContext() {
    super(Arrays.asList(new Object[] {new XBeanProcessor()}));
  }
}
