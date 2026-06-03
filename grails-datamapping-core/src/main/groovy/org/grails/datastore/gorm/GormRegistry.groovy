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

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.transaction.PlatformTransactionManager

import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

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
@CompileStatic
@SuppressWarnings(['unused', 'DuplicatedCode'])
class GormRegistry {

    private static final GormRegistry instance = new GormRegistry()
    private final GormApiFactory defaultApiFactory = new DefaultGormApiFactory()
    final GormApiResolver apiResolver = new GormApiResolver(this)
    final EntityApiRegistry entityApiRegistry = new EntityApiRegistry(this)
    final GormStaticApiRegistry staticApiRegistry = entityApiRegistry.staticApiRegistry
    final GormInstanceApiRegistry instanceApiRegistry = entityApiRegistry.instanceApiRegistry
    final GormValidationApiRegistry validationApiRegistry = entityApiRegistry.validationApiRegistry
    final TransactionResolver transactionResolver = new TransactionResolver(this)
    final DynamicFinderCreator dynamicFinderCreator = new DynamicFinderCreator(this)

    final DatastoreDiscovery datastoreDiscovery = new DatastoreDiscovery()
    final Map<String, Datastore> datastoresByQualifier = datastoreDiscovery.datastoresByQualifier
    final Map<Class, Datastore> datastoresByType = datastoreDiscovery.datastoresByType
    final Set<Datastore> allDatastores = datastoreDiscovery.allDatastores

    private final Map<Class, GormApiFactory> apiFactoriesByDatastoreType = new ConcurrentHashMap<>()

    static GormRegistry getInstance() {
        return instance
    }

    /**
     * @return The default datastore
     */
    Datastore getDefaultDatastore() {
        return datastoreDiscovery.getDefaultDatastore()
    }

    /**
     * Resets the registry.
     * Nominally unused in core mapping runtime code, but heavily used by testing frameworks to reset state between spec executions.
     */
    static void reset() {
        instance.resetInstance()
    }

    private void resetInstance() {
        entityApiRegistry.clear()
        datastoreDiscovery.clear()
        apiFactoriesByDatastoreType.clear()
        GormEnhancerRegistry.getInstance().clearPreferredDatastore()
        GormEnhancerRegistry.getInstance().clearResolvingDatastoreDepth()
    }

    static <D> GormStaticApi<D> findStaticApi(Class<D> entity) {
        instance.resolveStaticApi(entity, (String) null)
    }

