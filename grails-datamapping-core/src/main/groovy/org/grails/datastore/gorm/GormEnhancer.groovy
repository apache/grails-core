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
import org.grails.datastore.gorm.finders.FinderMethod
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
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.reflect.MetaClassUtils
import org.grails.datastore.mapping.reflect.NameUtils
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

    private static final Logger log = LoggerFactory.getLogger(GormEnhancer)
    private static final GormEnhancerRegistry STATE_REGISTRY = GormEnhancerRegistry.getInstance()

    private final GormRegistry registry
    private final List<String> connectionSourceNames
    final Datastore datastore
    PlatformTransactionManager transactionManager
    boolean failOnError = false
    boolean markDirty = true

    /**
     * Whether to include external entities
     */
    boolean includeExternal = true



    /**
     * Construct a new GormEnhancer for the given arguments.
     *
     * @param datastore The datastore (required)
     * @param transactionManager The transaction manager (required)
     * @param settings The connection source settings (required)
     * @param registry The GORM registry (optional, defaults to singleton instance)
     */
    GormEnhancer(Datastore datastore, 
                 PlatformTransactionManager transactionManager, 
                 ConnectionSourceSettings settings,
                 GormRegistry registry = GormRegistry.getInstance()) {
        assert datastore != null, 'Datastore is required'
        assert transactionManager != null, 'PlatformTransactionManager is required'
        assert settings != null, 'ConnectionSourceSettings is required'
        
        this.datastore = datastore
        this.registry = registry
        
        this.failOnError = settings.isFailOnError()
        Boolean markDirty = settings.getMarkDirty()
        this.markDirty = markDirty == null ? true : markDirty
        this.transactionManager = transactionManager

        this.connectionSourceNames = ConnectionSourceNameResolver.resolveConnectionSourceNames(datastore)

        String qualifier = ConnectionSourceNameResolver.resolveDefaultConnectionSourceName(datastore)
        registry.initializeDatastore(datastore, qualifier)

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
        if (!entity.isExternal()) {
            // Delegate entity registration orchestration to the registry
            registry.registerEntity(entity, this)
            
            // Add dynamic methods to the class
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
        STATE_REGISTRY.setPreferredDatastore(datastore)
    }

    /**
     * @return The preferred datastore for the current thread
     */
    static Datastore getPreferredDatastore() {
        return STATE_REGISTRY.getPreferredDatastore()
    }

    static void clearPreferredDatastore() {
        STATE_REGISTRY.clearPreferredDatastore()
    }

    /**
     * Find the tenant id for the given entity
     *
     * @param entity
     * @return
     */
    protected static String findTenantId(Class entity) {
        findTenantId(entity, getRegistry())
    }

    protected static String findTenantId(Class entity, GormRegistry registry) {
        if (MultiTenant.isAssignableFrom(entity)) {
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

    /**
     * Find a static API for the give entity type and qualifier (the connection name)
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return A static API
     *
     * @throws IllegalStateException if no static API is found for the type
     */
    /**
     * @deprecated Use {@code GormRegistry.getInstance().findStaticApi(entity, qualifier)}.
     */
    @Deprecated
    static <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier = null) {
        getRegistry().apiResolver.findStaticApi(entity, qualifier)
    }

    static <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier, GormRegistry registry) {
        String className = NameUtils.getClassName(entity)
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
    /**
     * @deprecated Use {@code GormRegistry.getInstance().findInstanceApi(entity, qualifier)}.
     */
    @Deprecated
    static <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier = null) {
        getRegistry().apiResolver.findInstanceApi(entity, qualifier)
    }

    static <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier, GormRegistry registry) {
        String className = NameUtils.getClassName(entity)
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
    /**
     * @deprecated Use {@code GormRegistry.getInstance().findValidationApi(entity, qualifier)}.
     */
    @Deprecated
    static <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier = null) {
        getRegistry().apiResolver.findValidationApi(entity, qualifier)
    }

    static <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier, GormRegistry registry) {
        String className = NameUtils.getClassName(entity)
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
    /**
     * @deprecated Use {@code GormRegistry.getInstance().getApiResolver().findDatastore(entity, qualifier)}.
     */
    @Deprecated
    static Datastore findDatastore(Class entity, String qualifier = null) {
        getRegistry().apiResolver.findDatastore(entity, qualifier)
    }

    @CompileDynamic
    static Datastore findDatastore(Class entity, String qualifier, GormRegistry registry) {
        int depth = STATE_REGISTRY.getResolvingDatastoreDepth()
        if (depth > 5) {
            return registry.datastoresByQualifier.get(ConnectionSource.DEFAULT)
        }

        String className = entity != null ? NameUtils.getClassName(entity) : null

        // PRIORITY 1: Check preferred datastore for this thread
        Datastore preferred = STATE_REGISTRY.getPreferredDatastore()
        if (preferred != null) {
            if (qualifier != null) {
                if (preferred instanceof org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) {
                    try {
                        Datastore ds = ((org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore)preferred).getDatastoreForConnection(qualifier)
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
                                STATE_REGISTRY.setResolvingDatastoreDepth(depth + 1)
                                try {
                                    return findDatastore(entity, tid.toString(), registry)
                                } finally {
                                    STATE_REGISTRY.setResolvingDatastoreDepth(depth)
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
            
            // Fallback: Try to get the datastore from the default datastore via connection name or tenant ID
            Datastore defaultDs = registry.getDatastore(className, ConnectionSource.DEFAULT)
            // First try multi-datasource lookup (getDatastoreForConnection handles runtime-added sources)
            if (defaultDs instanceof org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore) {
                try {
                    STATE_REGISTRY.setResolvingDatastoreDepth(depth + 1)
                    ds = ((org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore)defaultDs).getDatastoreForConnection(qualifier)
                    if (ds != null && ds != defaultDs) return ds
                } catch (Throwable e) {
                    // ignore — connection name not found, fall through to tenant lookup
                } finally {
                    STATE_REGISTRY.setResolvingDatastoreDepth(depth)
                }
            }
            if (defaultDs instanceof org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore) {
                try {
                    STATE_REGISTRY.setResolvingDatastoreDepth(depth + 1)
                    ds = ((org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore)defaultDs).getDatastoreForTenantId(qualifier)
                    if (ds != null && ds != defaultDs) return ds
                } catch (Throwable e) {
                    // ignore
                } finally {
                    STATE_REGISTRY.setResolvingDatastoreDepth(depth)
                }
            }
            return defaultDs
        }

        // PRIORITY 3: Check if ANY session is bound to the thread
        for (Datastore registeredDs in registry.allDatastores) {
            if (TransactionSynchronizationManager.hasResource(registeredDs) || registeredDs.hasCurrentSession()) {
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
            // Only DATABASE mode requires a tenant ID to select a different datastore.
            // DISCRIMINATOR and SCHEMA modes share the same connection and should use defaultDs.
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
                    STATE_REGISTRY.setResolvingDatastoreDepth(depth + 1)
                    try {
                        return findDatastore(entity, currentTenantId.toString(), registry)
                    } finally {
                        STATE_REGISTRY.setResolvingDatastoreDepth(depth)
                    }
                }
            } catch (Throwable e) {
                if (entity != null && MultiTenant.isAssignableFrom(entity) && e instanceof TenantNotFoundException) {
                    if (isDatabaseMode || multiTenantCapableDatastore.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
                        throw e
                    }
                    // For DISCRIMINATOR mode, we swallow it and return defaultDs so shared DB operations can proceed.
                    // Entity operations (save, query) will still throw via event listeners or query builders.
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

    /**
     * Finds a datastore by type
     *
     * @param datastoreType The datastore type
     * @return The datastore
     *
     * @throws IllegalStateException If no datastore is found for the type
     */
    /**
     * @deprecated Use {@code GormRegistry.getInstance().getApiResolver().findDatastoreByType(datastoreType)}.
     */
    @Deprecated
    static Datastore findDatastoreByType(Class<? extends Datastore> datastoreType) {
        return getRegistry().apiResolver.findDatastoreByType(datastoreType)
    }

    static Datastore findDatastoreByType(Class<? extends Datastore> datastoreType, GormRegistry registry) {
        Datastore datastore = registry.datastoresByType.get(datastoreType)
        if (datastore == null) {
            throw new IllegalStateException("No GORM implementation configured for type [$datastoreType]. Ensure GORM has been initialized correctly")
        }
        return datastore
    }

    /**
     * @return The transaction manager if there is only one
     */
    /**
     * @deprecated Use {@code GormRegistry.getInstance().getApiResolver().findSingleTransactionManager()}.
     */
    @Deprecated
    static PlatformTransactionManager findSingleTransactionManager() {
        return getRegistry().apiResolver.findSingleTransactionManager()
    }

    /**
     * @param connectionName The connection name
     * @return The transaction manager
     */
    /**
     * @deprecated Use {@code GormRegistry.getInstance().getApiResolver().findSingleTransactionManager(connectionName)}.
     */
    @Deprecated
    static PlatformTransactionManager findSingleTransactionManager(String connectionName) {
        return getRegistry().apiResolver.findSingleTransactionManager(connectionName)
    }

    /**
     * Finds a single datastore
     *
     * @throws IllegalStateException If no datastore is found or more than one is configured
     */
    /**
     * @deprecated Use {@code GormRegistry.getInstance().getApiResolver().findSingleDatastore()}.
     */
    @Deprecated
    static Datastore findSingleDatastore() {
        return getRegistry().apiResolver.findSingleDatastore()
    }

    static Datastore findSingleDatastore(GormRegistry registry) {
        
        if (registry.datastoresByQualifier.size() > 1) {
            return findDatastore(null, null, registry)
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
    /**
     * @deprecated Use {@code GormRegistry.getInstance().getApiResolver().findTransactionManager(entity, qualifier)}.
     */
    @Deprecated
    static PlatformTransactionManager findTransactionManager(Class entity, String qualifier = null) {
        return getRegistry().apiResolver.findTransactionManager(entity, qualifier)
    }

    /**
     * Find an entity for the given entity class
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return The entity
     */
    /**
     * @deprecated Use {@code GormRegistry.getInstance().getApiResolver().findEntity(entity, qualifier)}.
     */
    @Deprecated
    static PersistentEntity findEntity(Class entity, String qualifier = findTenantId(entity)) {
        return getRegistry().apiResolver.findEntity(entity, qualifier)
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
        if (STATE_REGISTRY.getPreferredDatastore() == datastore) {
            STATE_REGISTRY.clearPreferredDatastore()
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
            def api = registry.apiResolver.findStaticApi(cls, null)
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
            def api = registry.apiResolver.findStaticApi(cls, null)
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



    @CompileDynamic
    protected void addInstanceMethods(PersistentEntity e, boolean onlyExtendedMethods) {
        Class cls = e.javaClass
        ExpandoMetaClass mc = MetaClassUtils.getExpandoMetaClass(cls)
        
        mc.methodMissing = { String name, args ->
            def api = registry.apiResolver.findInstanceApi(cls, null)
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
            def api = registry.apiResolver.findInstanceApi(cls, null)
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
            registry.apiResolver.findInstanceApi(cls, null).setProperty(name, val)
        }
    }

    @CompileStatic
    protected <D> GormStaticApi<D> getStaticApi(Class<D> cls) {
        getStaticApi(cls, getDatastoreResolver(cls), ConnectionSource.DEFAULT)
    }

    @CompileStatic
    protected <D> GormStaticApi<D> getStaticApi(Class<D> cls, DatastoreResolver resolver, String qualifier) {
        GormApiFactory apiFactory = registry.getApiFactory(datastore)
        return apiFactory.createStaticApi(cls, datastore.mappingContext, resolver, qualifier, registry)
    }

    @CompileStatic
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls) {
        getInstanceApi(cls, getDatastoreResolver(cls))
    }

    @CompileStatic
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, DatastoreResolver resolver) {
        GormApiFactory apiFactory = registry.getApiFactory(datastore)
        return apiFactory.createInstanceApi(cls, datastore.mappingContext, resolver, registry, failOnError, markDirty)
    }

    @CompileStatic
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls) {
        getValidationApi(cls, getDatastoreResolver(cls))
    }

    @CompileStatic
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, DatastoreResolver resolver) {
        GormApiFactory apiFactory = registry.getApiFactory(datastore)
        return apiFactory.createValidationApi(cls, datastore.mappingContext, resolver, registry)
    }

    protected DatastoreResolver getDatastoreResolver(Class cls) {
        new DatastoreResolver() {
            @Override
            Datastore resolve() {
                registry.apiResolver.findDatastore(cls, null)
            }
        }
    }

    protected DatastoreResolver getDefaultDatastoreResolver() {
        new DatastoreResolver() {
            @Override
            Datastore resolve() {
                registry.apiResolver.findDatastore(null, null)
            }
        }
    }

    protected List<FinderMethod> createDynamicFinders() {
        createDynamicFinders(getDefaultDatastoreResolver(), datastore.mappingContext, registry)
    }

    static List<FinderMethod> createDynamicFinders(Datastore targetDatastore) {
        createDynamicFinders(new DatastoreResolver() {
            @Override
            Datastore resolve() {
                targetDatastore
            }
        }, targetDatastore.getMappingContext(), getRegistry())
    }

    static List<FinderMethod> createDynamicFinders(DatastoreResolver datastoreResolver, MappingContext mappingContext) {
        createDynamicFinders(datastoreResolver, mappingContext, getRegistry())
    }

    static List<FinderMethod> createDynamicFinders(DatastoreResolver datastoreResolver, MappingContext mappingContext, GormRegistry registry) {
        return registry.defaultApiFactory.createDynamicFinders(datastoreResolver, mappingContext)
    }
}
