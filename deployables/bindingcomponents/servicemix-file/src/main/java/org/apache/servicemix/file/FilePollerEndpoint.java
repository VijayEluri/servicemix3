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
package org.apache.servicemix.file;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.concurrent.locks.Lock;

import javax.jbi.JBIException;
import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.xml.namespace.QName;

import org.apache.servicemix.common.DefaultComponent;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.endpoints.PollingEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.jbi.util.FileUtil;
import org.apache.servicemix.locks.LockManager;
import org.apache.servicemix.locks.impl.SimpleLockManager;

/**
 * A polling endpoint which looks for a file or files in a directory
 * and sends the files into the JBI bus as messages, deleting the files
 * by default when they are processed.
 *
 * @org.apache.xbean.XBean element="poller"
 *
 * @version $Revision$
 */
public class FilePollerEndpoint extends PollingEndpoint implements FileEndpointType {

    private File file;
    private FileFilter filter;
    private boolean deleteFile = true;
    private boolean recursive = true;
    private boolean autoCreateDirectory = true;
    private File archive;
    private FileMarshaler marshaler = new DefaultFileMarshaler();
    private LockManager lockManager;

    public FilePollerEndpoint() {
    }

    public FilePollerEndpoint(ServiceUnit serviceUnit, QName service, String endpoint) {
        super(serviceUnit, service, endpoint);
    }

    public FilePollerEndpoint(DefaultComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void poll() throws Exception {
        pollFileOrDirectory(file);
    }

    public void validate() throws DeploymentException {
        super.validate();
        if (file == null) {
            throw new DeploymentException("You must specify a file property");
        }
        if (isAutoCreateDirectory() && !file.exists()) {
            file.mkdirs();
        }
        if (archive != null) {
            if (!deleteFile) {
                throw new DeploymentException("Archive shouldn't be specified unless deleteFile='true'");
            }
            if (isAutoCreateDirectory() && !archive.exists()) {
                archive.mkdirs();
            }
            if (!archive.isDirectory()) {
                throw new DeploymentException("Archive should refer to a directory");
            }
        }
        if (lockManager == null) {
            lockManager = createLockManager();
        }
    }
    
    protected LockManager createLockManager() {
        return new SimpleLockManager();
    }


    // Properties
    //-------------------------------------------------------------------------
    /**
            * Returns the file the endpoint polls.
            *
            * @return the <code>File</code> object for the file being polled
            */
    public File getFile() {
        return file;
    }

    /**
     *  Specifies the file to be polled. This can be a directory or a file. 
     * If it is a directory, all files in the directory, or its 
     * subdirectories, will be processed by the endpoint. If it is a file, olny 
     * files matching the filename will be processed.
     *
     * @param file a <code>File</code> object representing the directory or file to poll
     * @org.apache.xbean.Property description="the relative path of the file to poll. This can be a directory or a file. 
     *                If it is a directory, all files in the directory, or its subdirectories, will be processed by the endpoint. 
     *                If it is a file, olny files matching the filename will be processed."
     */
    public void setFile(File file) {
        this.file = file;
    }

    /**
            * Returns the object used to manage the endpoint's file locking 
            * strategy.
            *
     * @return the lockManager
     */
    public LockManager getLockManager() {
        return lockManager;
    }

    /**
            * Specifies a class that implements the locking strategy used by 
            * the endpoint. This class must be an implementation of the 
            * <code>org.apache.servicemix.locks.LockManager</code> interface.
            *
     * @param lockManager the <code>LockManager</code> implementation to use
     * @org.apache.xbean.Property description="the bean defining the class implementing the file locking strategy"
     */
    public void setLockManager(LockManager lockManager) {
        this.lockManager = lockManager;
    }

    /**
            * Returns the object implementing the endpoint's file filter.
            *
            * @return the file filer
            */
    public FileFilter getFilter() {
        return filter;
    }

    /**
           * Specifies a class that implements the filtering logic used to 
           * choose which files to process. This class must be an 
           * implementation of the <code>java.io.FileFilter</code> interface.
           *
           * @param filter a <code>FileFilter</code> implementation defining the endpoints filtering logic
           * @org.apache.xbean.Property description="the bean defining the class implementing the file filtering strategy"
     */
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns whether or not we should delete the file when its processed
     */
    public boolean isDeleteFile() {
        return deleteFile;
    }

    /**
            * Specifiedsif the endpoint should delete a file after it is 
            * consumed. The default is true.
            *
            * @param deleteFile a boolean specifying if the file should be deleted
            * @org.apache.xbean.Property description="specifies if files are deleted after the endpoint processes them. 
            *       The defualt is <code>true</code>."
            */
    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    /**
           * Returns wheter the endpoint should poll subdirectories.
           */
    public boolean isRecursive() {
        return recursive;
    }

    /**
            * Specifies if the endpoint should poll the subdirectories of the 
            * directory being polled. Setting this to false means that the 
            * endpoint will only poll the specified directory for files. If the 
            * endpoint is polling for a specific file, this property is ignored.
            *
            * @param recursive a boolen specifying if subdirectories should be polled
            * @org.apache.xbean.Property description="specifies if subdirectories are polled. The defualt is <code>true</code>."
            */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    /** Returns wheter the endpoint should create the directory being polled 
            * if it does not exist.
            */
    public boolean isAutoCreateDirectory() {
        return autoCreateDirectory;
    }

    /**
            * Specifies if the endpoint should create the directory it is 
            * configured to poll if it does not exist.  If you set this to 
            * <code>false</code> and the directory does not exist, the endpoint 
            * will not do anything.
            *
            * @param autoCreateDirectory a boolean specifying if the endpoint creates directories
            * @org.apache.xbean.Property description="specifies if directories are created. The defualt is <code>true</code>."
            */
    public void setAutoCreateDirectory(boolean autoCreateDirectory) {
        this.autoCreateDirectory = autoCreateDirectory;
    }

    /**
            * Returns the object responsible for marshaling files into the NMR.
            */
    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    /**
            * Specifies a <code>FileMarshaler</code> object that will marshal 
            * file data into the NMR. The default file marshaller can read 
            * valid XML data. <code>FileMarshaler</code> objects are 
            * implementations of 
            * <code>org.apache.servicemix.components.util.FileMarshaler</code>.
            *
            * @param marshaler a <code>FileMarshaler</code> object that can read data from the file system
            * @org.apache.xbean.Property description="the bean defining the class used to marshal data from the file system"
            */
    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }
    
