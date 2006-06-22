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
package org.apache.servicemix.jbi.deployment;

/**
 * @version $Revision$
 */
public class SharedLibrary {
    private String classLoaderDelegation = "parent-first";
    private String version;
    private Identification identification;
    private ClassPath sharedLibraryClassPath;

    public SharedLibrary() {
    }

    public String getClassLoaderDelegation() {
        return classLoaderDelegation;
    }

    public void setClassLoaderDelegation(String classLoaderDelegation) {
        this.classLoaderDelegation = classLoaderDelegation;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Identification getIdentification() {
        return identification;
    }

    public void setIdentification(Identification identification) {
        this.identification = identification;
    }

    public ClassPath getSharedLibraryClassPath() {
        return sharedLibraryClassPath;
    }

    public void setSharedLibraryClassPath(ClassPath sharedLibraryClassPath) {
        this.sharedLibraryClassPath = sharedLibraryClassPath;
    }

    public boolean isParentFirstClassLoaderDelegation() {
        return classLoaderDelegation != null && classLoaderDelegation.equalsIgnoreCase("parent-first");
    }

    public boolean isSelfFirstClassLoaderDelegation() {
        return classLoaderDelegation != null && classLoaderDelegation.equalsIgnoreCase("self-first");
    }
}
