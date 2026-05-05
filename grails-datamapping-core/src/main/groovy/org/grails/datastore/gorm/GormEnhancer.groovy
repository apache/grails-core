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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.CurrentTenantHolder
import org.grails.datastore.gorm.finders.CountByFinder
import org.grails.datastore.gorm.finders.FindAllByBooleanFinder
import org.grails.datastore.gorm.finders.FindAllByFinder
import org.grails.datastore.gorm.finders.FindByBooleanFinder
import org.grails.datastore.gorm.finders.FindByFinder
import org.grails.datastore.gorm.finders.FindOrCreateByFinder
import org.grails.datastore.gorm.finders.FindOrSaveByFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.finders.ListOrderByFinder
import org.grails.datastore.mapping.core.Datastore

import org.springframework.transaction.support.TransactionSynchronizationManager
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.reflect.MetaClassUtils
import org.grails.datastore.mapping.reflect.NameUtils
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager

import java.util.concurrent.ConcurrentHashMap

/**
 * Enhances a class with GORM methods
 *
 * @author Graeme Rocher
 */
@CompileStatic
class GormEnhancer implements Closeable {

    private static final ThreadLocal<Integer> RESOLVING_DATASTORE = ThreadLocal.withInitial { 0 }
    
    private static final ThreadLocal<Datastore> PREFERRED_DATASTORE = new ThreadLocal<>()

    private static final Map<String, Map<String, Closure>> NAMED_QUERIES = new ConcurrentHashMap<>()

    private final GormRegistry registry
    private final List<String> connectionSourceNames
    final Datastore datastore
    PlatformTransactionManager transactionManager
    boolean failOnError = false
    boolean markDirty = true
    private static final Logger log = LoggerFactory.getLogger(GormEnhancer)

    /**
     * Whether to include external entities
     */
    boolean includeExternal = true

    GormEnhancer(Datastore datastore) {
        this(datastore, null, new ConnectionSourceSettings(), GormRegistry.getInstance())
    }

    GormEnhancer(Datastore datastore, PlatformTransactionManager transactionManager, boolean failOnError = false, boolean markDirty = true) {
        this(datastore, transactionManager, new ConnectionSourceSettings().failOnError(failOnError).markDirty(markDirty), GormRegistry.getInstance())
    }

    /**
     * Construct a new GormEnhancer for the given arguments
     *
     * @param datastore The datastore
     * @param transactionManager The transaction manager
     * @param settings The settings
     */
    GormEnhancer(Datastore datastore, PlatformTransactionManager transactionManager, ConnectionSourceSettings settings) {
        this(datastore, transactionManager, settings, GormRegistry.getInstance())
    }

    /**
     * Construct a new GormEnhancer for the given arguments
     *
     * @param datastore The datastore
     * @param transactionManager The transaction manager
     * @param settings The settings
     * @param registry The registry to use
     */
    GormEnhancer(Datastore datastore, PlatformTransactionManager transactionManager, ConnectionSourceSettings settings, GormRegistry registry) {
        this.datastore = datastore
        this.registry = registry ?: GormRegistry.getInstance()
        this.failOnError = settings.isFailOnError()
        Boolean markDirty = settings.getMarkDirty()
        this.markDirty = markDirty == null ? true : markDirty
        this.transactionManager = transactionManager

        if (datastore instanceof ConnectionSourcesProvider) {
            ConnectionSources connectionSources = ((ConnectionSourcesProvider) datastore).connectionSources
            if (connectionSources != null) {
                Iterable<ConnectionSource> allConnections = connectionSources.allConnectionSources
                if (allConnections instanceof Collection) {
                    this.connectionSourceNames = ((Collection<ConnectionSource>) allConnections).collect { it.name }
                } else {
                    this.connectionSourceNames = allConnections?.collect { it.name } ?: [ConnectionSource.DEFAULT]
                }
            } else {
                this.connectionSourceNames = [ConnectionSource.DEFAULT]
            }
        } else {
            this.connectionSourceNames = [ConnectionSource.DEFAULT]
        }

        if (datastore != null) {
            registerConstraints(datastore)
            this.registry.registerDatastoreByType(datastore)
            String qualifier = ConnectionSource.DEFAULT
            if (datastore instanceof ConnectionSourcesProvider) {
                qualifier = ((ConnectionSourcesProvider) datastore).connectionSources.defaultConnectionSource.name
            }
            this.registry.registerDatastore(qualifier, datastore)
        }

        for (entity in datastore.mappingContext.persistentEntities) {
            registerEntity(entity)
        }
    }

