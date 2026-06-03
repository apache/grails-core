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
import grails.gorm.MultiTenant
import grails.gorm.multitenancy.CurrentTenantHolder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore

/**
 * Manages the registration, lookup, creation, and resolution of GORM entity APIs.
 * Extracted from GormRegistry to improve single responsibility and unit testability.
 *
 * @author Walter Duque de Estrada
 * @since 8.0.0
 */
@CompileStatic
@SuppressWarnings(['unused', 'DuplicatedCode'])
class EntityApiRegistry {

    private final GormRegistry registry
    final GormStaticApiRegistry staticApiRegistry
    final GormInstanceApiRegistry instanceApiRegistry
    final GormValidationApiRegistry validationApiRegistry

    EntityApiRegistry(GormRegistry registry) {
        this.registry = registry
        this.staticApiRegistry = new GormStaticApiRegistry(registry)
        this.instanceApiRegistry = new GormInstanceApiRegistry(registry)
        this.validationApiRegistry = new GormValidationApiRegistry(registry)
    }

    void registerApi(String className, GormStaticApi staticApi, GormInstanceApi instanceApi, GormValidationApi validationApi) {
        String normalizedClassName = registry.normalizeEntityKey(className)
        staticApiRegistry.register(normalizedClassName, staticApi)
        instanceApiRegistry.register(normalizedClassName, instanceApi)
        validationApiRegistry.register(normalizedClassName, validationApi)
    }

    void registerEntityApis(String className, GormStaticApi staticApi, GormInstanceApi instanceApi, GormValidationApi validationApi) {
        registerApi(className, staticApi, instanceApi, validationApi)
    }

    void registerEntityApis(Class cls, GormStaticApi staticApi, GormInstanceApi instanceApi, GormValidationApi validationApi) {
        registerEntityApis(cls.name, staticApi, instanceApi, validationApi)
    }

    GormStaticApi getStaticApi(Class entityClass) {
        return staticApiRegistry.get(registry.normalizeEntityKey(entityClass))
    }

    GormInstanceApi getInstanceApi(Class entityClass) {
        return instanceApiRegistry.get(registry.normalizeEntityKey(entityClass))
    }

    GormValidationApi getValidationApi(Class entityClass) {
        return validationApiRegistry.get(registry.normalizeEntityKey(entityClass))
    }

    GormStaticApi getStaticApi(Class entityClass, String qualifier) {
        return staticApiRegistry.get(registry.normalizeEntityKey(entityClass), registry.normalizeQualifier(qualifier))
    }

    GormInstanceApi getInstanceApi(Class entityClass, String qualifier) {
        return instanceApiRegistry.get(registry.normalizeEntityKey(entityClass), registry.normalizeQualifier(qualifier))
    }

    GormValidationApi getValidationApi(Class entityClass, String qualifier) {
        return validationApiRegistry.get(registry.normalizeEntityKey(entityClass), registry.normalizeQualifier(qualifier))
    }

    GormStaticApi getStaticApi(String className) {
        return staticApiRegistry.get(registry.normalizeEntityKey(className))
    }

    GormStaticApi getStaticApi(String className, String qualifier) {
        return staticApiRegistry.get(registry.normalizeEntityKey(className), registry.normalizeQualifier(qualifier))
    }

    GormInstanceApi getInstanceApi(String className) {
        return instanceApiRegistry.get(registry.normalizeEntityKey(className))
    }

    GormInstanceApi getInstanceApi(String className, String qualifier) {
        return instanceApiRegistry.get(registry.normalizeEntityKey(className), registry.normalizeQualifier(qualifier))
    }

    GormValidationApi getValidationApi(String className) {
        return validationApiRegistry.get(registry.normalizeEntityKey(className))
    }

    GormValidationApi getValidationApi(String className, String qualifier) {
        return validationApiRegistry.get(registry.normalizeEntityKey(className), registry.normalizeQualifier(qualifier))
    }

    GormStaticApi resolveStaticApi(Class entityClass) {
        return resolveStaticApi(entityClass, (String) null)
    }

