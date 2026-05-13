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
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.PersistentEntity

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
@Slf4j
class GormRegistry {

    private static final GormRegistry instance = new GormRegistry()
    private final GormApiFactory defaultApiFactory = new DefaultGormApiFactory()
    private final GormApiResolver apiResolver = new GormApiResolver(this)

    // O(M) storage for Entity APIs (indexed by Class Name)
    private final Map<String, GormStaticApi> staticApis = new ConcurrentHashMap<>()
    private final Map<String, GormInstanceApi> instanceApis = new ConcurrentHashMap<>()
    private final Map<String, GormValidationApi> validationApis = new ConcurrentHashMap<>()
    
    // O(N) storage for Datastores (indexed by Qualifier)
    private final Map<String, Datastore> datastoresByQualifier = new ConcurrentHashMap<>()
    
    // Entity-specific overrides (for complex multi-datasource setups)
    private final Map<String, Map<String, Datastore>> entityDatastores = new ConcurrentHashMap<>()

    private final Map<Class, Datastore> datastoresByType = new ConcurrentHashMap<>()
    private final Map<Class<? extends Datastore>, GormApiFactory> apiFactoriesByDatastoreType = new ConcurrentHashMap<>()
    
    // Set of all active datastore instances
    private final Set<Datastore> allDatastores = Collections.newSetFromMap(new ConcurrentHashMap<Datastore, Boolean>())

    static GormRegistry getInstance() {
        return instance
    }

    /**
     * Resets the global registry to a clean state.
     */
    static void reset() {
        // Clear MetaClasses to prevent stale GORM methods
        def classNames = new HashSet<String>()
        classNames.addAll(instance.staticApis.keySet())
        classNames.addAll(instance.instanceApis.keySet())
        classNames.addAll(instance.validationApis.keySet())
        
        for (className in classNames) {
            try {
                Class cls = Class.forName(className, false, Thread.currentThread().contextClassLoader)
                if (cls != null) {
                    GroovySystem.metaClassRegistry.removeMetaClass(cls)
                }
            } catch (Throwable e) {
                // ignore
            }
        }

        instance.staticApis.clear()
        instance.instanceApis.clear()
        instance.validationApis.clear()
        instance.datastoresByQualifier.clear()
        instance.entityDatastores.clear()
        instance.datastoresByType.clear()
        instance.allDatastores.clear()
        instance.apiFactoriesByDatastoreType.clear()
    }

    GormApiResolver getApiResolver() {
        return apiResolver
    }

    GormApiFactory getDefaultApiFactory() {
        return defaultApiFactory
    }

    void registerApiFactory(Class<? extends Datastore> datastoreType, GormApiFactory apiFactory) {
        if (datastoreType != null && apiFactory != null) {
            apiFactoriesByDatastoreType.put(datastoreType, apiFactory)
        }
    }

    GormApiFactory getApiFactory(Datastore datastore) {
        if (datastore == null) {
            return defaultApiFactory
        }
        GormApiFactory factory = apiFactoriesByDatastoreType.get(datastore.getClass())
        return factory ?: defaultApiFactory
    }

