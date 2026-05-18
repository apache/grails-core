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
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings

import java.util.concurrent.ConcurrentHashMap

@CompileStatic
abstract class AbstractGormApiRegistry<T extends AbstractDatastoreApi> {

    private final Map<String, T> apis = new ConcurrentHashMap<>()
    private final Map<String, Map<String, T>> qualifiedApis = new ConcurrentHashMap<>()
    protected final GormRegistry registry

    AbstractGormApiRegistry(GormRegistry registry) {
        this.registry = registry
    }

    void register(String className, T api) {
        String normalizedClassName = registry.normalizeEntityKey(className)
        if (normalizedClassName != null && api != null) {
            apis.put(normalizedClassName, api)
            qualifiedApis.remove(normalizedClassName)
        }
    }

    T get(String className) {
        return apis.get(registry.normalizeEntityKey(className))
    }

    T get(String className, String qualifier) {
        return getDirect(registry.normalizeEntityKey(className), registry.normalizeQualifier(qualifier))
    }

    T getDirect(String normalizedClassName, String normalizedQualifier) {
        if (ConnectionSource.DEFAULT.equals(normalizedQualifier)) {
            return apis.get(normalizedClassName)
        }

        Map<String, T> classQualifiedApis = qualifiedApis.computeIfAbsent(normalizedClassName, { new ConcurrentHashMap<String, T>() })
        T api = classQualifiedApis.get(normalizedQualifier)
        
        if (api == null) {
            T defaultApi = apis.get(normalizedClassName)
            if (defaultApi != null) {
                Datastore ds = registry.getDatastoreDirect(normalizedClassName, normalizedQualifier)
                if (ds == null && defaultApi.getDatastore() instanceof MultipleConnectionSourceCapableDatastore) {
                    Datastore defaultDatastore = defaultApi.getDatastore()
                    boolean canResolveConnection = true
                    if (defaultDatastore instanceof MultiTenantCapableDatastore) {
                        MultiTenancySettings.MultiTenancyMode mode = ((MultiTenantCapableDatastore) defaultDatastore).getMultiTenancyMode()
                        if (mode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR ||
                                mode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
                            canResolveConnection = false
                        }
                    }
                    if (canResolveConnection) {
                        ds = ((MultipleConnectionSourceCapableDatastore) defaultDatastore).getDatastoreForConnection(normalizedQualifier)
                    } else {
                        ds = defaultDatastore
                    }
                }
                if (ds != null && ds != defaultApi.getDatastore()) {
                    api = qualify(defaultApi, normalizedQualifier)
                    if (api != null) {
                        classQualifiedApis.put(normalizedQualifier, api)
                    }
                } else {
                    return defaultApi
                }
            }
        }
        
        return api
    }

    boolean containsKey(String className) {
        return apis.containsKey(registry.normalizeEntityKey(className))
    }

    int size() {
        return apis.size()
    }

    Set<String> keySet() {
        return apis.keySet()
    }

    void clear() {
        apis.clear()
        qualifiedApis.clear()
    }

    protected String className(Class entity) {
        return registry.normalizeEntityKey(entity)
    }

    protected IllegalStateException stateException(Class entity) {
        return new IllegalStateException("No GORM implementation configured for class [${entity.name}]. Ensure GORM has been initialized correctly")
    }

    protected abstract T qualify(T api, String qualifier)
}
