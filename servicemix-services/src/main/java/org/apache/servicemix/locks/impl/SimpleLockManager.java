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
package org.apache.servicemix.locks.impl;

import org.apache.servicemix.locks.LockManager;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;
import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentMap;
import edu.emory.mathcs.backport.java.util.concurrent.locks.Lock;
import edu.emory.mathcs.backport.java.util.concurrent.locks.ReentrantLock;

public class SimpleLockManager implements LockManager {

    private ConcurrentMap locks = new ConcurrentHashMap();
    
    public Lock getLock(String id) {
        Lock lock = (Lock) locks.get(id);
        if (lock == null) {
            lock = new ReentrantLock();
            Lock oldLock = (Lock) locks.putIfAbsent(id, lock);
            if (oldLock != null) {
                lock = oldLock;
            }
        }
        return lock;
    }

}