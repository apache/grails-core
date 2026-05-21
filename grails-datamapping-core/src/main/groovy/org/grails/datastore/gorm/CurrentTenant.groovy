/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.datastore.gorm

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Holds a reference to the current tenant for the thread
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@PackageScope
@CompileStatic
class CurrentTenant  {

    private static final ThreadLocal<Serializable> currentTenantThreadLocal = new ThreadLocal<>()

    /**
     * @return Obtain the current tenant
     */
    static Serializable get() {
        currentTenantThreadLocal.get()
    }

    /**
     * Set the current tenant
     *
     * @param tenantId The tenant id
     */
    private static void set(Serializable tenantId) {
        currentTenantThreadLocal.set(tenantId)
    }

    private static void remove() {
        currentTenantThreadLocal.remove()
    }

    /**
     * Execute with the current tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withTenant(Serializable tenantId, Closure<T> callable) {
        try {
            set(tenantId)
            callable.call()
        } finally {
            remove()
        }
    }
}
