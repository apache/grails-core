/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.Datastore

/**
 * Singleton registry for managing GormEnhancer's static state.
 * 
 * This class holds thread-local and shared state that was previously
 * defined as static fields in GormEnhancer, allowing for better isolation
 * and testability.
 *
 * @author Graeme Rocher
 */
@CompileStatic
class GormEnhancerRegistry {

    private static final GormEnhancerRegistry INSTANCE = new GormEnhancerRegistry()

    private final ThreadLocal<Integer> resolvingDatastore = ThreadLocal.withInitial { 0 }
    private final ThreadLocal<Datastore> preferredDatastore = new ThreadLocal<>()

    /**
     * @return The singleton instance
     */
    static GormEnhancerRegistry getInstance() {
        return INSTANCE
    }

    /**
     * Set the resolving datastore depth for the current thread
     *
     * @param depth The depth
     */
    void setResolvingDatastoreDepth(int depth) {
        resolvingDatastore.set(depth)
    }

    /**
     * Get the resolving datastore depth for the current thread
     *
     * @return The depth
     */
    int getResolvingDatastoreDepth() {
        return resolvingDatastore.get()
    }

    /**
     * Clear the resolving datastore depth for the current thread
     */
    void clearResolvingDatastoreDepth() {
        resolvingDatastore.remove()
    }

    /**
     * Set the preferred datastore for the current thread
     *
     * @param datastore The datastore
     */
    void setPreferredDatastore(Datastore datastore) {
        preferredDatastore.set(datastore)
    }

    /**
     * Get the preferred datastore for the current thread
     *
     * @return The datastore, or null if none is set
     */
    Datastore getPreferredDatastore() {
        return preferredDatastore.get()
    }

    /**
     * Clear the preferred datastore for the current thread
     */
    void clearPreferredDatastore() {
        preferredDatastore.remove()
    }

}
