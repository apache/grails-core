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
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource

import java.util.concurrent.ConcurrentHashMap

/**
 * A registry of GORM API objects. This registry is used to decouple the API
 * objects from the static state in GormEnhancer.
 *
 * It implements an O(M+N) memory strategy where:
 * M = Number of Entities
 * N = Number of Connections (Tenants)
 *
 * @author Walter Duque de Estrada
 * @since 8.0.0
 */
@CompileStatic
class GormRegistry {

    private static final GormRegistry instance = new GormRegistry()

    // O(M) storage for Entity APIs (indexed by Class Name)
    private final Map<String, GormStaticApi> staticApis = new ConcurrentHashMap<>()
    private final Map<String, GormInstanceApi> instanceApis = new ConcurrentHashMap<>()
    private final Map<String, GormValidationApi> validationApis = new ConcurrentHashMap<>()
    
    // O(N) storage for Datastores (indexed by Qualifier)
    private final Map<String, Datastore> datastoresByQualifier = new ConcurrentHashMap<>()
    
    // Entity-specific overrides (for complex multi-datasource setups)
    private final Map<String, Map<String, Datastore>> entityDatastores = new ConcurrentHashMap<>()

    private final Map<Class, Datastore> datastoresByType = new ConcurrentHashMap<>()
    
    // Set of all active datastore instances
    private final Set<Datastore> allDatastores = Collections.newSetFromMap(new ConcurrentHashMap<Datastore, Boolean>())

    static GormRegistry getInstance() {
        return instance
    }

    /**
     * Resets the global registry to a clean state.
     */
    static void reset() {
        instance.staticApis.clear()
        instance.instanceApis.clear()
        instance.validationApis.clear()
        instance.datastoresByQualifier.clear()
        instance.entityDatastores.clear()
        instance.datastoresByType.clear()
        instance.allDatastores.clear()
    }

    GormStaticApi getStaticApi(String className) {
        return staticApis.get(className)
    }

    GormStaticApi getStaticApi(String className, String qualifier) {
        GormStaticApi api = staticApis.get(className)
        if (api != null && qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            Datastore ds = getDatastore(className, qualifier)
            if (ds != null && ds != api.getDatastore()) {
                return api.forQualifier(qualifier)
            }
        }
        return api
    }

    GormInstanceApi getInstanceApi(String className) {
        return instanceApis.get(className)
    }

    GormInstanceApi getInstanceApi(String className, String qualifier) {
        GormInstanceApi api = instanceApis.get(className)
        if (api != null && qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            Datastore ds = getDatastore(className, qualifier)
            if (ds != null && ds != api.getDatastore()) {
                return api.forQualifier(qualifier)
            }
        }
        return api
    }

    GormValidationApi getValidationApi(String className) {
        return validationApis.get(className)
    }

    GormValidationApi getValidationApi(String className, String qualifier) {
        GormValidationApi api = validationApis.get(className)
        if (api != null && qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            Datastore ds = getDatastore(className, qualifier)
            if (ds != null && ds != api.getDatastore()) {
                return api.forQualifier(qualifier)
            }
        }
        return api
    }

    /**
     * Finds a datastore for an entity and qualifier.
     */
    Datastore getDatastore(String className, String qualifier) {
        qualifier = qualifier ?: ConnectionSource.DEFAULT
        
        // 1. Check if there is an entity-specific override for this qualifier
        if (className != null) {
            Datastore ds = entityDatastores.get(className)?.get(qualifier)
            if (ds != null) return ds
        }
        
        // 2. Check the global qualifier map
        return datastoresByQualifier.get(qualifier)
    }

    /**
     * Registers GORM APIs for an entity.
     */
    void registerApi(String className, GormStaticApi staticApi, GormInstanceApi instanceApi, GormValidationApi validationApi) {
        if (staticApi != null) staticApis.put(className, staticApi)
        if (instanceApi != null) instanceApis.put(className, instanceApi)
        if (validationApi != null) validationApis.put(className, validationApi)
    }

    /**
     * Registers a datastore for a qualifier. (O(N) part)
     */
    void registerDatastore(String qualifier, Datastore datastore) {
        if (datastore == null) return
        String q = qualifier ?: ConnectionSource.DEFAULT
        datastoresByQualifier.put(q, datastore)
        allDatastores.add(datastore)
    }

    /**
     * Registers an entity-specific datastore override.
     */
    void registerEntityDatastore(String className, String qualifier, Datastore datastore) {
        if (datastore != null) {
            String q = qualifier ?: ConnectionSource.DEFAULT
            getInternalMap(entityDatastores, className).put(q, datastore)
            allDatastores.add(datastore)
        }
    }

    void registerDatastoreByType(Datastore datastore) {
        if (datastore == null) return
        datastoresByType.put(datastore.getClass(), datastore)
        allDatastores.add(datastore)
    }

    void removeDatastoreByType(Datastore datastore) {
        if (datastore == null) return
        datastoresByType.remove(datastore.getClass())
        // Don't remove from allDatastores here, as it might still be registered by qualifier
    }

    /**
     * Removes all occurrences of a datastore from the global and entity-specific maps.
     */
    void removeDatastore(Datastore datastore) {
        if (datastore == null) return
        
        allDatastores.remove(datastore)
        datastoresByType.remove(datastore.getClass())
        
        Iterator<Map.Entry<String, Datastore>> it = datastoresByQualifier.entrySet().iterator()
        while (it.hasNext()) {
            if (it.next().value == datastore) it.remove()
        }
        
        for (entityMap in entityDatastores.values()) {
            Iterator<Map.Entry<String, Datastore>> eit = entityMap.entrySet().iterator()
            while (eit.hasNext()) {
                if (eit.next().value == datastore) eit.remove()
            }
        }
    }

    /**
     * Removes a datastore for a specific entity.
     */
    void removeEntityDatastore(String className, Datastore datastore) {
        if (className != null && datastore != null) {
            Map<String, Datastore> entityMap = entityDatastores.get(className)
            if (entityMap != null) {
                Iterator<Map.Entry<String, Datastore>> eit = entityMap.entrySet().iterator()
                while (eit.hasNext()) {
                    if (eit.next().value == datastore) eit.remove()
                }
            }
        }
    }

    Map<String, Datastore> getDatastoresByQualifier() {
        return datastoresByQualifier
    }

    Map<Class, Datastore> getDatastoresByType() {
        return datastoresByType
    }

    Set<Datastore> getAllDatastores() {
        return allDatastores
    }

    private <T> Map<String, T> getInternalMap(Map<String, Map<String, T>> rootMap, String key) {
        Map<String, T> map = rootMap.get(key)
        if (map == null) {
            map = new ConcurrentHashMap<String, T>()
            Map<String, T> existing = rootMap.putIfAbsent(key, map)
            if (existing != null) {
                map = existing
            }
        }
        return map
    }
}