    static <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier) {
        instance.resolveStaticApi(entity, qualifier)
    }

    static <D> GormInstanceApi<D> findInstanceApi(Class<D> entity) {
        instance.resolveInstanceApi(entity, (String) null)
    }

    static <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier) {
        instance.resolveInstanceApi(entity, qualifier)
    }

    static <D> GormValidationApi<D> findValidationApi(Class<D> entity) {
        instance.resolveValidationApi(entity, (String) null)
    }

    static <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier) {
        instance.resolveValidationApi(entity, qualifier)
    }

    static Datastore findDatastore(Class entity) {
        instance.apiResolver.findDatastore(entity, (String) null)
    }

    static Datastore findDatastore(Class entity, String qualifier) {
        instance.apiResolver.findDatastore(entity, qualifier)
    }

    /**
     * Registers a custom GormApiFactory for a specific datastore type.
     * Nominally unused within the core mapping module, but invoked dynamically by external datastore implementations (e.g. Hibernate, MongoDB) to customize API generation.
     */
    void registerApiFactory(Class datastoreType, GormApiFactory factory) {
        apiFactoriesByDatastoreType.put(datastoreType, factory)
    }

    GormApiFactory getApiFactory(Datastore datastore) {
        GormApiFactory factory = apiFactoriesByDatastoreType.get(datastore.getClass())
        if (factory == null) {
            for (Map.Entry<Class, GormApiFactory> entry in apiFactoriesByDatastoreType.entrySet()) {
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
     * Nominally unused, but invoked at compile-time by transactional AST transformations.
     */
    PlatformTransactionManager findSingleTransactionManager() {
        transactionResolver.findSingleTransactionManager()
    }

    /**
     * Finds a single transaction manager for a specific qualifier.
     * Nominally unused, but invoked at compile-time by transactional AST transformations.
     */
    PlatformTransactionManager findSingleTransactionManager(String qualifier) {
        transactionResolver.findSingleTransactionManager(qualifier)
    }

    /**
     * Finds a transaction manager for a specific entity class and qualifier.
     * Nominally unused, but invoked at compile-time by transactional/service AST transformations.
     */
    PlatformTransactionManager findTransactionManager(Class entityClass, String qualifier) {
        transactionResolver.findTransactionManager(entityClass, qualifier)
    }

    /**
     * Finds a transaction manager for a specific entity class.
     * Nominally unused, but invoked at compile-time by transactional/service AST transformations.
     */
    PlatformTransactionManager findTransactionManager(Class entityClass) {
        transactionResolver.findTransactionManager(entityClass)
    }

    /**
     * Finds a datastore for a specific qualifier (connection name).
     */
    Datastore getDatastore(String qualifier) {
        datastoreDiscovery.getDatastore(qualifier)
    }

    /**
     * Internal method to avoid redundant normalization.
     */
    Datastore getDatastoreDirect(String normalizedClassName, String normalizedQualifier) {
        datastoreDiscovery.getDatastoreDirect(normalizedClassName, normalizedQualifier)
    }

    /**
     * Internal method to avoid ambiguity.
     */
    Datastore getDatastoreByString(String className, String qualifier) {
        datastoreDiscovery.getDatastoreByString(className, qualifier)
    }

    /**
     * Finds a datastore for a specific entity class.
     * Part of the public API for external integrations and manual datastore lookup.
     */
    Datastore getDatastore(Class entityClass) {
        datastoreDiscovery.getDatastore(entityClass)
    }

    /**
     * Finds a datastore for a specific entity class and qualifier.
     */
    Datastore getDatastore(Class entityClass, String qualifier) {
        datastoreDiscovery.getDatastore(entityClass, qualifier)
    }

    /**
     * Finds a datastore for an entity class name and qualifier.
     */
    Datastore getDatastore(String className, String qualifier) {
        datastoreDiscovery.getDatastore(className, qualifier)
    }

    /**
     * Registers GORM APIs for an entity.
     */
    void registerApi(String className, GormStaticApi staticApi, GormInstanceApi instanceApi, GormValidationApi validationApi) {
        entityApiRegistry.registerApi(className, staticApi, instanceApi, validationApi)
    }

    /**
     * Registers a datastore for a qualifier. (O(N) part)
     */
    void registerDatastore(String qualifier, Datastore datastore) {
        datastoreDiscovery.registerDatastore(qualifier, datastore)
    }

    /**
     * Initializes a datastore, registering its type and default qualifier.
     */
    void initializeDatastore(Datastore datastore) {
        datastoreDiscovery.initializeDatastore(datastore)
    }

    /**
     * Registers a datastore.
     */
    void registerDatastore(Datastore datastore) {
        datastoreDiscovery.registerDatastore(datastore)
    }

    /**
     * Registers a datastore by its type.
     * Nominally unused in core mapping runtime code, but used by test suites and external integrations.
     */
    void registerDatastoreByType(Datastore datastore) {
        datastoreDiscovery.registerDatastoreByType(datastore)
    }

    /**
     * Registers a datastore by qualifier only, without adding it to the global type-based discovery.
     */
    void registerDatastoreByQualifier(String qualifier, Datastore datastore) {
        datastoreDiscovery.registerDatastoreByQualifier(qualifier, datastore)
    }

    /**
     * Removes a datastore from discovery by its class type.
     * Nominally unused in core mapping runtime code, but used by testing frameworks to clean up dynamic datastores.
     */
    void removeDatastoreByType(Class datastoreType) {
        datastoreDiscovery.removeDatastoreByType(datastoreType)
    }

    /**
     * Removes a datastore from discovery by its instance type.
     * Nominally unused in core mapping runtime code, but used by testing frameworks to clean up dynamic datastores.
     */
    void removeDatastoreByType(Datastore datastore) {
        datastoreDiscovery.removeDatastoreByType(datastore)
    }

    /**
     * Removes a datastore from global discovery (allDatastores and datastoresByType)
     * but keeps it in datastoresByQualifier.
     * Nominally unused in core mapping runtime code, but used by test suites to verify multi-datastore isolation.
     */
    void removeDatastoreFromDiscovery(Datastore datastore) {
        datastoreDiscovery.removeDatastoreFromDiscovery(datastore)
    }

    /**
     * Completely removes a datastore from the registry.
     */
    void removeDatastore(Datastore datastore) {
        datastoreDiscovery.removeDatastore(datastore)
        entityApiRegistry.removeDatastore(datastore)
    }

    /**
     * Removes a datastore for a specific entity.
     */
    void removeEntityDatastore(String className, Datastore datastore) {
        datastoreDiscovery.removeEntityDatastore(className, datastore)
    }

    /**
     * Checks if a specific datastore is explicitly registered for an entity.
     */
    boolean isDatastoreRegisteredForEntity(String className, Datastore datastore) {
        datastoreDiscovery.isDatastoreRegisteredForEntity(className, datastore)
    }

    GormStaticApi getStaticApi(Class entityClass) {
        entityApiRegistry.getStaticApi(entityClass)
    }

    GormInstanceApi getInstanceApi(Class entityClass) {
        entityApiRegistry.getInstanceApi(entityClass)
    }

    GormValidationApi getValidationApi(Class entityClass) {
        entityApiRegistry.getValidationApi(entityClass)
    }

    GormStaticApi getStaticApi(Class entityClass, String qualifier) {
        entityApiRegistry.getStaticApi(entityClass, qualifier)
    }

    GormInstanceApi getInstanceApi(Class entityClass, String qualifier) {
        entityApiRegistry.getInstanceApi(entityClass, qualifier)
    }

    GormValidationApi getValidationApi(Class entityClass, String qualifier) {
        entityApiRegistry.getValidationApi(entityClass, qualifier)
    }

    GormStaticApi resolveStaticApi(Class entityClass) {
        entityApiRegistry.resolveStaticApi(entityClass)
    }

    GormStaticApi resolveStaticApi(Class entityClass, String qualifier) {
        entityApiRegistry.resolveStaticApi(entityClass, qualifier)
    }

    GormInstanceApi resolveInstanceApi(Class entityClass) {
        entityApiRegistry.resolveInstanceApi(entityClass)
    }

    GormInstanceApi resolveInstanceApi(Class entityClass, String qualifier) {
        entityApiRegistry.resolveInstanceApi(entityClass, qualifier)
    }

    GormValidationApi resolveValidationApi(Class entityClass) {
        entityApiRegistry.resolveValidationApi(entityClass)
    }

    GormValidationApi resolveValidationApi(Class entityClass, String qualifier) {
        entityApiRegistry.resolveValidationApi(entityClass, qualifier)
    }

    GormStaticApi getStaticApi(String className) {
        entityApiRegistry.getStaticApi(className)
    }

    GormStaticApi getStaticApi(String className, String qualifier) {
        entityApiRegistry.getStaticApi(className, qualifier)
    }

    GormInstanceApi getInstanceApi(String className) {
        entityApiRegistry.getInstanceApi(className)
    }

    GormInstanceApi getInstanceApi(String className, String qualifier) {
        entityApiRegistry.getInstanceApi(className, qualifier)
    }

    GormValidationApi getValidationApi(String className) {
        entityApiRegistry.getValidationApi(className)
    }

    GormValidationApi getValidationApi(String className, String qualifier) {
        entityApiRegistry.getValidationApi(className, qualifier)
    }

    String normalizeEntityKey(Object entityKey) {
        datastoreDiscovery.normalizeEntityKey(entityKey)
    }

    /**
     * @deprecated Use {@code normalizeEntityKey(Class)}.
     */
    @Deprecated
    String normalizeEntityKeyFromClass(Class entityClass) {
        normalizeEntityKey(entityClass)
    }

    /**
     * @deprecated Use {@code normalizeQualifier(String)}.
     */
    @Deprecated
    String normalizeQualifierByString(String qualifier) {
        normalizeQualifier(qualifier)
    }

    /**
     * Register API objects for a persistent entity.
     * Creates and registers StaticApi, InstanceApi, and ValidationApi for the given entity.
     * Part of the public API for external plugins and test environments.
     *
     * @param className The entity class name
     * @param staticApi The static API implementation
     * @param instanceApi The instance API implementation
     * @param validationApi The validation API implementation
     */
    void registerEntityApis(String className, GormStaticApi staticApi, GormInstanceApi instanceApi, GormValidationApi validationApi) {
        entityApiRegistry.registerEntityApis(className, staticApi, instanceApi, validationApi)
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
        datastoreDiscovery.registerEntityDatastores(className, datastore, connectionSourceNames, entity)
    }

    /**
     * Registers an entity-specific datastore override.
     */
    void registerEntityDatastore(String className, String qualifier, Datastore datastore) {
        datastoreDiscovery.registerEntityDatastore(className, qualifier, datastore)
    }

    String normalizeEntityKey(Class cls) {
        datastoreDiscovery.normalizeEntityKey(cls)
    }

    String normalizeEntityKey(String className) {
        datastoreDiscovery.normalizeEntityKey(className)
    }

    String normalizeQualifier(String qualifier) {
        datastoreDiscovery.normalizeQualifier(qualifier)
    }

    /**
     * Creates dynamic finders for the default datastore
     *
     * @return List of finder methods
     */
    List<FinderMethod> createDynamicFinders(Datastore targetDatastore) {
        dynamicFinderCreator.createDynamicFinders(targetDatastore)
    }

    /**
     * Creates dynamic finders using the given resolver and mapping context.
     *
     * @param resolver The datastore resolver
     * @param mappingContext The mapping context
     * @return List of finder methods
     */
    List<FinderMethod> createDynamicFinders(DatastoreResolver resolver, MappingContext mappingContext) {
        dynamicFinderCreator.createDynamicFinders(resolver, mappingContext)
    }

    /**
     * Create a DatastoreResolver for a class and optional qualifier.
     */
    DatastoreResolver createClassDatastoreResolver(Class cls, String qualifier = ConnectionSource.DEFAULT) {
        dynamicFinderCreator.createClassDatastoreResolver(cls, qualifier)
    }

    /**
     * Create a GormStaticApi instance
     */
    GormStaticApi createStaticApi(Class cls, Datastore datastore, DatastoreResolver resolver, String qualifier) {
        entityApiRegistry.createStaticApi(cls, datastore, resolver, qualifier)
    }

    GormInstanceApi createInstanceApi(Class cls, Datastore datastore, DatastoreResolver resolver, boolean failOnError, boolean markDirty) {
        entityApiRegistry.createInstanceApi(cls, datastore, resolver, failOnError, markDirty)
    }

    GormValidationApi createValidationApi(Class cls, Datastore datastore, DatastoreResolver resolver) {
        entityApiRegistry.createValidationApi(cls, datastore, resolver)
    }

    /**
     * Register API objects for a persistent entity.
     * Part of the public API for external plugins and test environments.
     */
    void registerEntityApis(Class cls, GormStaticApi staticApi, GormInstanceApi instanceApi, GormValidationApi validationApi) {
        entityApiRegistry.registerEntityApis(cls, staticApi, instanceApi, validationApi)
    }

    /**
     * Register constraints for all entities in a datastore.
     * Delegates to the ConstraintsEvaluator if available in the mapping context.
     *
     * @param datastore The datastore containing the entities
     */
    @CompileDynamic
    static void registerConstraints(Object datastore) {
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
        Datastore typedDatastore = (Datastore) datastore
        registerDatastore(defaultQualifier, typedDatastore)
        datastoresByType.put(typedDatastore.getClass(), typedDatastore)
    }

    /**
     * Register a persistent entity with GORM, orchestrating API and datastore registration.
     * This delegates to a GormEnhancer for creating the API instances.
     *
     * @param entity The persistent entity to register
     * @param enhancer The GormEnhancer that provides API creation
     */
    void registerEntity(PersistentEntity persistentEntity, GormEnhancer enhancer) {
        if (!persistentEntity) {
            throw new IllegalArgumentException('Argument [persistentEntity] cannot be null')
        }
        if (!enhancer) {
            throw new IllegalArgumentException('Argument [enhancer] cannot be null')
        }

        String className = persistentEntity.name

        // Always (re)register API singletons so classloader or datastore changes do not leave stale API instances.
        final Class cls = persistentEntity.javaClass
        DatastoreResolver resolver = createClassDatastoreResolver(cls)
        Datastore datastore = enhancer.datastore

        GormStaticApi staticApi = createStaticApi(cls, datastore, resolver, ConnectionSource.DEFAULT)
        GormInstanceApi instanceApi = createInstanceApi(cls, datastore, resolver, enhancer.failOnError, enhancer.markDirty)
        GormValidationApi validationApi = createValidationApi(cls, datastore, resolver)

        registerEntityApis(className, staticApi, instanceApi, validationApi)

        // Register datastore mappings
        Datastore datastoreForMappings = enhancer.datastore
        List<String> qualifiers = enhancer.allQualifiers(datastore, persistentEntity)
        registerEntityDatastores(className, datastoreForMappings, qualifiers, persistentEntity)
    }

}
