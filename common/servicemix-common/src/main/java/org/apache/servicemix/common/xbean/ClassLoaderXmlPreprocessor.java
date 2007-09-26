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
package org.apache.servicemix.common.xbean;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.xml.parsers.DocumentBuilder;

import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.framework.SharedLibrary;
import org.apache.servicemix.jbi.framework.ComponentMBeanImpl;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.xbean.classloader.JarFileClassLoader;
import org.apache.xbean.server.repository.FileSystemRepository;
import org.apache.xbean.server.repository.Repository;
import org.apache.xbean.server.spring.loader.SpringLoader;
import org.apache.xbean.spring.context.SpringApplicationContext;
import org.apache.xbean.spring.context.SpringXmlPreprocessor;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * An advanced xml preprocessor that will create a default classloader for the SU if none
 * is configured.
 * 
 * @author gnodet
 */
public class ClassLoaderXmlPreprocessor implements SpringXmlPreprocessor {

    public static final String CLASSPATH_XML = "classpath.xml";
    public static final String LIB_DIR = "/lib";
    
    private final FileSystemRepository repository;
    private final JBIContainer container;
    
    public ClassLoaderXmlPreprocessor(Repository repository) {
        this(repository, null);
    }

    public ClassLoaderXmlPreprocessor(Repository repository, JBIContainer container) {
        if (repository instanceof FileSystemRepository == false) {
            throw new IllegalArgumentException("repository must be a FileSystemRepository");
        }
        this.repository = (FileSystemRepository) repository;
        this.container = container;
    }

    public void preprocess(SpringApplicationContext applicationContext, XmlBeanDefinitionReader reader, Document document) {
        // determine the classLoader
        ClassLoader classLoader;
        NodeList classpathElements = document.getDocumentElement().getElementsByTagName("classpath");
        if (classpathElements.getLength() == 0) {
            // Check if a classpath.xml file exists in the root of the SU
            URL url = repository.getResource(CLASSPATH_XML);
            if (url != null) {
                try {
                    DocumentBuilder builder = new SourceTransformer().createDocumentBuilder();
                    Document doc = builder.parse(url.toString());
                    classLoader = getClassLoader(applicationContext, reader, doc);
                } catch (Exception e) {
                    throw new FatalBeanException("Unable to load classpath.xml file", e);
                }
            } else {
                try {
                    URL[] urls = getDefaultLocations();
                    ClassLoader parentLoader = getParentClassLoader(applicationContext);
                    classLoader = new JarFileClassLoader(applicationContext.getDisplayName(), urls, parentLoader);
                    // assign the class loader to the xml reader and the
                    // application context
                } catch (Exception e) {
                    throw new FatalBeanException("Unable to create default classloader for SU", e);
                }
            }
        } else {
            classLoader = getClassLoader(applicationContext, reader, document);
        }
        reader.setBeanClassLoader(classLoader);
        applicationContext.setClassLoader(classLoader);
        Thread.currentThread().setContextClassLoader(classLoader);
    }
    
