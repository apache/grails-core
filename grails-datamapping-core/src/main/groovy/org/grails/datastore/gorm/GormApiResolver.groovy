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

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.CurrentTenantHolder
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.NameUtils
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.PlatformTransactionManager

/**
 * Instance-based resolver for GORM APIs and datastores.
 *
 * @since 8.0.0
 */
@CompileStatic
class GormApiResolver {

    private final GormRegistry registry
    private final GormEnhancerRegistry stateRegistry = GormEnhancerRegistry.getInstance()

    GormApiResolver(GormRegistry registry) {
        this.registry = registry
    }

    <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier = null) {
        return registry.getStaticApiRegistry().findStaticApi(entity, qualifier)
    }

    <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier = null) {
        return registry.getInstanceApiRegistry().findInstanceApi(entity, qualifier)
    }

    <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier = null) {
        return registry.getValidationApiRegistry().findValidationApi(entity, qualifier)
    }

    @CompileDynamic
    Datastore findDatastore(Class entity, String qualifier = null) {
        int depth = stateRegistry.getResolvingDatastoreDepth()
        if (depth > 5) {
            return registry.datastoresByQualifier.get(ConnectionSource.DEFAULT)
        }

        String className = entity != null ? NameUtils.getClassName(entity) : null

        Datastore preferred = stateRegistry.getPreferredDatastore()
        if (preferred != null) {
            if (qualifier != null) {
                if (preferred instanceof MultipleConnectionSourceCapableDatastore) {
                    try {
                        Datastore ds = ((MultipleConnectionSourceCapableDatastore) preferred).getDatastoreForConnection(qualifier)
                        if (ds != null) {
                            return ds
                        }
                    } catch (Throwable e) {
                        // ignore
                    }
                }
                if (ConnectionSource.DEFAULT.equals(qualifier)) {
                    return preferred
                }
            } else {
                if (className == null || preferred.mappingContext.getPersistentEntity(className) != null) {
                    if (preferred instanceof MultiTenantCapableDatastore) {
                        MultiTenantCapableDatastore mtds = (MultiTenantCapableDatastore) preferred
                        try {
                            Serializable tid = CurrentTenantHolder.get()
                            if (tid == null && entity != null && MultiTenant.isAssignableFrom(entity)) {
                                tid = mtds.tenantResolver.resolveTenantIdentifier()
                            }

                            if (ConnectionSource.DEFAULT.equals(tid)) {
                                return preferred
                            }

                            if (tid != null && !ConnectionSource.DEFAULT.equals(tid.toString())) {
                                stateRegistry.setResolvingDatastoreDepth(depth + 1)
                                try {
                                    return findDatastore(entity, tid.toString())
                                } finally {
                                    stateRegistry.setResolvingDatastoreDepth(depth)
                                }
                            }
                        } catch (Throwable e) {
                            if (entity != null && MultiTenant.isAssignableFrom(entity) && e instanceof TenantNotFoundException) {
                                throw e
                            }
                        }
                    }
                    return preferred
                }
            }
        }

        if (qualifier != null && !ConnectionSource.DEFAULT.equals(qualifier)) {
            Object resource = TransactionSynchronizationManager.getResource(qualifier)
            if (resource instanceof Datastore) {
                return (Datastore) resource
            }

            Datastore ds = registry.getDatastore(className, qualifier)
            if (ds != null) return ds

            Datastore defaultDs = registry.getDatastore(className, ConnectionSource.DEFAULT)
            if (defaultDs instanceof MultipleConnectionSourceCapableDatastore) {
                try {
                    stateRegistry.setResolvingDatastoreDepth(depth + 1)
                    ds = ((MultipleConnectionSourceCapableDatastore) defaultDs).getDatastoreForConnection(qualifier)
                    if (ds != null && ds != defaultDs) return ds
                } catch (Throwable e) {
                    // ignore
                } finally {
                    stateRegistry.setResolvingDatastoreDepth(depth)
                }
            }
            if (defaultDs instanceof MultiTenantCapableDatastore) {
                try {
                    stateRegistry.setResolvingDatastoreDepth(depth + 1)
                    ds = ((MultiTenantCapableDatastore) defaultDs).getDatastoreForTenantId(qualifier)
                    if (ds != null && ds != defaultDs) return ds
                } catch (Throwable e) {
                    // ignore
                } finally {
                    stateRegistry.setResolvingDatastoreDepth(depth)
                }
            }
            return defaultDs
        }

        for (Datastore registeredDs in registry.allDatastores) {
            if (TransactionSynchronizationManager.hasResource(registeredDs) || registeredDs.hasCurrentSession()) {
                if (className != null) {
                    if (registry.getDatastore(className, ConnectionSource.DEFAULT) == registeredDs) {
                        return registeredDs
                    } else if (registeredDs.getMappingContext().getPersistentEntity(className) != null) {
                        return registeredDs
                    }
                } else if (registry.allDatastores.size() == 1) {
                    return registeredDs
                }
            }
        }

        Datastore defaultDs = registry.getDatastore(className, ConnectionSource.DEFAULT)
        if (defaultDs instanceof MultiTenantCapableDatastore) {
            MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) defaultDs
            boolean isDatabaseMode = multiTenantCapableDatastore.getMultiTenancyMode() ==
                    MultiTenancySettings.MultiTenancyMode.DATABASE
            try {
                Serializable currentTenantId = CurrentTenantHolder.get()
                if (currentTenantId == null && entity != null && MultiTenant.isAssignableFrom(entity)) {
                    currentTenantId = multiTenantCapableDatastore.tenantResolver.resolveTenantIdentifier()
                }

                if (ConnectionSource.DEFAULT.equals(currentTenantId)) {
                    return defaultDs
                }

                if (currentTenantId != null && !ConnectionSource.DEFAULT.equals(currentTenantId.toString())) {
                    stateRegistry.setResolvingDatastoreDepth(depth + 1)
                    try {
                        return findDatastore(entity, currentTenantId.toString())
                    } finally {
                        stateRegistry.setResolvingDatastoreDepth(depth)
                    }
                }
            } catch (Throwable e) {
                if (entity != null && MultiTenant.isAssignableFrom(entity) && e instanceof TenantNotFoundException) {
                    if (isDatabaseMode || multiTenantCapableDatastore.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
                        throw e
                    }
                }
            }
        }

        if (defaultDs == null) {
            defaultDs = registry.getDatastore(null, ConnectionSource.DEFAULT)
        }
        if (defaultDs == null && entity != null) {
            throw stateException(entity)
        }
        return defaultDs
    }

    Datastore findDatastoreByType(Class<? extends Datastore> datastoreType) {
        Datastore datastore = registry.datastoresByType.get(datastoreType)
        if (datastore == null) {
            throw new IllegalStateException("No GORM implementation configured for type [$datastoreType]. Ensure GORM has been initialized correctly")
        }
        return datastore
    }

    Datastore findSingleDatastore() {
        if (registry.datastoresByQualifier.size() > 1) {
            return findDatastore(null, null)
        }

        Datastore defaultDs = registry.datastoresByQualifier.get(ConnectionSource.DEFAULT)
        if (defaultDs != null) {
            return defaultDs
        }

        if (registry.datastoresByQualifier.size() == 1) {
            return registry.datastoresByQualifier.values().first()
        }

        Collection<Datastore> allDatastores = registry.datastoresByType.values()
        if (allDatastores.isEmpty()) {
            throw new IllegalStateException('No GORM implementations configured. Ensure GORM has been initialized correctly')
        }
        if (allDatastores.size() > 1) {
            throw new IllegalStateException("More than one GORM implementation is configured. Registered by type: ${allDatastores*.getClass()*.name}. Registered by qualifier: ${registry.datastoresByQualifier.keySet()}")
        }
        return allDatastores.first()
    }

    PlatformTransactionManager findSingleTransactionManager() {
        Datastore ds = findSingleDatastore()
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) ds).transactionManager
        }
        return null
    }

    PlatformTransactionManager findSingleTransactionManager(String connectionName) {
        Datastore ds = findDatastore(null, connectionName)
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) ds).transactionManager
        }
        return null
    }

    PlatformTransactionManager findTransactionManager(Class entity, String qualifier = null) {
        Datastore ds = findDatastore(entity, qualifier)
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) ds).transactionManager
        }
        return null
    }

    PersistentEntity findEntity(Class entity, String qualifier = null) {
        String resolvedQualifier = qualifier ?: findTenantId(entity)
        return findDatastore(entity, resolvedQualifier)?.mappingContext?.getPersistentEntity(entity.name)
    }

    private String findTenantId(Class entity) {
        if (entity != null && MultiTenant.isAssignableFrom(entity)) {
            Datastore defaultDatastore = registry.getDatastore(entity.name, ConnectionSource.DEFAULT)
            if (defaultDatastore instanceof MultiTenantCapableDatastore) {
                MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) defaultDatastore
                try {
                    Serializable tid = Tenants.currentId(multiTenantCapableDatastore)
                    return tid?.toString() ?: ConnectionSource.DEFAULT
                } catch (Throwable e) {
                    return ConnectionSource.DEFAULT
                }
            }
        }
        return ConnectionSource.DEFAULT
    }

}
