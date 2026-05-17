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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import grails.gorm.multitenancy.CurrentTenantHolder
import grails.gorm.MultiTenant
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

    private final Map<String, Datastore> datastoresByQualifier = new ConcurrentHashMap<>()
    private final Map<String, Map<String, Datastore>> entityDatastores = new ConcurrentHashMap<>()
    private final Map<Class, String> normalizedEntityKeysByClass = new ConcurrentHashMap<>()
    private final Map<String, String> normalizedEntityKeysByName = new ConcurrentHashMap<>()
    private final Map<String, String> normalizedQualifiers = new ConcurrentHashMap<>()
    private final Map<Class, Datastore> datastoresByType = new ConcurrentHashMap<>()
    private final Map<Class, GormApiFactory> apiFactoriesByDatastoreType = new ConcurrentHashMap<>()
    private final Set<Datastore> allDatastores = Collections.newSetFromMap(new ConcurrentHashMap<Datastore, Boolean>())

    static GormRegistry getInstance() {
        return instance
    }

    /**
     * Resets the registry.
     */
    static void reset() {
        instance.resetInstance()
    }

    private void resetInstance() {
        staticApiRegistry.clear()
        instanceApiRegistry.clear()
        validationApiRegistry.clear()
        datastoresByQualifier.clear()
        entityDatastores.clear()
        normalizedEntityKeysByClass.clear()
        normalizedEntityKeysByName.clear()
        normalizedQualifiers.clear()
        datastoresByType.clear()
        apiFactoriesByDatastoreType.clear()
        allDatastores.clear()
    }

    @Deprecated
    static <D> GormStaticApi<D> findStaticApi(Class<D> entity) {
        instance.resolveStaticApi(entity, (String) null)
    }

    /**
     * @deprecated Use {@code GormRegistry.getInstance().findStaticApi(entity, qualifier)}.
     */
    @Deprecated
    static <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier) {
        instance.resolveStaticApi(entity, qualifier)
    }

    /**
     * @deprecated Use {@code GormRegistry.getInstance().findInstanceApi(entity, qualifier)}.
     */
    @Deprecated
    static <D> GormInstanceApi<D> findInstanceApi(Class<D> entity) {
        instance.resolveInstanceApi(entity, (String) null)
    }

    /**
     * @deprecated Use {@code GormRegistry.getInstance().findInstanceApi(entity, qualifier)}.
     */
    @Deprecated
    static <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier) {
        instance.resolveInstanceApi(entity, qualifier)
    }

    /**
     * @deprecated Use {@code GormRegistry.getInstance().findValidationApi(entity, qualifier)}.
     */
    @Deprecated
    static <D> GormValidationApi<D> findValidationApi(Class<D> entity) {
        instance.resolveValidationApi(entity, (String) null)
    }

    /**
     * @deprecated Use {@code GormRegistry.getInstance().findValidationApi(entity, qualifier)}.
     */
    @Deprecated
    static <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier) {
        instance.resolveValidationApi(entity, qualifier)
    }

    /**
     * @deprecated Use {@code GormRegistry.getInstance().getApiResolver().findDatastore(entity, qualifier)}.
     */
    @Deprecated
    static Datastore findDatastore(Class entity) {
        instance.apiResolver.findDatastore(entity, (String) null)
    }

    /**
     * @deprecated Use {@code GormRegistry.getInstance().getApiResolver().findDatastore(entity, qualifier)}.
     */
    @Deprecated
    static Datastore findDatastore(Class entity, String qualifier) {
        instance.apiResolver.findDatastore(entity, qualifier)
    }

    GormApiResolver getApiResolver() {
        return apiResolver
    }

    void registerApiFactory(Class datastoreType, GormApiFactory factory) {
        apiFactoriesByDatastoreType.put(datastoreType, factory)
    }

    GormApiFactory getApiFactory(Datastore datastore) {
        GormApiFactory factory = apiFactoriesByDatastoreType.get(datastore.getClass())
        if (factory == null) {
            for (entry in apiFactoriesByDatastoreType) {
                if (entry.key.isInstance(datastore)) {
                    return entry.value
                }
            }
            return defaultApiFactory
        }
        return factory
    }

    /**
     * Finds a single transaction manager if only one datastore is registered.
     */
    PlatformTransactionManager findSingleTransactionManager() {
        return findSingleTransactionManager(ConnectionSource.DEFAULT)
    }

    /**
     * Finds a single transaction manager for a specific qualifier.
     */
    PlatformTransactionManager findSingleTransactionManager(String qualifier) {
        Datastore ds = datastoresByQualifier.get(normalizeQualifier(qualifier))
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) ds).transactionManager
        }
        return null
    }

    /**
     * Finds a transaction manager for a specific entity class.
     */
    PlatformTransactionManager findTransactionManager(Class entityClass) {
        Datastore ds = getDatastore(entityClass)
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore) ds).transactionManager
        }
        return null
    }

    /**
     * Finds a datastore for a specific qualifier (connection name).
     */
    Datastore getDatastore(String qualifier) {
        return getDatastoreByString((String) null, qualifier)
    }

    /**
     * Internal method to avoid redundant normalization.
     */
    Datastore getDatastoreDirect(String normalizedClassName, String normalizedQualifier) {
        if (normalizedClassName != null) {
            Map<String, Datastore> mappedDatastores = entityDatastores.get(normalizedClassName)
            if (mappedDatastores != null) {
                Datastore ds = mappedDatastores.get(normalizedQualifier)
                if (ds != null) {
                    return ds
                }
            }
        }

        Datastore ds = datastoresByQualifier.get(normalizedQualifier)
        if (ds == null && ConnectionSource.DEFAULT.equals(normalizedQualifier)) {
            if (allDatastores.size() == 1) {
                return allDatastores.iterator().next()
            }
        }
        return ds
    }

    /**
     * Internal method to avoid ambiguity.
     */
    Datastore getDatastoreByString(String className, String qualifier) {
        return getDatastoreDirect(className != null ? normalizeEntityKey(className) : null, normalizeQualifier(qualifier))
    }

    /**
     * Finds a datastore for a specific entity class.
     */
    Datastore getDatastore(Class entityClass) {
        return getDatastore(entityClass, ConnectionSource.DEFAULT)
    }

    /**
     * Finds a datastore for a specific entity class and qualifier.
     */
    Datastore getDatastore(Class entityClass, String qualifier) {
        return getDatastoreByString(entityClass != null ? normalizeEntityKey(entityClass) : (String) null, qualifier)
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
     * Initializes a datastore, registering its type and default qualifier.
     */
    void initializeDatastore(Datastore datastore) {
        if (datastore == null) return
        registerDatastore(ConnectionSource.DEFAULT, datastore)
        datastoresByType.put(datastore.getClass(), datastore)
    }

    /**
     * Registers a datastore.
     */
    void registerDatastore(Datastore datastore) {
        initializeDatastore(datastore)
    }

    /**
     * Registers a datastore by its type.
     */
    void registerDatastoreByType(Datastore datastore) {
        if (datastore == null) return
        datastoresByType.put(datastore.getClass(), datastore)
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

    void removeDatastoreByType(Class datastoreType) {
        if (datastoreType == null) return
        datastoresByType.remove(datastoreType)
    }

    void removeDatastoreByType(Datastore datastore) {
        if (datastore == null) return
        removeDatastoreByType(datastore.getClass())
    }

    /**
     * Removes a datastore from global discovery (allDatastores and datastoresByType)
     * but keeps it in datastoresByQualifier.
     */
    void removeDatastoreFromDiscovery(Datastore datastore) {
        if (datastore == null) return
        allDatastores.remove(datastore)
        datastoresByType.remove(datastore.getClass())
    }

    /**
     * Completely removes a datastore from the registry.
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

    GormStaticApi getStaticApi(Class entityClass) {
        return staticApiRegistry.get(normalizeEntityKey(entityClass))
    }

    GormInstanceApi getInstanceApi(Class entityClass) {
        return instanceApiRegistry.get(normalizeEntityKey(entityClass))
    }

    GormValidationApi getValidationApi(Class entityClass) {
        return validationApiRegistry.get(normalizeEntityKey(entityClass))
    }

    GormStaticApi getStaticApi(Class entityClass, String qualifier) {
        return staticApiRegistry.get(normalizeEntityKey(entityClass), normalizeQualifier(qualifier))
    }

    GormInstanceApi getInstanceApi(Class entityClass, String qualifier) {
        return instanceApiRegistry.get(normalizeEntityKey(entityClass), normalizeQualifier(qualifier))
    }

    GormValidationApi getValidationApi(Class entityClass, String qualifier) {
        return validationApiRegistry.get(normalizeEntityKey(entityClass), normalizeQualifier(qualifier))
    }

    GormStaticApi resolveStaticApi(Class entityClass) {
        return resolveStaticApi(entityClass, (String) null)
    }

    GormStaticApi resolveStaticApi(Class entityClass, String qualifier) {
        String normalizedClassName = normalizeEntityKey(entityClass)
        String normalizedQualifier = normalizeQualifier(qualifier)
        
        if (ConnectionSource.DEFAULT.equals(normalizedQualifier)) {
            if (MultiTenant.isAssignableFrom(entityClass)) {
                Datastore ds = getDatastoreDirect(normalizedClassName, normalizedQualifier)
                if (ds instanceof MultiTenantCapableDatastore) {
                    Serializable tenantId = CurrentTenantHolder.get((MultiTenantCapableDatastore) ds)
                    if (tenantId != null) {
                        return staticApiRegistry.getDirect(normalizedClassName, tenantId.toString())
                    }
                }
            }
        }
        
        return staticApiRegistry.getDirect(normalizedClassName, normalizedQualifier)
    }

    GormInstanceApi resolveInstanceApi(Class entityClass) {
        return resolveInstanceApi(entityClass, (String) null)
    }

    GormInstanceApi resolveInstanceApi(Class entityClass, String qualifier) {
        String normalizedClassName = normalizeEntityKey(entityClass)
        String normalizedQualifier = normalizeQualifier(qualifier)

        if (ConnectionSource.DEFAULT.equals(normalizedQualifier)) {
            if (MultiTenant.isAssignableFrom(entityClass)) {
                Datastore ds = getDatastoreDirect(normalizedClassName, normalizedQualifier)
                if (ds instanceof MultiTenantCapableDatastore) {
                    Serializable tenantId = CurrentTenantHolder.get((MultiTenantCapableDatastore) ds)
                    if (tenantId != null) {
                        return instanceApiRegistry.getDirect(normalizedClassName, tenantId.toString())
                    }
                }
            }
        }
        
        return instanceApiRegistry.getDirect(normalizedClassName, normalizedQualifier)
    }

    GormValidationApi resolveValidationApi(Class entityClass) {
        return resolveValidationApi(entityClass, (String) null)
    }

    GormValidationApi resolveValidationApi(Class entityClass, String qualifier) {
        String normalizedClassName = normalizeEntityKey(entityClass)
        String normalizedQualifier = normalizeQualifier(qualifier)

        if (ConnectionSource.DEFAULT.equals(normalizedQualifier)) {
            if (MultiTenant.isAssignableFrom(entityClass)) {
                Datastore ds = getDatastoreDirect(normalizedClassName, normalizedQualifier)
                if (ds instanceof MultiTenantCapableDatastore) {
                    Serializable tenantId = CurrentTenantHolder.get((MultiTenantCapableDatastore) ds)
                    if (tenantId != null) {
                        return validationApiRegistry.getDirect(normalizedClassName, tenantId.toString())
                    }
                }
            }
        }
        
        return validationApiRegistry.getDirect(normalizedClassName, normalizedQualifier)
    }

    GormStaticApi getStaticApi(String className) {
        return staticApiRegistry.get(normalizeEntityKey(className))
    }

    GormStaticApi getStaticApi(String className, String qualifier) {
        return staticApiRegistry.get(normalizeEntityKey(className), normalizeQualifier(qualifier))
    }

    GormInstanceApi getInstanceApi(String className) {
        return instanceApiRegistry.get(normalizeEntityKey(className))
    }

    GormInstanceApi getInstanceApi(String className, String qualifier) {
        return instanceApiRegistry.get(normalizeEntityKey(className), normalizeQualifier(qualifier))
    }

    GormValidationApi getValidationApi(String className) {
        return validationApiRegistry.get(normalizeEntityKey(className))
    }

    GormValidationApi getValidationApi(String className, String qualifier) {
        return validationApiRegistry.get(normalizeEntityKey(className), normalizeQualifier(qualifier))
    }
    GormInstanceApiRegistry getInstanceApiRegistry() {
        return instanceApiRegistry
    }

    GormValidationApiRegistry getValidationApiRegistry() {
        return validationApiRegistry
    }

    Set<Datastore> getAllDatastores() {
        return allDatastores
    }

    Map<Class, Datastore> getDatastoresByType() {
        return datastoresByType
    }

    private Map<String, Datastore> getInternalMap(Map<String, Map<String, Datastore>> rootMap, String key) {
        Map<String, Datastore> map = rootMap.get(key)
        if (map == null) {
            map = new ConcurrentHashMap<String, Datastore>()
            Map<String, Datastore> prior = rootMap.putIfAbsent(key, map)
            if (prior != null) {
                return prior
            }
        }
        return map
    }

    String normalizeEntityKey(Object entityKey) {
        if (entityKey == null) {
            return null
        }
        if (entityKey instanceof Class) {
            Class entityClass = (Class) entityKey
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
        } else {
            String className = entityKey.toString()
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
     * @param datastore The datastore to register
     * @param connectionSourceNames The connection source names to register the datastore for
     * @param entity The persistent entity (for entity-specific qualifier resolution)
     */
    void registerEntityDatastores(String className, Object datastore, List<String> connectionSourceNames, Object entity) {

        if (datastore == null) return
        String normalizedClassName = normalizeEntityKey(className)
        if (normalizedClassName == null) {
            return
        }
        
        // Register datastores for each connection source
        for (String connectionSourceName in connectionSourceNames) {
            registerEntityDatastore(normalizedClassName, connectionSourceName, (Datastore) datastore)
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
     * Creates dynamic finders using the given resolver and mapping context
     *
     * @param resolver The datastore resolver
     * @param mappingContext The mapping context
     * @return List of finder methods
     */
    List<FinderMethod> createDynamicFinders(DatastoreResolver resolver, MappingContext mappingContext) {
        // Implementation provided by GormEnhancer or specialized factories
        return []
    }

    /**
     * Create a DatastoreResolver for a class and optional qualifier.
     */
    DatastoreResolver createClassDatastoreResolver(Class cls, String qualifier = ConnectionSource.DEFAULT) {
        String normalizedClassName = normalizeEntityKey(cls)
        String normalizedQualifier = normalizeQualifier(qualifier)
        return new DatastoreResolver() {
            @Override
            Datastore resolve() {
                getDatastoreDirect(normalizedClassName, normalizedQualifier)
            }
        }
    }

    /**
     * Create a GormStaticApi instance
     */
    GormStaticApi createStaticApi(Class cls, Datastore datastore, DatastoreResolver resolver, String qualifier) {
        return getApiFactory(datastore).createStaticApi(cls, datastore.mappingContext, resolver, qualifier, this)
    }

    /**
     * Create a GormInstanceApi instance
     */
    GormInstanceApi createInstanceApi(Class cls, Datastore datastore, DatastoreResolver resolver, boolean failOnError, boolean markDirty) {
        return getApiFactory(datastore).createInstanceApi(cls, datastore.mappingContext, resolver, this, failOnError, markDirty)
    }

    /**
     * Create a GormValidationApi instance
     */
    GormValidationApi createValidationApi(Class cls, Datastore datastore, DatastoreResolver resolver) {
        return getApiFactory(datastore).createValidationApi(cls, datastore.mappingContext, resolver, this)
    }

    /**
     * Register API objects for a persistent entity
     */
    void registerEntityApis(Class cls, GormStaticApi staticApi, GormInstanceApi instanceApi, GormValidationApi validationApi) {
        registerEntityApis(cls.name, staticApi, instanceApi, validationApi)
    }

    /**
     * Register constraints for all entities in a datastore.
     * Delegates to the ConstraintsEvaluator if available in the mapping context.
     *
     * @param datastore The datastore containing the entities
     */
    @CompileDynamic
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
        if (persistentEntity == null) return

        String className = persistentEntity.name

        if (enhancer != null) {
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
    }

}