    /**
     * Registers a new entity with the GORM enhancer
     *
     * @param entity The entity
     */
    void registerEntity(PersistentEntity entity) {
        Datastore datastore = this.datastore
        if (appliesToDatastore(datastore, entity)) {
            final Class cls = entity.javaClass
            String className = entity.name

            // 1. Register API singletons (O(M) part)
            if (registry.getStaticApi(className) == null) {
                final MappingContext mappingContext = entity.mappingContext
                DatastoreResolver resolver = new DatastoreResolver() {
                    @Override Datastore resolve() { GormEnhancer.findDatastore(cls) }
                }

                GormStaticApi staticApi = getStaticApi(cls, resolver, ConnectionSource.DEFAULT)
                GormInstanceApi instanceApi = getInstanceApi(cls, resolver)
                GormValidationApi validationApi = getValidationApi(cls, resolver)

                registry.registerApi(className, staticApi, instanceApi, validationApi)
            }

            // 2. Register Datastores (O(N) part)
            for (String qualifier in connectionSourceNames) {
                Datastore dsToRegister = datastore
                if (datastore instanceof org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) {
                    try {
                        dsToRegister = ((org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore)datastore).getDatastoreForConnection(qualifier)
                    } catch (Throwable e) {
                        // ignore
                    }
                }

                if (datastore instanceof MultiTenantCapableDatastore && !ConnectionSource.DEFAULT.equals(qualifier) && dsToRegister == datastore) {
                    continue
                }

                registry.registerDatastore(qualifier, dsToRegister)
                registry.registerEntityDatastore(className, qualifier, dsToRegister)
            }
            
            registry.registerEntityDatastore(className, ConnectionSource.DEFAULT, datastore)

            addStaticMethods(entity)
            addInstanceMethods(entity, false)
        }
    }

    /**
     * Obtain all of the qualifiers (typically the connection names) for the datastore and entity
     *
     * @param datastore The datastore
     * @param entity The entity
     * @return The qualifiers
     */
    @CompileDynamic
    List<String> allQualifiers(Datastore datastore, PersistentEntity entity) {
        List<String> qualifiers = new ArrayList<>()
        qualifiers.addAll(ConnectionSourcesSupport.getConnectionSourceNames(entity))

        boolean isMultiTenant = MultiTenant.isAssignableFrom(entity.javaClass)
        boolean hasExplicitAll = qualifiers.contains(ConnectionSource.ALL)
        boolean hasExplicitNonDefaultDatasource = isMultiTenant &&
                !hasExplicitAll &&
                qualifiers.size() > 0 &&
                !qualifiers.equals(ConnectionSourcesSupport.DEFAULT_CONNECTION_SOURCE_NAMES)

        if ((isMultiTenant || hasExplicitAll) && !hasExplicitNonDefaultDatasource) {
            qualifiers.clear()
            if (datastore == this.datastore) {
                qualifiers.addAll(connectionSourceNames)
            } else {
                def className = entity.name
                for (String q in connectionSourceNames) {
                    if (registry.getDatastore(className, q) == datastore) {
                        qualifiers.add(q)
                    }
                }

                if (qualifiers.isEmpty()) {
                    for (String q in registry.datastoresByQualifier.keySet()) {
                        if (registry.datastoresByQualifier.get(q) == datastore) {
                            qualifiers.add(q)
                        }
                    }
                }
            }
        }

        if (qualifiers.isEmpty()) {
            qualifiers.add(ConnectionSource.DEFAULT)
        }
        return qualifiers.unique()
    }

    /**
     * @return The GORM registry instance
     */
    static GormRegistry getRegistry() {
        return GormRegistry.instance
    }

    public static void setPreferredDatastore(Datastore datastore) {
        PREFERRED_DATASTORE.set(datastore)
    }

