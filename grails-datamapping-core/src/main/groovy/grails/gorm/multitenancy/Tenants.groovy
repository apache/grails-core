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
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException

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
     * Binds the given tenant id to the current thread for the duration of the closure.
     *
     * This binds the tenant only. Unlike {@link #withId(Serializable, Closure)} it opens no session and
     * manages no connection life cycle: the closure runs in whatever session the caller has already
     * established, and the caller remains responsible for opening and closing it. Reach for {@code withId}
     * when the tenant's work needs a session of its own - in DATABASE mode that is also what selects the
     * tenant's connection.
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
     * The current id for the given datastore. Inside {@link #withoutId(Closure)} it is
     * {@link ConnectionSource#DEFAULT}.
     *
     * @param multiTenantCapableDatastore The multi tenant capable datastore
     * @return The current id
     * @throws TenantNotFoundException if no current tenant is found, including when the tenant resolver returns
     * {@code null}
     */
    static Serializable currentId(MultiTenantCapableDatastore multiTenantCapableDatastore) {
        def tenantId = CurrentTenantHolder.get(multiTenantCapableDatastore)
        if (tenantId != null) {
            return tenantId
        } else {
            TenantResolver tenantResolver = multiTenantCapableDatastore.getTenantResolver()
            def resolved = tenantResolver.resolveTenantIdentifier()
            if (resolved == null) {
                throw new TenantNotFoundException("Tenant resolver [${tenantResolver.getClass().name}] resolved no tenant id")
            }
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
     * Whether the current thread runs without a tenant id for the default datastore, as it does inside
     * {@link #withoutId(Closure)} and in a method annotated with {@link WithoutTenant}. A tenant id that a
     * {@link TenantResolver} returns or that is passed to {@link #withId(Serializable, Closure)} never counts, even if it
     * is the name of the default connection source, {@link ConnectionSource#DEFAULT}.
     * <p>
     * The tenant id is bound per datastore, so this method looks up the single configured datastore. When more than one
     * GORM implementation is configured, use {@link #isWithoutId(Datastore)} with the datastore in question instead.
     *
     * @return Whether the current thread runs without a tenant id
     * @throws IllegalStateException if GORM is not initialized or more than one GORM implementation is configured
     * @since 7.0.18
     */
    static boolean isWithoutId() {
        isWithoutId(datastoreLocator.getDatastore())
    }

    /**
     * Whether the current thread runs without a tenant id for the given datastore, as it does inside
     * {@link #withoutId(MultiTenantCapableDatastore, Closure)}. A tenant id that a {@link TenantResolver} returns or
     * that is passed to {@code withId} never counts, even if it is the name of the default connection source,
     * {@link ConnectionSource#DEFAULT}.
     *
     * @param datastore The datastore
     * @return Whether the current thread runs without a tenant id for the datastore
     * @since 7.0.18
     */
    static boolean isWithoutId(Datastore datastore) {
        CurrentTenantHolder.isWithoutTenant(datastore)
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
            return withCurrent((MultiTenantCapableDatastore) datastore, callable)
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
            return withCurrent((MultiTenantCapableDatastore) datastore, callable)
        }
        else {
            throw new UnsupportedOperationException('Datastore implementation does not support multi-tenancy')
        }
    }

    /**
     * Execute the given closure with the current tenant for the given datastore. Inside
     * {@link #withoutId(MultiTenantCapableDatastore, Closure)} it runs without a tenant id, and a closure that takes
     * the tenant id gets {@link ConnectionSource#DEFAULT}.
     *
     * @param multiTenantCapableDatastore The multi tenant capable datastore
     * @param callable The closure
     * @return The result of the closure
     * @since 7.0.18
     */
    static <T> T withCurrent(MultiTenantCapableDatastore multiTenantCapableDatastore, Closure<T> callable) {
        if (isWithoutId(multiTenantCapableDatastore)) {
            Datastore childDatastore = datastoreForTenantId(multiTenantCapableDatastore, ConnectionSource.DEFAULT)
            return CurrentTenantHolder.withoutTenant(multiTenantCapableDatastore) {
                callWithTenantId(multiTenantCapableDatastore, ConnectionSource.DEFAULT, childDatastore, callable)
            } as T
        }
        return withId(multiTenantCapableDatastore, currentId(multiTenantCapableDatastore), callable)
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
     * Binds the given tenant id to the current thread for the datastore that maps the given domain class,
     * for the duration of the closure.
     *
     * This binds the tenant only. Unlike {@link #withId(Class, Serializable, Closure)} it opens no session
     * and manages no connection: the caller owns the session the closure runs in.
     *
     * @param domainClass The domain class whose datastore the tenant should be bound for
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
        Datastore childDatastore = datastoreForTenantId(multiTenantCapableDatastore, tenantId)
        return CurrentTenantHolder.withTenant(multiTenantCapableDatastore, tenantId) {
            callWithTenantId(multiTenantCapableDatastore, tenantId, childDatastore, callable)
        } as T
    }

    private static Datastore datastoreForTenantId(MultiTenantCapableDatastore multiTenantCapableDatastore, Serializable tenantId) {
        try {
            return multiTenantCapableDatastore.getDatastoreForTenantId(tenantId)
        }
        catch (ConfigurationException | TenantException e) {
            // An unknown tenant or missing connection: fall through to the session-creating path below,
            // which resolves the tenant again and reports the failure to the caller rather than hiding it.
            log.warn('Could not resolve a datastore for tenant [{}]: {}', tenantId, e.message)
            return null
        }
    }

    private static Object callWithTenantId(MultiTenantCapableDatastore multiTenantCapableDatastore, Serializable tenantId,
                                           Datastore childDatastore, Closure callable) {
        // Only reuse an already-bound per-tenant session for non-shared-connection modes
        // (e.g. DATABASE), where getDatastoreForTenantId yields a distinct child datastore.
        // Shared-connection modes (DISCRIMINATOR, SCHEMA) must run through the shared-connection
        // path below so the closure receives the datastore's own session (which adapters may
        // expose as a native session), rather than the resolved child datastore's session.
        if (!multiTenantCapableDatastore.getMultiTenancyMode().isSharedConnection() &&
                childDatastore != null && childDatastore.hasCurrentSession()) {
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
        if (multiTenantCapableDatastore.getMultiTenancyMode().isSharedConnection()) {
            def i = callable.parameterTypes.length
            if (i == 2) {
                return multiTenantCapableDatastore.withSession { session ->
                    return callable.call(tenantId, session)
                }
            }
            else {
                switch (i) {
                    case 0:
                        return callable.call()
                    case 1:
                        return callable.call(tenantId)
                    default:
                        throw new IllegalArgumentException('Provided closure accepts too many arguments')
                }
            }
        }
        else {
            return multiTenantCapableDatastore.withNewSession(tenantId) { session ->
                def i = callable.parameterTypes.length
                switch (i) {
                    case 0:
                        return callable.call()
                    case 1:
                        return callable.call(tenantId)
                    case 2:
                        return callable.call(tenantId, session)
                    default:
                        throw new IllegalArgumentException('Provided closure accepts too many arguments')
                }

            }
        }
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
