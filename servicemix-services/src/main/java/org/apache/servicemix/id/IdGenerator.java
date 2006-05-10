/**
 *
 * Copyright 2005-2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copied from http://svn.apache.org/repos/asf/incubator/activemq/trunk/activemq-core/src/main/java/org/apache/activemq/util/IdGenerator.java
 */

package org.apache.servicemix.id;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generator for Globally unique Strings.
 */

public class IdGenerator{

	private static final Logger log = Logger.getLogger(IdGenerator.class.getName());
	private static final String UNIQUE_STUB;
	private static int instanceCount;
    private static String hostName;
	private String seed;
	private long sequence;
	
	static {
		String stub = "";
		boolean canAccessSystemProps = true;
		try{
			SecurityManager sm = System.getSecurityManager();
			if(sm != null){
				sm.checkPropertiesAccess();
			}
		}catch(SecurityException se){
			canAccessSystemProps = false;
		}
		
		if ( canAccessSystemProps) {
			try {
				hostName = InetAddress.getLocalHost().getHostName();
				ServerSocket ss = new ServerSocket(0);
				stub=hostName + "-" + ss.getLocalPort() + "-" + System.currentTimeMillis() + "-";
				Thread.sleep(100);
				ss.close();
			}catch(Exception ioe){
				log.log(Level.WARNING, "could not generate unique stub",ioe);
			}
		}else{
            hostName="localhost";
			stub = hostName + "-1-" +System.currentTimeMillis() +"-";
		}
		UNIQUE_STUB = stub;
	}
    
    /**
     * As we have to find the hostname as a side-affect of generating
     * a unique stub, we allow it's easy retrevial here
     * @return the local host name
     */
    
    public static String getHostName(){
        return hostName;
    }
	
	/**
	 * Construct an IdGenerator
	 *
	 */
	
	public IdGenerator(String prefix){
		synchronized(UNIQUE_STUB){
			this.seed = prefix + UNIQUE_STUB +(instanceCount++) +":";
		}
	}
    
    public IdGenerator(){
        this("ID:");
    }
	
	/**
	 * Generate a unqiue id
	 * @return a unique id
	 */
	
	public synchronized String generateId(){
		return this.seed + (this.sequence++);
	}
    
    /**
     * Generate a unique ID - that is friendly for a URL or file system
     * @return a unique id
     */
    public String generateSanitizedId(){
        String result = generateId();
        result = result.replace(':', '-');
        result = result.replace('_', '-');
        result = result.replace('.', '-');
        return result;
    }

}