    <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier = null) {
        return apiResolver.findStaticApi(entity, qualifier)
    }

    <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier = null) {
        return apiResolver.findInstanceApi(entity, qualifier)
    }

    <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier = null) {
        return apiResolver.findValidationApi(entity, qualifier)
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
        
        if (className != null) {
            Map<String, Datastore> mappedDatastores = entityDatastores.get(className)
            if (mappedDatastores != null) {
                Datastore ds = mappedDatastores.get(qualifier)
                if (ds != null) return ds
                
                // If requested qualifier not found, return the first one available for this entity
                if (ConnectionSource.DEFAULT.equals(qualifier) && !mappedDatastores.isEmpty()) {
                    return mappedDatastores.values().iterator().next()
                }
            }
        }
        
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
     * Registers a datastore by qualifier only, without adding it to the global type-based discovery.
     */
    void registerDatastoreByQualifier(String qualifier, Datastore datastore) {
        if (qualifier != null && datastore != null) {
            datastoresByQualifier.put(qualifier, datastore)
        }
    }

    /**
     * Registers an entity-specific datastore override.
     */
    void registerEntityDatastore(String className, String qualifier, Datastore datastore) {
        if (datastore != null) {
            String q = qualifier ?: ConnectionSource.DEFAULT
            getInternalMap(entityDatastores, className).put(q, datastore)
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
     * Removes a datastore from global discovery (allDatastores and datastoresByType)
     * but keeps it in datastoresByQualifier.
     */
    void removeDatastoreFromDiscovery(Datastore datastore) {
        if (datastore == null) return
        System.err.println "REMOVING datastore from discovery: ${datastore}"
        allDatastores.remove(datastore)
        datastoresByType.remove(datastore.getClass())
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

    /**
     * Register API objects for a persistent entity.
     * Creates and registers StaticApi, InstanceApi, and ValidationApi for the given entity.
     *
     * @param entity The persistent entity
     * @param staticApi The static API implementation
     * @param instanceApi The instance API implementation
     * @param validationApi The validation API implementation
     */
    void registerEntityApis(String className, GormStaticApi staticApi, GormInstanceApi instanceApi, GormValidationApi validationApi) {
        registerApi(className, staticApi, instanceApi, validationApi)
    }

    /**
     * Register datastores for a persistent entity across multiple connection sources.
     * Handles entity-specific datastore mappings for multi-tenant and multi-datasource scenarios.
     *
     * @param className The entity class name
     * @param datastore The root datastore
     * @param connectionSourceNames The list of connection source names
     * @param entity The persistent entity (for entity-specific qualifier resolution)
     */
    void registerEntityDatastores(String className, Object datastore, List<String> connectionSourceNames, Object entity) {
        if (datastore == null) return
        
        // Register datastores for each connection source
        for (String qualifier in connectionSourceNames) {
            Object dsToRegister = datastore
            
            // Get datastore for this specific connection source if supported
            if (datastore instanceof org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) {
                try {
                    dsToRegister = ((org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) datastore).getDatastoreForConnection(qualifier)
                } catch (Throwable e) {
                    // ignore and use root datastore
                }
            }

            // Skip non-default qualifiers for multi-tenant datastores
            if (datastore instanceof org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore && 
                !ConnectionSource.DEFAULT.equals(qualifier) && 
                dsToRegister == datastore) {
                continue
            }

            registerDatastore(qualifier, (Datastore) dsToRegister)
            registerEntityDatastore(className, qualifier, (Datastore) dsToRegister)
        }

        // Determine what to register under DEFAULT for entity-specific qualifiers
        // If the entity declares explicit qualifiers that do NOT include DEFAULT (e.g. connections "test1","test2"),
        // then the first declared qualifier is the entity's primary connection
        List<String> entityQualifiers = org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport.getConnectionSourceNames((org.grails.datastore.mapping.model.PersistentEntity) entity)
        boolean entityDeclaresDefault = entityQualifiers.contains(ConnectionSource.DEFAULT) ||
                entityQualifiers.contains(ConnectionSource.ALL)
        
        if (!entityDeclaresDefault && !entityQualifiers.isEmpty()) {
            String primaryQualifier = entityQualifiers.get(0)
            Object primaryDs = datastore
            if (datastore instanceof org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) {
                try {
                    primaryDs = ((org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) datastore).getDatastoreForConnection(primaryQualifier)
                } catch (Throwable e) {
                    // fall back to root datastore
                }
            }
            registerEntityDatastore(className, ConnectionSource.DEFAULT, (Datastore) primaryDs)
        } else {
            registerEntityDatastore(className, ConnectionSource.DEFAULT, (Datastore) datastore)
        }

        // Register entity-specific non-default qualifiers so they can be resolved later
        for (String entityQualifier in entityQualifiers) {
            if (!ConnectionSource.DEFAULT.equals(entityQualifier) && !ConnectionSource.ALL.equals(entityQualifier)) {
                Object dsForQualifier = datastore
                if (datastore instanceof org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) {
                    try {
                        dsForQualifier = ((org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) datastore).getDatastoreForConnection(entityQualifier)
                    } catch (Throwable e) {
                        // fall back to main datastore if the qualifier isn't available
                    }
                }
                registerEntityDatastore(className, entityQualifier, (Datastore) dsForQualifier)
            }
        }
    }

    /**
     * Register constraints for all entities in a datastore.
     * Delegates to the ConstraintsEvaluator if available in the mapping context.
     *
     * @param datastore The datastore containing the entities
     */
    void registerConstraints(Object datastore) {
        if (datastore == null) return
        
        try {
            def context = ((Datastore) datastore).mappingContext
            def factory = context.mappingFactory
            if (factory.hasProperty('entityContext')) {
                def constraintsEvaluator = factory.entityContext.getBean(Class.forName("org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator", false, GormRegistry.classLoader))
                if (constraintsEvaluator != null) {
                    for (entity in context.persistentEntities) {
                        constraintsEvaluator.evaluate(entity.javaClass)
                    }
                }
            }
        } catch (Throwable e) {
            log.debug("Could not register GORM constraints: $e.message")
        }
    }

    /**
     * Initialize a datastore with GORM.
     * Orchestrates constraint registration and datastore registration.
     * Note: Entity-specific registration is still handled by GormEnhancer.
     *
     * @param datastore The datastore to initialize
     * @param defaultQualifier The default connection source qualifier
     */
    void initializeDatastore(Object datastore, String defaultQualifier) {
        if (datastore == null) return
        
        // Register constraints
        registerConstraints(datastore)
        
        // Register datastore by type
        registerDatastoreByType((Datastore) datastore)
        
        // Register datastore with default qualifier
        registerDatastore(defaultQualifier, (Datastore) datastore)
    }

    /**
     * Register a persistent entity with GORM, orchestrating API and datastore registration.
     * This delegates to a GormEnhancer for creating the API instances.
     *
     * @param entity The persistent entity to register
     * @param enhancer The GormEnhancer that provides API creation (optional, uses global if null)
     */
    void registerEntity(Object entity, Object enhancer = null) {
        if (entity == null) return
        
        PersistentEntity persistentEntity = (PersistentEntity) entity
        String className = persistentEntity.name
        
        if (enhancer != null) {
            GormEnhancer gormEnhancer = (GormEnhancer) enhancer
            
            // Register API singletons via registry
            if (getStaticApi(className) == null ||
                getInstanceApi(className) == null ||
                getValidationApi(className) == null) {
                final Class cls = persistentEntity.javaClass
                DatastoreResolver resolver = new DatastoreResolver() {
                    @Override Datastore resolve() { apiResolver.findDatastore(cls, null) }
                }

                GormStaticApi staticApi = gormEnhancer.getStaticApi(cls, resolver, ConnectionSource.DEFAULT)
                GormInstanceApi instanceApi = gormEnhancer.getInstanceApi(cls, resolver)
                GormValidationApi validationApi = gormEnhancer.getValidationApi(cls, resolver)

                registerEntityApis(className, staticApi, instanceApi, validationApi)
            }

            // Register datastore mappings
            Datastore datastore = gormEnhancer.datastore
            List<String> connectionSourceNames = gormEnhancer.connectionSourceNames
            registerEntityDatastores(className, datastore, connectionSourceNames, persistentEntity)
        }
    }
}