    protected URL[] getDefaultLocations() {
        try {
            File root = repository.getRoot();
            File[] jars = new File(root, LIB_DIR).listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    name = name.toLowerCase();
                    return name.endsWith(".jar") || name.endsWith(".zip");
                }
            });
            URL[] urls = new URL[jars != null ? jars.length + 1 : 1];
            urls[0] = root.toURL();
            if (jars != null) {
                for (int i = 0; i < jars.length; i++) {
                    urls[i+1] = jars[i].toURL();
                }
            }
            return urls;
        } catch (MalformedURLException e) {
            throw new FatalBeanException("Unable to get default classpath locations", e);
        }
    }
    
    protected ClassLoader getClassLoader(SpringApplicationContext applicationContext, XmlBeanDefinitionReader reader, Document document) {
        // determine the classLoader
        ClassLoader classLoader;
        NodeList classpathElements = document.getDocumentElement().getElementsByTagName("classpath");
        if (classpathElements.getLength() < 1) {
            classLoader = getParentClassLoader(applicationContext);
        } else if (classpathElements.getLength() > 1) {
            throw new FatalBeanException("Expected only classpath element but found " + classpathElements.getLength());
        } else {
            Element classpathElement = (Element) classpathElements.item(0);
            
            // Delegation mode
            boolean inverse = false;
            String inverseAttr = classpathElement.getAttribute("inverse");
            if (inverseAttr != null && "true".equalsIgnoreCase(inverseAttr)) {
                inverse = true;
            }

            // build hidden classes
            List<String> hidden = new ArrayList<String>();
            NodeList hiddenElems = classpathElement.getElementsByTagName("hidden");
            for (int i = 0; i < hiddenElems.getLength(); i++) {
                Element hiddenElement = (Element) hiddenElems.item(i);
                String pattern = ((Text) hiddenElement.getFirstChild()).getData().trim();
                hidden.add(pattern);
            }

            // build non overridable classes
            List<String> nonOverridable = new ArrayList<String>();
            NodeList nonOverridableElems = classpathElement.getElementsByTagName("nonOverridable");
            for (int i = 0; i < nonOverridableElems.getLength(); i++) {
                Element nonOverridableElement = (Element) nonOverridableElems.item(i);
                String pattern = ((Text) nonOverridableElement.getFirstChild()).getData().trim();
                nonOverridable.add(pattern);
            }

            // build the classpath
            List<String> classpath = new ArrayList<String>();
            NodeList locations = classpathElement.getElementsByTagName("location");
            for (int i = 0; i < locations.getLength(); i++) {
                Element locationElement = (Element) locations.item(i);
                String location = ((Text) locationElement.getFirstChild()).getData().trim();
                classpath.add(location);
            }
            
            // Add shared libraries
            List<String> sls = new ArrayList<String>();
            NodeList libraries = classpathElement.getElementsByTagName("library");
            for (int i = 0; i < libraries.getLength(); i++) {
                Element locationElement = (Element) locations.item(i);
                String library = ((Text) locationElement.getFirstChild()).getData().trim();
                sls.add(library);
            }
            if (sls.size() > 0 && container == null) {
                throw new IllegalStateException("Can not reference shared libraries if the component is not deployed in ServiceMix");
            }

            // Add components
            List<String> components = new ArrayList<String>();
            NodeList componentList = classpathElement.getElementsByTagName("component");
            for (int i = 0; i < componentList.getLength(); i++) {
                Element locationElement = (Element) locations.item(i);
                String component = ((Text) locationElement.getFirstChild()).getData().trim();
                components.add(component);
            }
            if (components.size() > 0 && container == null) {
                throw new IllegalStateException("Can not reference other components if the component is not deployed in ServiceMix");
            }

            // convert the paths to URLS
            URL[] urls;
            if (classpath.size() != 0) {
                urls = new URL[classpath.size()];
                for (ListIterator<String> iterator = classpath.listIterator(); iterator.hasNext();) {
                    String location = iterator.next();
                    URL url = repository.getResource(location);
                    if (url == null) {
                        throw new FatalBeanException("Unable to resolve classpath location " + location);
                    }
                    urls[iterator.previousIndex()] = url;
                }
            } else {
                urls = getDefaultLocations();
            }

            // create the classloader
            List<ClassLoader> parents = new ArrayList<ClassLoader>();
            parents.add(getParentClassLoader(applicationContext));
            for (String library : sls) {
                SharedLibrary sl = container.getRegistry().getSharedLibrary(library);
                if (sl == null) {
                    throw new IllegalStateException("No such shared library: " + library);
                }
                parents.add(sl.getClassLoader());
            }
            for (String component : components) {
                ComponentMBeanImpl componentMBean = container.getRegistry().getComponent(component);
                if (componentMBean == null) {
                    throw new IllegalStateException("No such component: " + componentMBean);
                }
                parents.add(componentMBean.getComponent().getClass().getClassLoader());
            }
            classLoader = new JarFileClassLoader(applicationContext.getDisplayName(),
                                                 urls, 
                                                 parents.toArray(new ClassLoader[parents.size()]),
                                                 inverse,
                                                 hidden.toArray(new String[hidden.size()]),
                                                 nonOverridable.toArray(new String[nonOverridable.size()]));

            // remove the classpath element so Spring doesn't get confused
            document.getDocumentElement().removeChild(classpathElement);
        }
        return classLoader;
    }

    private static ClassLoader getParentClassLoader(SpringApplicationContext applicationContext) {
        ClassLoader classLoader = applicationContext.getClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        if (classLoader == null) {
            classLoader = SpringLoader.class.getClassLoader();
        }
        return classLoader;
    }
    
}
