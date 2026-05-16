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

import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.reflect.NameUtils
import org.springframework.transaction.PlatformTransactionManager

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
    private final GormStaticApiRegistry staticApiRegistry = new GormStaticApiRegistry(this)
    private final GormInstanceApiRegistry instanceApiRegistry = new GormInstanceApiRegistry(this)
    private final GormValidationApiRegistry validationApiRegistry = new GormValidationApiRegistry(this)
    
    // O(N) storage for Datastores (indexed by Qualifier)
    private final Map<String, Datastore> datastoresByQualifier = new ConcurrentHashMap<>()
    
    // Entity-specific overrides (for complex multi-datasource setups)
    private final Map<String, Map<String, Datastore>> entityDatastores = new ConcurrentHashMap<>()
    private final Map<Class, String> normalizedEntityKeysByClass = new ConcurrentHashMap<>()
    private final Map<String, String> normalizedEntityKeysByName = new ConcurrentHashMap<>()
    private final Map<String, String> normalizedQualifiers = new ConcurrentHashMap<>()

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
        classNames.addAll(instance.staticApiRegistry.keySet())
        classNames.addAll(instance.instanceApiRegistry.keySet())
        classNames.addAll(instance.validationApiRegistry.keySet())
        
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

        instance.staticApiRegistry.clear()
        instance.instanceApiRegistry.clear()
        instance.validationApiRegistry.clear()
        instance.datastoresByQualifier.clear()
        instance.entityDatastores.clear()
        instance.normalizedEntityKeysByClass.clear()
        instance.normalizedEntityKeysByName.clear()
        instance.normalizedQualifiers.clear()
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
        Class<? extends Datastore> datastoreType = datastore.getClass()
        GormApiFactory factory = apiFactoriesByDatastoreType.get(datastoreType)
        if (factory != null) {
            return factory
        }

        Class<?> current = datastoreType
        while (current != null && Datastore.isAssignableFrom(current)) {
            GormApiFactory inheritedFactory = apiFactoriesByDatastoreType.get((Class<? extends Datastore>) current)
            if (inheritedFactory != null) {
                apiFactoriesByDatastoreType.put(datastoreType, inheritedFactory)
                return inheritedFactory
            }
            current = current.getSuperclass()
        }

        for (entry in apiFactoriesByDatastoreType.entrySet()) {
            if (entry.key.isAssignableFrom(datastoreType)) {
                apiFactoriesByDatastoreType.put(datastoreType, entry.value)
                return entry.value
            }
        }

        return defaultApiFactory
    }

    <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier = null) {
        return staticApiRegistry.findStaticApi(entity, qualifier)
    }

    <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier = null) {
        return instanceApiRegistry.findInstanceApi(entity, qualifier)
    }

    <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier = null) {
        return validationApiRegistry.findValidationApi(entity, qualifier)
    }

    // Package-private helpers for AST transforms
    PlatformTransactionManager findSingleTransactionManager() {
        Datastore datastore = apiResolver.findSingleDatastore()
        if (datastore instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) datastore).transactionManager
        }
        return null
    }

    PlatformTransactionManager findSingleTransactionManager(String connectionName) {
        Datastore datastore = apiResolver.findDatastore(null, connectionName)
        if (datastore instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) datastore).transactionManager
        }
        return null
    }

    PlatformTransactionManager findTransactionManager(Class entity, String qualifier = null) {
        Datastore datastore = apiResolver.findDatastore(entity, qualifier)
        if (datastore instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) datastore).transactionManager
        }
        return null
    }

    GormStaticApi getStaticApi(String className) {
        return staticApiRegistry.get(normalizeEntityKey(className))
    }

    GormInstanceApi getInstanceApi(String className) {
        return instanceApiRegistry.get(normalizeEntityKey(className))
    }

    GormValidationApi getValidationApi(String className) {
        return validationApiRegistry.get(normalizeEntityKey(className))
    }

    /**
     * Finds a datastore for an entity and qualifier.
     */
    Datastore getDatastore(String className, String qualifier) {
        String normalizedQualifier = normalizeQualifier(qualifier)
        String normalizedClassName = normalizeEntityKey(className)

        if (normalizedClassName != null) {
            Map<String, Datastore> mappedDatastores = entityDatastores.get(normalizedClassName)
            if (mappedDatastores != null) {
                Datastore ds = mappedDatastores.get(normalizedQualifier)
                if (ds != null) return ds
                
                // If requested qualifier not found, return the first one available for this entity
                if (ConnectionSource.DEFAULT.equals(normalizedQualifier) && !mappedDatastores.isEmpty()) {
                    return mappedDatastores.values().iterator().next()
                }
            }
        }
        
        return datastoresByQualifier.get(normalizedQualifier)
    }

    /**
     * Registers GORM APIs for an entity.
     */
    void registerApi(String className, GormStaticApi staticApi, GormInstanceApi instanceApi, GormValidationApi validationApi) {
        String normalizedClassName = normalizeEntityKey(className)
        staticApiRegistry.register(normalizedClassName, staticApi)
        instanceApiRegistry.register(normalizedClassName, instanceApi)
        validationApiRegistry.register(normalizedClassName, validationApi)
    }

    /**
     * Registers a datastore for a qualifier. (O(N) part)
     */
    void registerDatastore(String qualifier, Datastore datastore) {
        if (datastore == null) return
        String normalizedQualifier = normalizeQualifier(qualifier)
        datastoresByQualifier.put(normalizedQualifier, datastore)
        allDatastores.add(datastore)
    }

    /**
     * Registers a datastore by qualifier only, without adding it to the global type-based discovery.
     */
    void registerDatastoreByQualifier(String qualifier, Datastore datastore) {
        if (qualifier != null && datastore != null) {
            datastoresByQualifier.put(normalizeQualifier(qualifier), datastore)
        }
    }

    /**
     * Registers an entity-specific datastore override.
     */
    void registerEntityDatastore(String className, String qualifier, Datastore datastore) {
        if (datastore != null) {
            String normalizedClassName = normalizeEntityKey(className)
            if (normalizedClassName == null) {
                return
            }
            String normalizedQualifier = normalizeQualifier(qualifier)
            getInternalMap(entityDatastores, normalizedClassName).put(normalizedQualifier, datastore)
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

    GormStaticApiRegistry getStaticApiRegistry() {
        return staticApiRegistry
    }

    GormInstanceApiRegistry getInstanceApiRegistry() {
        return instanceApiRegistry
    }

    GormValidationApiRegistry getValidationApiRegistry() {
        return validationApiRegistry
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

    String normalizeEntityKeyFromClass(Class entityClass) {
        if (entityClass == null) {
            return null
        }
        String existing = normalizedEntityKeysByClass.get(entityClass)
        if (existing != null) {
            return existing
        }
        String computed = NameUtils.getClassName(entityClass)
        String normalized = normalizeEntityKey(computed)
        if (normalized == null) {
            return null
        }
        String prior = normalizedEntityKeysByClass.putIfAbsent(entityClass, normalized)
        return prior != null ? prior : normalized
    }

    String normalizeEntityKey(String className) {
        if (className == null) {
            return null
        }
        String existing = normalizedEntityKeysByName.get(className)
        if (existing != null) {
            return existing
        }
        String normalized = className.trim()
        if (normalized.isEmpty()) {
            return null
        }
        String prior = normalizedEntityKeysByName.putIfAbsent(className, normalized)
        return prior != null ? prior : normalized
    }

    String normalizeQualifier(String qualifier) {
        if (qualifier == null) {
            return ConnectionSource.DEFAULT
        }
        String existing = normalizedQualifiers.get(qualifier)
        if (existing != null) {
            return existing
        }
        String normalized = qualifier.trim()
        if (normalized.isEmpty() || ConnectionSource.OLD_DEFAULT.equalsIgnoreCase(normalized)) {
            normalized = ConnectionSource.DEFAULT
        }
        String prior = normalizedQualifiers.putIfAbsent(qualifier, normalized)
        return prior != null ? prior : normalized
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
        String normalizedClassName = normalizeEntityKey(className)
        if (normalizedClassName == null) {
            return
        }
        
        // Register datastores for each connection source
        for (String qualifier in connectionSourceNames) {
            String normalizedQualifier = normalizeQualifier(qualifier)
            Object dsToRegister = datastore
            
            // Get datastore for this specific connection source if supported
            if (datastore instanceof org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) {
                try {
                    dsToRegister = ((org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) datastore).getDatastoreForConnection(normalizedQualifier)
                } catch (Throwable e) {
                    // ignore and use root datastore
                }
            }

            // Skip non-default qualifiers for multi-tenant datastores
            if (datastore instanceof org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore && 
                !ConnectionSource.DEFAULT.equals(normalizedQualifier) && 
                dsToRegister == datastore) {
                continue
            }

            registerDatastore(normalizedQualifier, (Datastore) dsToRegister)
            registerEntityDatastore(normalizedClassName, normalizedQualifier, (Datastore) dsToRegister)
        }

        // Determine what to register under DEFAULT for entity-specific qualifiers
        // If the entity declares explicit qualifiers that do NOT include DEFAULT (e.g. connections 'test1','test2'),
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
            registerEntityDatastore(normalizedClassName, ConnectionSource.DEFAULT, (Datastore) primaryDs)
        } else {
            registerEntityDatastore(normalizedClassName, ConnectionSource.DEFAULT, (Datastore) datastore)
        }

        // Register entity-specific non-default qualifiers so they can be resolved later
        for (String entityQualifier in entityQualifiers) {
            String normalizedEntityQualifier = normalizeQualifier(entityQualifier)
            if (!ConnectionSource.DEFAULT.equals(normalizedEntityQualifier) && !ConnectionSource.ALL.equals(entityQualifier)) {
                Object dsForQualifier = datastore
                if (datastore instanceof org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) {
                    try {
                        dsForQualifier = ((org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) datastore).getDatastoreForConnection(normalizedEntityQualifier)
                    } catch (Throwable e) {
                        // fall back to main datastore if the qualifier isn't available
                    }
                }
                registerEntityDatastore(normalizedClassName, normalizedEntityQualifier, (Datastore) dsForQualifier)
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
                def constraintsEvaluator = factory.entityContext.getBean(Class.forName('org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator', false, GormRegistry.classLoader))
                if (constraintsEvaluator != null) {
                    for (entity in context.persistentEntities) {
                        constraintsEvaluator.evaluate(entity.javaClass)
                    }
                }
            }
        } catch (Throwable e) {
            log.debug('Could not register GORM constraints: {}', e.message)
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
     * @param enhancer The GormEnhancer that provides API creation
     */
    void registerEntity(PersistentEntity persistentEntity, GormEnhancer enhancer) {
        assert persistentEntity != null, 'PersistentEntity is required'
        assert enhancer != null, 'GormEnhancer is required'

        String className = persistentEntity.name

        // Register API singletons via registry
        if (getStaticApi(className) == null ||
            getInstanceApi(className) == null ||
            getValidationApi(className) == null) {
            final Class cls = persistentEntity.javaClass
            DatastoreResolver resolver = createClassDatastoreResolver(cls)
            Datastore datastore = enhancer.datastore

            GormStaticApi staticApi = createStaticApi(cls, datastore, resolver, ConnectionSource.DEFAULT)
            GormInstanceApi instanceApi = createInstanceApi(cls, datastore, resolver, enhancer.failOnError, enhancer.markDirty)
            GormValidationApi validationApi = createValidationApi(cls, datastore, resolver)

            registerEntityApis(className, staticApi, instanceApi, validationApi)
        }

        // Register datastore mappings
        Datastore datastore = enhancer.datastore
        List<String> connectionSourceNames = enhancer.getConnectionSourceNames()
        registerEntityDatastores(className, datastore, connectionSourceNames, persistentEntity)
    }

    /**
     * Creates dynamic finders for the default datastore
     *
     * @return List of finder methods
     */
    List<FinderMethod> createDynamicFinders(Datastore targetDatastore) {
        createDynamicFinders(new DatastoreResolver() {
            @Override
            Datastore resolve() {
                targetDatastore
            }
        }, targetDatastore.getMappingContext())
    }

    /**
     * Creates dynamic finders for the given resolver and mapping context
     *
     * @param datastoreResolver The datastore resolver
     * @param mappingContext The mapping context
     * @return List of finder methods
     */
    List<FinderMethod> createDynamicFinders(DatastoreResolver datastoreResolver, MappingContext mappingContext) {
        return defaultApiFactory.createDynamicFinders(datastoreResolver, mappingContext)
    }

    /**
     * Create a GormStaticApi for the given class and datastore
     *
     * @param cls The domain class
     * @param datastore The datastore
     * @param resolver The datastore resolver
     * @param qualifier The connection qualifier
     * @return The GormStaticApi instance
     */
    <D> GormStaticApi<D> createStaticApi(Class<D> cls, Datastore datastore, DatastoreResolver resolver, String qualifier) {

        GormApiFactory apiFactory = getApiFactory(datastore)
        return apiFactory.createStaticApi(cls, datastore.getMappingContext(), resolver, qualifier, this)
    }

    /**
     * Create a GormInstanceApi for the given class and datastore
     *
     * @param cls The domain class
     * @param datastore The datastore
     * @param resolver The datastore resolver
     * @param failOnError Whether to fail on error
     * @param markDirty Whether to mark entities as dirty
     * @return The GormInstanceApi instance
     */
    <D> GormInstanceApi<D> createInstanceApi(Class<D> cls, Datastore datastore, DatastoreResolver resolver, boolean failOnError, boolean markDirty) {

        GormApiFactory apiFactory = getApiFactory(datastore)
        return apiFactory.createInstanceApi(cls, datastore.getMappingContext(), resolver, this, failOnError, markDirty)
    }

    /**
     * Create a GormValidationApi for the given class and datastore
     *
     * @param cls The domain class
     * @param datastore The datastore
     * @param resolver The datastore resolver
     * @return The GormValidationApi instance
     */
    <D> GormValidationApi<D> createValidationApi(Class<D> cls, Datastore datastore, DatastoreResolver resolver) {

        GormApiFactory apiFactory = getApiFactory(datastore)
        return apiFactory.createValidationApi(cls, datastore.getMappingContext(), resolver, this)
    }

    /**
     * Create a DatastoreResolver for the given class
     *
     * @param cls The class to resolve datastore for
     * @return A DatastoreResolver instance
     */
    DatastoreResolver createClassDatastoreResolver(Class cls) {

        new DatastoreResolver() {
            @Override
            Datastore resolve() {
                apiResolver.findDatastore(cls, null)
            }
        }
    }

    /**
     * Create the default DatastoreResolver
     *
     * @return A DatastoreResolver instance
     */
    DatastoreResolver createDefaultDatastoreResolver() {
        new DatastoreResolver() {
            @Override
            Datastore resolve() {
                apiResolver.findDatastore(null, null)
            }
        }
    }

    /**
     * Get or create a GormStaticApi for the given class with the specified resolver and qualifier
     * Subclasses can override to provide custom API implementations
     *
     * @param cls The domain class
     * @param datastore The datastore instance
     * @param resolver The datastore resolver
     * @param qualifier The connection qualifier
     * @return The GormStaticApi instance
     */
    <D> GormStaticApi<D> getStaticApi(Class<D> cls, Datastore datastore, DatastoreResolver resolver, String qualifier) {

        return createStaticApi(cls, datastore, resolver, qualifier)
    }

    /**
     * Get or create a GormInstanceApi for the given class with the specified resolver
     * Subclasses can override to provide custom API implementations
     *
     * @param cls The domain class
     * @param datastore The datastore instance
     * @param resolver The datastore resolver
     * @param failOnError Whether to fail on error
     * @param markDirty Whether to mark entities as dirty
     * @return The GormInstanceApi instance
     */
    <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, Datastore datastore, DatastoreResolver resolver, boolean failOnError, boolean markDirty) {

        return createInstanceApi(cls, datastore, resolver, failOnError, markDirty)
    }

    /**
     * Get or create a GormValidationApi for the given class with the specified resolver
     * Subclasses can override to provide custom API implementations
     *
     * @param cls The domain class
     * @param datastore The datastore instance
     * @param resolver The datastore resolver
     * @return The GormValidationApi instance
     */
    <D> GormValidationApi<D> getValidationApi(Class<D> cls, Datastore datastore, DatastoreResolver resolver) {

        return createValidationApi(cls, datastore, resolver)
    }
}
