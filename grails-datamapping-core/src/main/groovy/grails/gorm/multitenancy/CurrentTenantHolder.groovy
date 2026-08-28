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
package grails.gorm.multitenancy

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException

@CompileStatic
class CurrentTenantHolder {

    private static final ThreadLocal<Map<Object, Serializable>> currentTenantThreadLocal = new ThreadLocal<Map<Object, Serializable>>() {
        @Override
        protected Map<Object, Serializable> initialValue() {
            return new HashMap<>()
        }
    }

    /**
     * Obtain the tenant bound to the current thread, irrespective of datastore.
     *
     * Prefer {@link #get(Datastore)}. A thread can hold a different tenant for each datastore, and only
     * the datastore-scoped lookup can say which of them applies to a given operation.
     *
     * @return The bound tenant id, or {@code null} if no tenant is bound
     * @throws TenantException if different tenants are bound for different datastores. There is no
     *         correct answer to return in that case, and returning an arbitrary one is how cross-tenant
     *         reads and writes happen.
     */
    static Serializable get() {
        Map<Object, Serializable> map = currentTenantThreadLocal.get()
        if (map.isEmpty()) {
            return null
        }
        Set<Serializable> boundTenants = new LinkedHashSet<>(map.values())
        if (boundTenants.size() > 1) {
            throw new TenantException("Cannot resolve a single current tenant: ${boundTenants} are bound on this thread for different datastores. Use get(Datastore) to select the tenant of the datastore being operated on.")
        }
        return boundTenants.iterator().next()
    }

    /**
     * @return Obtain the current tenant
     */
    static Serializable get(Datastore datastore) {
        def map = currentTenantThreadLocal.get()
        def tenantId = map.get(datastore)
        if (tenantId == null) {
            tenantId = map.get(datastore.getClass())
        }
        return tenantId
    }

    /**
     * Set the current tenant
     *
     * @param tenantId The tenant id
     */
    static void set(Datastore datastore, Serializable tenantId) {
        currentTenantThreadLocal.get().put(datastore, tenantId)
    }

    static void set(Class<? extends Datastore> datastoreClass, Serializable tenantId) {
        currentTenantThreadLocal.get().put(datastoreClass, tenantId)
    }

    static void remove(Datastore datastore) {
        currentTenantThreadLocal.get().remove(datastore)
    }

    static void remove(Class<? extends Datastore> datastoreClass) {
        currentTenantThreadLocal.get().remove(datastoreClass)
    }

    /**
     * Execute with the current tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withTenant(Datastore datastore, Serializable tenantId, Closure<T> callable) {
        def previous = currentTenantThreadLocal.get().get(datastore)
        try {
            set(datastore, tenantId)
            callable.call(tenantId)
        } finally {
            if (previous == null) {
                remove(datastore)
            }
            else {
                set(datastore, previous)
            }
        }
    }

    static <T> T withTenant(Class<? extends Datastore> datastoreClass, Serializable tenantId, Closure<T> callable) {
        def previous = currentTenantThreadLocal.get().get(datastoreClass)
        try {
            set(datastoreClass, tenantId)
            callable.call(tenantId)
        } finally {
            if (previous == null) {
                remove(datastoreClass)
            }
            else {
                set(datastoreClass, previous)
            }
        }
    }

    /**
     * Execute without current tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withoutTenant(Datastore datastore, Closure<T> callable) {
        def previous = currentTenantThreadLocal.get().get(datastore)
        try {
            set(datastore, (Serializable) ConnectionSource.DEFAULT)
            callable.call()
        } finally {
            if (previous == null) {
                remove(datastore)
            } else {
                set(datastore, previous)
            }
        }
    }
}