    /**
     * @return The preferred datastore for the current thread
     */
    static Datastore getPreferredDatastore() {
        return PREFERRED_DATASTORE.get()
    }

    static void clearPreferredDatastore() {
        PREFERRED_DATASTORE.remove()
    }

    /**
     * Find the tenant id for the given entity
     *
     * @param entity
     * @return
     */
    protected static String findTenantId(Class entity) {
        if (MultiTenant.isAssignableFrom(entity)) {
            Datastore defaultDatastore = GormRegistry.instance.getDatastore(entity.name, ConnectionSource.DEFAULT)
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

    /**
     * Find a static API for the give entity type and qualifier (the connection name)
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return A static API
     *
     * @throws IllegalStateException if no static API is found for the type
     */
    static <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        GormRegistry registry = GormRegistry.instance
        GormStaticApi api = registry.getStaticApi(className)
        if (api == null) {
            throw stateException(entity)
        }

        if (qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            return api.forQualifier(qualifier)
        }
        return (GormStaticApi<D>) api
    }

    /**
     * Finds an instance API for the given entity class
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return The instance API
     */
    static <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        GormRegistry registry = GormRegistry.instance
        GormInstanceApi api = registry.getInstanceApi(className)
        if (api == null) {
            throw stateException(entity)
        }

        if (qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            return api.forQualifier(qualifier)
        }
        return (GormInstanceApi<D>) api
    }

    /**
     * Finds a validation API for the given entity class
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return The validation API
     */
    static <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        GormRegistry registry = GormRegistry.instance
        GormValidationApi api = registry.getValidationApi(className)
        if (api == null) {
            throw stateException(entity)
        }