    /**
            * Returns the file representing the location where processed files 
            * are archived.
            */
    public File getArchive() {
        return archive;
    }
    
    /**
            * Specifies a directory to which processed files are archived.
            * 
           * @param archive a <code>File</code> object for the archive directory
           * @org.apache.xbean.Property description="the relative path of the directory where processed files will be archived"
          */
    public void setArchive(File archive) {
        this.archive = archive;
    }

    // Implementation methods
    //-------------------------------------------------------------------------


    protected void pollFileOrDirectory(File fileOrDirectory) {
        pollFileOrDirectory(fileOrDirectory, true);
    }

    protected void pollFileOrDirectory(File fileOrDirectory, boolean processDir) {
        if (!fileOrDirectory.isDirectory()) {
            pollFile(fileOrDirectory); // process the file
        } else if (processDir) {
            logger.debug("Polling directory " + fileOrDirectory);
            File[] files = fileOrDirectory.listFiles(getFilter());
            for (int i = 0; i < files.length; i++) {
                pollFileOrDirectory(files[i], isRecursive()); // self-recursion
            }
        } else {
            logger.debug("Skipping directory " + fileOrDirectory);
        }
    }

    protected void pollFile(final File aFile) {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling file " + aFile + " for processing");
        }
        getExecutor().execute(new Runnable() {
            public void run() {
                String uri = file.toURI().relativize(aFile.toURI()).toString();
                Lock lock = lockManager.getLock(uri);
                if (lock.tryLock()) {
                    boolean unlock = true;
                    try {
                        unlock = processFileAndDelete(aFile);
                    } finally {
                        if (unlock) {
                            lock.unlock();
                        }
                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Unable to acquire lock on " + aFile);
                    }
                }
            }
        });
    }

    protected boolean processFileAndDelete(File aFile) {
        boolean unlock = true;
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file " + aFile);
            }
            if (aFile.exists()) {
                processFile(aFile);
                unlock = false;
                if (isDeleteFile()) {
                    if (archive != null) {
                        FileUtil.moveFile(aFile, archive);
                    } else {
                        if (!aFile.delete()) {
                            throw new IOException("Could not delete file " + aFile);
                        }
                    }
                    unlock = true;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to process file: " + aFile + ". Reason: " + e, e);
        }
        return unlock;
    }

    protected void processFile(File aFile) throws Exception {
        InputStream in = null;
        try {
            String name = aFile.getCanonicalPath();
            in = new BufferedInputStream(new FileInputStream(aFile));
            InOnly exchange = getExchangeFactory().createInOnlyExchange();
            configureExchangeTarget(exchange);
            NormalizedMessage message = exchange.createMessage();
            exchange.setInMessage(message);
            marshaler.readMessage(exchange, message, in, name);
            sendSync(exchange);
            if (exchange.getStatus() == ExchangeStatus.ERROR) {
                Exception e = exchange.getError();
                if (e == null) {
                    e = new JBIException("Unkown error");
                }
                throw e;
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    public String getLocationURI() {
        return file.toURI().toString();
    }

    public void process(MessageExchange exchange) throws Exception {
        // Do nothing. In our case, this method should never be called
        // as we only send synchronous InOnly exchange
    }
}