    GormStaticApi resolveStaticApi(Class entityClass, String qualifier) {
        String normalizedClassName = registry.normalizeEntityKey(entityClass)
        String normalizedQualifier = registry.normalizeQualifier(qualifier)

        if (MultiTenant.isAssignableFrom(entityClass)) {
            if (normalizedQualifier != ConnectionSource.DEFAULT) {
                GormStaticApi api = staticApiRegistry.getDirect(normalizedClassName, normalizedQualifier)
                if (api != null) return api
            }

            Datastore ds = registry.getDatastoreDirect(normalizedClassName, normalizedQualifier)
            if (ds instanceof MultiTenantCapableDatastore) {
                Serializable tenantId = CurrentTenantHolder.get((MultiTenantCapableDatastore) ds)
                if (tenantId != null) {
                    GormStaticApi api = staticApiRegistry.getDirect(normalizedClassName, tenantId.toString())
                    if (api != null) return api
                }
            }
            
            if (normalizedQualifier != ConnectionSource.DEFAULT) {
                GormStaticApi api = staticApiRegistry.getDirect(normalizedClassName, ConnectionSource.DEFAULT)
                if (api != null) return api
            }
        }

        return staticApiRegistry.getDirect(normalizedClassName, normalizedQualifier)
    }

    GormInstanceApi resolveInstanceApi(Class entityClass) {
        return resolveInstanceApi(entityClass, (String) null)
    }

    GormInstanceApi resolveInstanceApi(Class entityClass, String qualifier) {
        String normalizedClassName = registry.normalizeEntityKey(entityClass)
        String normalizedQualifier = registry.normalizeQualifier(qualifier)

        if (MultiTenant.isAssignableFrom(entityClass)) {
            if (normalizedQualifier != ConnectionSource.DEFAULT) {
                GormInstanceApi api = instanceApiRegistry.getDirect(normalizedClassName, normalizedQualifier)
                if (api != null) return api
            }

            Datastore ds = registry.getDatastoreDirect(normalizedClassName, normalizedQualifier)
            if (ds instanceof MultiTenantCapableDatastore) {
                Serializable tenantId = CurrentTenantHolder.get((MultiTenantCapableDatastore) ds)
                if (tenantId != null) {
                    GormInstanceApi api = instanceApiRegistry.getDirect(normalizedClassName, tenantId.toString())
                    if (api != null) return api
                }
            }
            
            if (normalizedQualifier != ConnectionSource.DEFAULT) {
                GormInstanceApi api = instanceApiRegistry.getDirect(normalizedClassName, ConnectionSource.DEFAULT)
                if (api != null) return api
            }
        }

        return instanceApiRegistry.getDirect(normalizedClassName, normalizedQualifier)
    }

    GormValidationApi resolveValidationApi(Class entityClass) {
        return resolveValidationApi(entityClass, (String) null)
    }

    GormValidationApi resolveValidationApi(Class entityClass, String qualifier) {
        String normalizedClassName = registry.normalizeEntityKey(entityClass)
        String normalizedQualifier = registry.normalizeQualifier(qualifier)

        if (MultiTenant.isAssignableFrom(entityClass)) {
            if (normalizedQualifier != ConnectionSource.DEFAULT) {
                GormValidationApi api = validationApiRegistry.getDirect(normalizedClassName, normalizedQualifier)
                if (api != null) return api
            }

            Datastore ds = registry.getDatastoreDirect(normalizedClassName, normalizedQualifier)
            if (ds instanceof MultiTenantCapableDatastore) {
                Serializable tenantId = CurrentTenantHolder.get((MultiTenantCapableDatastore) ds)
                if (tenantId != null) {
                    GormValidationApi api = validationApiRegistry.getDirect(normalizedClassName, tenantId.toString())
                    if (api != null) return api
                }
            }
            
            if (normalizedQualifier != ConnectionSource.DEFAULT) {
                GormValidationApi api = validationApiRegistry.getDirect(normalizedClassName, ConnectionSource.DEFAULT)
                if (api != null) return api
            }
        }

        return validationApiRegistry.getDirect(normalizedClassName, normalizedQualifier)
    }

    GormStaticApi createStaticApi(Class cls, Datastore datastore, DatastoreResolver resolver, String qualifier) {
        return registry.getApiFactory(datastore).createStaticApi(cls, datastore.mappingContext, resolver, qualifier, registry)
    }

    GormInstanceApi createInstanceApi(Class cls, Datastore datastore, DatastoreResolver resolver, boolean failOnError, boolean markDirty) {
        return registry.getApiFactory(datastore).createInstanceApi(cls, datastore.mappingContext, resolver, registry, failOnError, markDirty)
    }

    GormValidationApi createValidationApi(Class cls, Datastore datastore, DatastoreResolver resolver) {
        return registry.getApiFactory(datastore).createValidationApi(cls, datastore.mappingContext, resolver, registry)
    }

    void removeDatastore(Datastore datastore) {
        staticApiRegistry.removeDatastore(datastore)
        instanceApiRegistry.removeDatastore(datastore)
        validationApiRegistry.removeDatastore(datastore)
    }

    void clear() {
        staticApiRegistry.clear()
        instanceApiRegistry.clear()
        validationApiRegistry.clear()
    }
}
