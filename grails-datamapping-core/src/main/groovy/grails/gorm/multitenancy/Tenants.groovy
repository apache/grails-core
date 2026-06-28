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
import groovy.util.logging.Slf4j

import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver

/**
 * Helper methods for working with multi tenancy
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
@Slf4j
class Tenants {

    /**
     * Pluggable locator for Datastore instances, allowing for easier testing.
     */
    static DatastoreLocator datastoreLocator = new DatastoreLocator()

    static class DatastoreLocator {
        Datastore getDatastore() {
            GormRegistry.instance.apiResolver.findSingleDatastore()
        }
        Datastore getDatastore(Class<? extends Datastore> datastoreClass) {
            GormRegistry.instance.apiResolver.findDatastoreByType(datastoreClass)
        }
        Datastore getDatastoreForDomain(Class domainClass) {
            GormRegistry.instance.apiResolver.findDatastore(domainClass)
        }
    }

    /**
     * Execute the given closure with the given tenant id.
     *
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withTenant(Serializable tenantId, Closure<T> callable) {
        Datastore datastore = datastoreLocator.getDatastore()
        return CurrentTenantHolder.withTenant(datastore.getClass(), tenantId) {
            return CurrentTenantHolder.withTenant(datastore, tenantId, callable)
        }
    }

    /**
     * Execute the given closure for each tenant.
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static void eachTenant(Closure callable) {
        Datastore datastore = datastoreLocator.getDatastore()
        eachTenantInternal(datastore, callable)
    }

    /**
     * Execute the given closure for each tenant.
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static void eachTenant(Class<? extends Datastore> datastoreClass, Closure callable) {
        eachTenantInternal(datastoreLocator.getDatastore(datastoreClass), callable)
    }

    /**
     * @return The current tenant id
     *
     * @throws org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException if no current tenant is found
     */
    static Serializable currentId() {
        Datastore datastore = datastoreLocator.getDatastore()
        if (datastore instanceof MultiTenantCapableDatastore) {
            MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) datastore
            return currentId(multiTenantCapableDatastore)
        }
        else {
            throw new UnsupportedOperationException('Datastore implementation does not support multi-tenancy')
        }
    }

    /**
     * The current id for the given datastore
     *
     * @param multiTenantCapableDatastore The multi tenant capable datastore
     * @return The current id
     */
    static Serializable currentId(MultiTenantCapableDatastore multiTenantCapableDatastore) {
        def tenantId = CurrentTenantHolder.get(multiTenantCapableDatastore)
        if (tenantId != null) {
            return tenantId
        } else {
            TenantResolver tenantResolver = multiTenantCapableDatastore.getTenantResolver()
            def resolved = tenantResolver.resolveTenantIdentifier()
            return resolved
        }
    }

    /**
     * @return The current tenant id for the given datastore type
     *
     * @throws org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException if no current tenant is found
     */
    static Serializable currentId(Class<? extends Datastore> datastoreClass) {
        Datastore datastore = datastoreLocator.getDatastore(datastoreClass)
        if (datastore instanceof MultiTenantCapableDatastore) {
            return currentId((MultiTenantCapableDatastore) datastore)
        }
        else {
            throw new UnsupportedOperationException('Datastore implementation does not support multi-tenancy')
        }
    }

    /**
     * Execute the given closure without any tenant id. In Multi tenancy mode SINGLE this will execute against the default data source. If multi tenancy mode
     * MULTI this will execute without including the "tenantId" on any query. Use with caution.
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withoutId(Closure<T> callable) {
        Datastore datastore = datastoreLocator.getDatastore()
        if (datastore instanceof MultiTenantCapableDatastore) {
            MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) datastore
            return withoutId(multiTenantCapableDatastore, callable)
        } else {
            throw new UnsupportedOperationException('Datastore implementation does not support multi-tenancy')
        }
    }

    /**
     * Execute the given closure with the current tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withCurrent(Closure<T> callable) {
        Datastore datastore = datastoreLocator.getDatastore()
        if (datastore instanceof MultiTenantCapableDatastore) {
            MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) datastore
            Serializable tenantIdentifier = currentId(multiTenantCapableDatastore)
            return withId(multiTenantCapableDatastore, tenantIdentifier, callable)
        }
        else {
            throw new UnsupportedOperationException('Datastore implementation does not support multi-tenancy')
        }
    }

    /**
     * Execute the given closure with the current tenant
     *
     * @param datastoreClass The datastore class
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withCurrent(Class<? extends Datastore> datastoreClass, Closure<T> callable) {
        Datastore datastore = datastoreLocator.getDatastore(datastoreClass)
        if (datastore instanceof MultiTenantCapableDatastore) {
            MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) datastore
            Serializable tenantIdentifier = currentId(multiTenantCapableDatastore)
            return withId(multiTenantCapableDatastore, tenantIdentifier, callable)
        }
        else {
            throw new UnsupportedOperationException('Datastore implementation does not support multi-tenancy')
        }
    }

    /**
     * Execute the given closure with given tenant id
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withId(Serializable tenantId, Closure<T> callable) {
        Datastore datastore = datastoreLocator.getDatastore()
        if (datastore instanceof MultiTenantCapableDatastore) {
            MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) datastore
            return withId(multiTenantCapableDatastore, tenantId, callable)
        }
        else {
            throw new UnsupportedOperationException('Datastore implementation does not support multi-tenancy')
        }
    }

    /**
     * Execute the given closure with given tenant id
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withId(Class domainClass, Serializable tenantId, Closure<T> callable) {
        Datastore datastore = datastoreLocator.getDatastoreForDomain(domainClass)
        if (datastore instanceof MultiTenantCapableDatastore) {
            MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) datastore
            return withId(multiTenantCapableDatastore, tenantId, callable)
        }
        else {
            throw new UnsupportedOperationException('Datastore implementation does not support multi-tenancy')
        }
    }

    /**
     * Execute the given closure with given tenant id for the given datastore. This method will create a new datastore session for the scope of the call and hence is designed to be used to manage the connection life cycle
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withTenant(Class domainClass, Serializable tenantId, Closure<T> callable) {
        Datastore datastore = datastoreLocator.getDatastoreForDomain(domainClass)
        return CurrentTenantHolder.withTenant(datastore.getClass(), tenantId) {
            return CurrentTenantHolder.withTenant(datastore, tenantId, callable)
        }
    }

    /**
     * Execute the given closure without tenant id for the given datastore. This method will create a new datastore session for the scope of the call and hence is designed to be used to manage the connection life cycle
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withoutId(MultiTenantCapableDatastore multiTenantCapableDatastore, Closure<T> callable) {
        return CurrentTenantHolder.withoutTenant(multiTenantCapableDatastore) {
            if (multiTenantCapableDatastore.getMultiTenancyMode().isSharedConnection()) {
                def i = callable.parameterTypes.length
                if (i == 0) {
                    return callable.call()
                } else {
                    return multiTenantCapableDatastore.withSession { session ->
                        return callable.call(session)
                    }
                }
            } else {
                return multiTenantCapableDatastore.withNewSession(ConnectionSource.DEFAULT) { session ->
                    def i = callable.parameterTypes.length
                    switch (i) {
                        case 0:
                            return callable.call()
                            break
                        case 1:
                            return callable.call(ConnectionSource.DEFAULT)
                            break
                        case 2:
                            return callable.call(ConnectionSource.DEFAULT, session)
                        default:
                            throw new IllegalArgumentException('Provided closure accepts too many arguments')
                    }

                }
            }
        } as T
    }

    /**
     * Execute the given closure with given tenant id for the given datastore. This method will create a new datastore session for the scope of the call and hence is designed to be used to manage the connection life cycle
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withId(MultiTenantCapableDatastore multiTenantCapableDatastore, Serializable tenantId, Closure<T> callable) {
        log.debug('Tenants.withId called for datastore {} with tenantId {}', multiTenantCapableDatastore, tenantId)
        org.grails.datastore.mapping.core.Datastore childDatastore = null
        try {
            childDatastore = multiTenantCapableDatastore.getDatastoreForTenantId(tenantId)
        } catch (Throwable e) {
            // ignore
        }
        // Only reuse an already-bound per-tenant session for non-shared-connection modes
        // (e.g. DATABASE), where getDatastoreForTenantId yields a distinct child datastore.
        // Shared-connection modes (DISCRIMINATOR, SCHEMA) must run through the shared-connection
        // path below so the closure receives the datastore's own session (which adapters may
        // expose as a native session), rather than the resolved child datastore's session.
        if (!multiTenantCapableDatastore.getMultiTenancyMode().isSharedConnection() &&
                childDatastore != null && childDatastore.hasCurrentSession()) {
            return CurrentTenantHolder.withTenant(multiTenantCapableDatastore, tenantId) {
                def i = callable.parameterTypes.length
                switch (i) {
                    case 0:
                        return callable.call()
                    case 1:
                        return callable.call(tenantId)
                    case 2:
                        return callable.call(tenantId, childDatastore.getCurrentSession())
                    default:
                        throw new IllegalArgumentException('Provided closure accepts too many arguments')
                }
            }
        }
        return CurrentTenantHolder.withTenant(multiTenantCapableDatastore, tenantId) {
            if (multiTenantCapableDatastore.getMultiTenancyMode().isSharedConnection()) {
                def i = callable.parameterTypes.length
                if (i == 2) {
                    return multiTenantCapableDatastore.withSession { session ->
                        def result = callable.call(tenantId, session)
                        log.debug('Result from shared connection with 2 args: {}', result)
                        return result
                    }
                }
                else {
                    switch (i) {
                        case 0:
                            def result = callable.call()
                            log.debug('Result from shared connection with 0 args: {}', result)
                            return result
                        case 1:
                            def result = callable.call(tenantId)
                            log.debug('Result from shared connection with 1 arg: {}', result)
                            return result
                        default:
                            throw new IllegalArgumentException('Provided closure accepts too many arguments')
                    }
                }
            }
            else {
                return multiTenantCapableDatastore.withNewSession(tenantId) { session ->
                    log.debug('Inside withNewSession for tenantId {}', tenantId)
                    def i = callable.parameterTypes.length
                    switch (i) {
                        case 0:
                            def result = callable.call()
                            log.debug('Result from new session with 0 args: {}', result)
                            return result
                        case 1:
                            def result = callable.call(tenantId)
                            log.debug('Result from new session with 1 arg: {}', result)
                            return result
                        case 2:
                            def result = callable.call(tenantId, session)
                            log.debug('Result from new session with 2 args: {}', result)
                            return result
                        default:
                            throw new IllegalArgumentException('Provided closure accepts too many arguments')
                    }

                }
            }
        } as T
    }

    /**
     * Execute the given closure for each tenant for the given datastore. This method will create a new datastore session for the scope of the call and hence is designed to be used to manage the connection life cycle
     * @param callable The closure
     * @return The result of the closure
     */
    static void eachTenant(MultiTenantCapableDatastore multiTenantCapableDatastore, Closure callable) {
        MultiTenancySettings.MultiTenancyMode multiTenancyMode = multiTenantCapableDatastore.multiTenancyMode
        if (multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DATABASE) {
            if (multiTenantCapableDatastore.tenantResolver instanceof AllTenantsResolver) {
                def tenantIds = ((AllTenantsResolver) multiTenantCapableDatastore.tenantResolver).resolveTenantIds()
                for (tenantId in tenantIds) {
                    withId(multiTenantCapableDatastore, tenantId, callable)
                }
            } else {
                ConnectionSources connectionSources = multiTenantCapableDatastore.connectionSources
                for (ConnectionSource connectionSource in connectionSources.allConnectionSources) {
                    def tenantId = connectionSource.name
                    if (tenantId != ConnectionSource.DEFAULT) {
                        withId(multiTenantCapableDatastore, tenantId, callable)
                    }
                }
            }
        } else if (multiTenancyMode.isSharedConnection()) {
            TenantResolver tenantResolver = multiTenantCapableDatastore.tenantResolver
            if (tenantResolver instanceof AllTenantsResolver) {
                for (tenantId in ((AllTenantsResolver) tenantResolver).resolveTenantIds()) {
                    withId(multiTenantCapableDatastore, tenantId, callable)
                }
            } else {
                throw new UnsupportedOperationException("Multi tenancy mode $multiTenancyMode is configured, but the configured TenantResolver does not implement the [org.grails.datastore.mapping.multitenancy.AllTenantsResolver] interface")
            }
        } else {
            throw new UnsupportedOperationException("Method not supported in multi tenancy mode $multiTenancyMode")
        }
    }

    private static void eachTenantInternal(Datastore datastore, Closure callable) {
        if (datastore instanceof MultiTenantCapableDatastore) {
            MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) datastore
            eachTenant(multiTenantCapableDatastore, callable)
        } else {
            throw new UnsupportedOperationException('Datastore implementation does not support multi-tenancy')
        }
    }

}