        if (qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            return api.forQualifier(qualifier)
        }
        return (GormValidationApi<D>) api
    }

    /**
     * Find a datastore for the give entity type and qualifier (the connection name)
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return A datastore
     *
     * @throws IllegalStateException if no datastore is found for the type
     */
    @CompileDynamic
    static Datastore findDatastore(Class entity, String qualifier = null) {
        int depth = RESOLVING_DATASTORE.get()
        if (depth > 5) {
            return GormRegistry.instance.datastoresByQualifier.get(ConnectionSource.DEFAULT)
        }

        String className = entity != null ? NameUtils.getClassName(entity) : null
        GormRegistry registry = GormRegistry.instance

        // PRIORITY 1: Check preferred datastore for this thread
        Datastore preferred = PREFERRED_DATASTORE.get()
        if (preferred != null) {
            if (qualifier != null) {
                if (preferred instanceof org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) {
                    try {
                        Datastore ds = ((org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore)preferred).getDatastoreForConnection(qualifier)
                        if (ds != null) {
                            // System.out.println "RESOLVED DATASTORE: $qualifier (via preferred child)"
                            return ds
                        }
                    } catch (Throwable e) {
                        // ignore
                    }
                }
                if (ConnectionSource.DEFAULT.equals(qualifier)) return preferred
            }
            else {
                // For naked lookups, prefer the preferred datastore if it handles this entity
                if (className == null || preferred.mappingContext.getPersistentEntity(className) != null) {
                    if (preferred instanceof MultiTenantCapableDatastore) {
                        MultiTenantCapableDatastore mtds = (MultiTenantCapableDatastore) preferred
                        try {
                            Serializable tid = CurrentTenantHolder.get()
                            // If tid is null and it is a MultiTenant entity, we MUST enforce context
                            if (tid == null && entity != null && MultiTenant.isAssignableFrom(entity)) {
                                tid = mtds.tenantResolver.resolveTenantIdentifier()
                            }
                            
                            // Respect withoutId
                            if (ConnectionSource.DEFAULT.equals(tid)) {
                                return preferred
                            }
                            
                            if (tid != null && !ConnectionSource.DEFAULT.equals(tid.toString())) {
                                RESOLVING_DATASTORE.set(depth + 1)
                                try {
                                    return findDatastore(entity, tid.toString())
                                } finally {
                                    RESOLVING_DATASTORE.set(depth)
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
        
        // PRIORITY 2: If qualifier is provided, use it from registry
        if (qualifier != null && !ConnectionSource.DEFAULT.equals(qualifier)) {
            Object resource = TransactionSynchronizationManager.getResource(qualifier)
            if (resource instanceof Datastore) {
                return (Datastore)resource
            }
            
            Datastore ds = registry.getDatastore(className, qualifier)
            if (ds != null) return ds
            
            // Fallback for multi-tenancy: Try to get the datastore from the default datastore if it is MultiTenantCapable
            Datastore defaultDs = registry.getDatastore(className, ConnectionSource.DEFAULT)
            if (defaultDs instanceof org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore) {
                try {
                    RESOLVING_DATASTORE.set(depth + 1)
                    ds = ((org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore)defaultDs).getDatastoreForTenantId(qualifier)
                    if (ds != null) return ds
                } catch (Throwable e) {
                    // ignore
                } finally {
                    RESOLVING_DATASTORE.set(depth)
                }
            }
            return defaultDs
        }

        // PRIORITY 3: Check if ANY session is bound to the thread
        for (Datastore registeredDs in registry.allDatastores) {
            if (TransactionSynchronizationManager.hasResource(registeredDs)) {
                if (className != null) {
                    if (registry.getDatastore(className, ConnectionSource.DEFAULT) == registeredDs) {
                        return registeredDs
                    }
                    else if (registeredDs.getMappingContext().getPersistentEntity(className) != null) {
                        return registeredDs
                    }
                }
                else if (registry.allDatastores.size() == 1) {
                    return registeredDs
                }
            }
        }

        // PRIORITY 4: Resolve current context from default datastore
        Datastore defaultDs = registry.getDatastore(className, ConnectionSource.DEFAULT)
        if (defaultDs instanceof MultiTenantCapableDatastore) {
            MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) defaultDs
            try {
                Serializable currentTenantId = CurrentTenantHolder.get()
                if (currentTenantId == null && entity != null && MultiTenant.isAssignableFrom(entity)) {
                    currentTenantId = multiTenantCapableDatastore.tenantResolver.resolveTenantIdentifier()
                }

                if (ConnectionSource.DEFAULT.equals(currentTenantId)) {
                    return defaultDs
                }
                
                if (currentTenantId != null && !ConnectionSource.DEFAULT.equals(currentTenantId.toString())) {
                    RESOLVING_DATASTORE.set(depth + 1)
                    try {
                        return findDatastore(entity, currentTenantId.toString())
                    } finally {
                        RESOLVING_DATASTORE.set(depth)
                    }
                }
            } catch (Throwable e) {
                if (entity != null && MultiTenant.isAssignableFrom(entity) && e instanceof TenantNotFoundException) {
                    throw e
                }
            }
        }

        if (defaultDs == null && entity != null) {
            throw stateException(entity)
        }
        return defaultDs
    }

    /**
     * Finds a datastore by type
     *
     * @param datastoreType The datastore type
     * @return The datastore
     *
     * @throws IllegalStateException If no datastore is found for the type
     */
    static Datastore findDatastoreByType(Class<? extends Datastore> datastoreType) {
        Datastore datastore = GormRegistry.instance.datastoresByType.get(datastoreType)
        if (datastore == null) {
            throw new IllegalStateException("No GORM implementation configured for type [$datastoreType]. Ensure GORM has been initialized correctly")
        }
        return datastore
    }

    /**
     * @return The transaction manager if there is only one
     */
    static PlatformTransactionManager findSingleTransactionManager() {
        def ds = findSingleDatastore()
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore)ds).getTransactionManager()
        }
        return null
    }

    /**
     * @param connectionName The connection name
     * @return The transaction manager
     */
    static PlatformTransactionManager findSingleTransactionManager(String connectionName) {
        def ds = findDatastore(null, connectionName)
        if (ds instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore)ds).getTransactionManager()
        }
        return null
    }

    /**
     * Finds a single datastore
     *
     * @throws IllegalStateException If no datastore is found or more than one is configured
     */
    static Datastore findSingleDatastore() {
        GormRegistry registry = GormRegistry.instance
        
        if (registry.datastoresByQualifier.size() > 1) {
            return findDatastore(null)
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

    /**
     * Find the transaction manager for the given entity
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return The transaction manager
     */
    static PlatformTransactionManager findTransactionManager(Class entity, String qualifier = null) {
        Datastore datastore = findDatastore(entity, qualifier)
        if (datastore instanceof TransactionCapableDatastore) {
            return ((TransactionCapableDatastore)datastore).transactionManager
        }
        return null
    }

    /**
     * Find an entity for the given entity class
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return The entity
     */
    static PersistentEntity findEntity(Class entity, String qualifier = findTenantId(entity)) {
        findDatastore(entity, qualifier).getMappingContext().getPersistentEntity(entity.name)
    }

    private static IllegalStateException stateException(Class entity) {
        new IllegalStateException("No GORM implementation configured for class [${entity.name}]. Ensure GORM has been initialized correctly")
    }

    /**
     * Closes the enhancer clearing any stored static state
     */
    @CompileStatic
    void close() throws IOException {
        removeConstraints()
        if (PREFERRED_DATASTORE.get() == datastore) {
            PREFERRED_DATASTORE.remove()
        }
        registry.removeDatastore(datastore)
        def metaClassRegistry = GroovySystem.metaClassRegistry
        for (entity in datastore.mappingContext.persistentEntities) {
            def cls = entity.javaClass
            def className = cls.name
            registry.removeEntityDatastore(className, datastore)
            
            boolean stillManaged = (registry.getStaticApi(className) != null)
            
            if (!stillManaged) {
                metaClassRegistry.removeMetaClass(cls)
            }
        }
    }

    @CompileDynamic
    protected void removeConstraints() {
        try {
            def cls = Class.forName("org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator", false, GormEnhancer.classLoader)
            if (cls != null) {
                def factory = datastore.mappingContext.mappingFactory
                if (factory.hasProperty('entityContext')) {
                    def constraintsEvaluator = factory.entityContext.getBean(cls)
                    if (constraintsEvaluator != null) {
                        for (entity in datastore.mappingContext.persistentEntities) {
                            constraintsEvaluator.removeConstraints(entity.javaClass)
                        }
                    }
                }
            }
        } catch (Throwable e) {
            log.debug("Not running in Grails environment, cannot de-register constraints. ${e.message}")
        }
    }

    @CompileDynamic
    protected void registerConstraints(Datastore datastore) {
        try {
            def context = datastore.mappingContext
            def factory = context.mappingFactory
            if (factory.hasProperty('entityContext')) {
                def constraintsEvaluator = factory.entityContext.getBean(Class.forName("org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator", false, GormEnhancer.classLoader))
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

    @CompileStatic
    List<FinderMethod> getFinders() {
        if(finders == null) {
            finders = Collections.unmodifiableList(createDynamicFinders(datastore))
        }
        return finders
    }

    List<FinderMethod> finders

    /**
     * Enhance all entities in the datastore
     *
     * @param onlyExtendedMethods Whether to only enhance with extended methods
     */
    @CompileStatic
    void enhance(boolean onlyExtendedMethods = false) {
    }

    /**
     * Enhance a single entity
     *
     * @param e The entity
     * @param onlyExtendedMethods Whether to only enhance with extended methods
     */
    void enhance(PersistentEntity e, boolean onlyExtendedMethods = false) {
        registerEntity(e)
    }

    void addStaticMethods(PersistentEntity entity) {
        addStaticMethods(entity, false)
    }

    @CompileDynamic
    protected void addStaticMethods(PersistentEntity e, boolean onlyExtendedMethods) {
        def cls = e.javaClass
        ExpandoMetaClass mc = MetaClassUtils.getExpandoMetaClass(cls)
        
        mc.static.methodMissing = { String name, args ->
            def api = GormEnhancer.findStaticApi(cls)
            try {
                return api.invokeMethod(name, args)
            } catch (MissingMethodException mme) {
                if (mme.method == name && mme.type == api.class) {
                    return api.methodMissing(name, args)
                }
                throw mme
            }
        }
        mc.static.propertyMissing = { String name ->
            def api = GormEnhancer.findStaticApi(cls)
            try {
                return api.getProperty(name)
            } catch (MissingPropertyException mpe) {
                if (mpe.property == name && mpe.type == api.class) {
                    return api.propertyMissing(name)
                }
                throw mpe
            }
        }
    }

    protected boolean appliesToDatastore(Datastore datastore, PersistentEntity entity) {
        !entity.isExternal()
    }

    @CompileDynamic
    protected void addInstanceMethods(PersistentEntity e, boolean onlyExtendedMethods) {
        Class cls = e.javaClass
        ExpandoMetaClass mc = MetaClassUtils.getExpandoMetaClass(cls)
        
        mc.methodMissing = { String name, args ->
            def api = GormEnhancer.findInstanceApi(cls)
            try {
                return api.invokeMethod(name, args)
            } catch (MissingMethodException mme) {
                if (mme.method == name && mme.type == api.class) {
                    return api.methodMissing(delegate, name, args)
                }
                throw mme
            }
        }
        mc.propertyMissing = { String name ->
            def api = GormEnhancer.findInstanceApi(cls)
            try {
                return api.getProperty(name)
            } catch (MissingPropertyException mpe) {
                if (mpe.property == name && mpe.type == api.class) {
                    return api.propertyMissing(delegate, name)
                }
                throw mpe
            }
        }
        mc.propertyMissing = { String name, val ->
            GormEnhancer.findInstanceApi(cls).setProperty(name, val)
        }
    }

    @CompileStatic
    protected <D> GormStaticApi<D> getStaticApi(Class<D> cls) {
        getStaticApi(cls, getDatastoreResolver(cls), ConnectionSource.DEFAULT)
    }

    @CompileStatic
    protected <D> GormStaticApi<D> getStaticApi(Class<D> cls, DatastoreResolver resolver, String qualifier) {
        new GormStaticApi<D>(cls, datastore.mappingContext, createDynamicFinders(resolver, datastore.mappingContext), resolver, qualifier)
    }

    @CompileStatic
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls) {
        getInstanceApi(cls, getDatastoreResolver(cls))
    }

    @CompileStatic
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, DatastoreResolver resolver) {
        def instanceApi = new GormInstanceApi<D>(cls, datastore.mappingContext, resolver)
        instanceApi.failOnError = failOnError
        instanceApi.markDirty = markDirty
        return instanceApi
    }

    @CompileStatic
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls) {
        getValidationApi(cls, getDatastoreResolver(cls))
    }

    @CompileStatic
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, DatastoreResolver resolver) {
        new GormValidationApi<D>(cls, datastore.mappingContext, resolver)
    }

    protected DatastoreResolver getDatastoreResolver(Class cls) {
        new DatastoreResolver() {
            @Override
            Datastore resolve() {
                GormEnhancer.findDatastore(cls)
            }
        }
    }

    protected DatastoreResolver getDefaultDatastoreResolver() {
        new DatastoreResolver() {
            @Override
            Datastore resolve() {
                GormEnhancer.findDatastore(null)
            }
        }
    }

    protected List<FinderMethod> createDynamicFinders() {
        createDynamicFinders(getDefaultDatastoreResolver(), datastore.mappingContext)
    }

    static List<FinderMethod> createDynamicFinders(Datastore targetDatastore) {
        createDynamicFinders(new DatastoreResolver() {
            @Override
            Datastore resolve() {
                targetDatastore
            }
        }, targetDatastore.getMappingContext())
    }

    static List<FinderMethod> createDynamicFinders(DatastoreResolver datastoreResolver, MappingContext mappingContext) {
        [new FindOrCreateByFinder(datastoreResolver, mappingContext),
         new FindOrSaveByFinder(datastoreResolver, mappingContext),
         new FindByFinder(datastoreResolver, mappingContext),
         new FindAllByFinder(datastoreResolver, mappingContext),
         new FindAllByBooleanFinder(datastoreResolver, mappingContext),
         new FindByBooleanFinder(datastoreResolver, mappingContext),
         new CountByFinder(datastoreResolver, mappingContext),
         new ListOrderByFinder(datastoreResolver, mappingContext)] as List<FinderMethod>
    }
}
