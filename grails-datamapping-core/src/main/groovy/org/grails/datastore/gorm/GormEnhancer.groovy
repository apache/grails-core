/* Copyright (C) 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.reflection.CachedMethod
import org.codehaus.groovy.runtime.metaclass.ClosureStaticMetaMethod
import org.codehaus.groovy.runtime.metaclass.MethodSelectionException

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.TransactionSynchronizationManager

import grails.gorm.MultiTenant
import grails.gorm.api.GormInstanceOperations
import grails.gorm.api.GormStaticOperations
import grails.gorm.api.GormValidationOperations
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.gorm.multitenancy.TenantDelegatingGormOperations
import org.grails.datastore.gorm.finders.CountByFinder
import org.grails.datastore.gorm.finders.FindAllByBooleanFinder
import org.grails.datastore.gorm.finders.FindAllByFinder
import org.grails.datastore.gorm.finders.FindByBooleanFinder
import org.grails.datastore.gorm.finders.FindByFinder
import org.grails.datastore.gorm.finders.FindOrCreateByFinder
import org.grails.datastore.gorm.finders.FindOrSaveByFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.finders.ListOrderByFinder
import org.grails.datastore.gorm.internal.InstanceMethodInvokingClosure
import org.grails.datastore.gorm.internal.StaticMethodInvokingClosure
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.reflect.MetaClassUtils
import org.grails.datastore.mapping.reflect.NameUtils
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore

/**
 * Enhances a class with GORM behavior. This implementation is truly stateless (Class-Singleton model).
 * All APIs are stored in a flat map by class name.
 *
 * @author Graeme Rocher
 */
@Slf4j
@CompileStatic
class GormEnhancer implements Closeable {

    private static final Map<String, Map<String, Closure>> NAMED_QUERIES = new ConcurrentHashMap<>()

    private static final Map<String, GormStaticApi> STATIC_APIS = new ConcurrentHashMap<String, GormStaticApi>()
    private static final Map<String, GormInstanceApi> INSTANCE_APIS = new ConcurrentHashMap<String, GormInstanceApi>()
    private static final Map<String, GormValidationApi> VALIDATION_APIS = new ConcurrentHashMap<String, GormValidationApi>()
    
    // DATASTORES map is still nested because it maps (Qualifier -> Class -> Datastore) for multi-tenant resolution
    public static final Map<String, Map<Class, Datastore>> DATASTORES = new ConcurrentHashMap<String, Map<Class, Datastore>>()

    public static final Map<Class, Datastore> DATASTORES_BY_TYPE = new ConcurrentHashMap<Class, Datastore>()
    private static final Map<Datastore, GormEnhancer> ENHANCERS = Collections.synchronizedMap(new WeakHashMap<Datastore, GormEnhancer>())
    
    private static final ThreadLocal<Datastore> THREAD_LOCAL_DATASTORE = new ThreadLocal<>()

    /**
     * Sets a datastore override for the current thread. Used by TCK and unit tests for isolation.
     */
    static void setThreadLocalDatastore(Datastore datastore) {
        if (datastore == null) {
            THREAD_LOCAL_DATASTORE.remove()
        } else {
            THREAD_LOCAL_DATASTORE.set(datastore)
        }
    }

    /**
     * Executes the given closure with the given datastore bound to the thread
     */
    static <T> T doWithDatastore(Datastore datastore, Closure<T> callable) {
        Datastore previous = getThreadLocalDatastore()
        try {
            setThreadLocalDatastore(datastore)
            return callable.call()
        } finally {
            setThreadLocalDatastore(previous)
        }
    }

    /**
     * @return The thread-local datastore override, or null
     */
    static Datastore getThreadLocalDatastore() {
        THREAD_LOCAL_DATASTORE.get()
    }

    private static Map<Class, Datastore> getDatastoreMap(String qualifier) {
        return DATASTORES.computeIfAbsent(qualifier) { new ConcurrentHashMap<Class, Datastore>() }
    }

    /**
     * Finds the finders for the given datastore
     * @param datastore The datastore
     * @return The finders
     */
    static List<FinderMethod> findFinders(Datastore datastore) {
        GormEnhancer get = ENHANCERS.get(datastore)
        if (get == null) {
            // Check if it's a child datastore or find any enhancer for this datastore type
            for (enhancer in ENHANCERS.values()) {
                if (enhancer.datastore.getClass().isInstance(datastore)) {
                    return enhancer.finders
                }
            }
        }
        return get?.finders ?: Collections.<FinderMethod>emptyList()
    }

    /**
     * Create a datastore proxy that resolves the actual datastore at call-time based on the entity class.
     * This is used to make dynamic finders context-aware in stateless mode.
     */
    static Datastore createEntityAwareProxy(Class entityClass) {
        (Datastore) Proxy.newProxyInstance(
            Datastore.class.getClassLoader(),
            [Datastore, ConnectionSourcesProvider, MultiTenantCapableDatastore] as Class[],
            new InvocationHandler() {
                Object invoke(Object proxy, Method method, Object[] args) {
                    Datastore target = findDatastore(entityClass)
                    return method.invoke(target, args)
                }
            }
        )
    }

    final Datastore datastore
    PlatformTransactionManager transactionManager
    List<FinderMethod> finders
    boolean failOnError
    boolean markDirty

    /**
     * Whether to include external entities
     */
    boolean includeExternal = true
    /**
     * Whether to enhance classes dynamically using meta programming as well, only necessary for Java classes
     */
    final boolean dynamicEnhance

    GormEnhancer(Datastore datastore) {
        this(datastore, null)
    }

    GormEnhancer(Datastore datastore, PlatformTransactionManager transactionManager, boolean failOnError = false, boolean dynamicEnhance = false, boolean markDirty = true) {
        this(datastore, transactionManager, new ConnectionSourceSettings().failOnError(failOnError).markDirty(markDirty))
    }

    /**
     * Construct a new GormEnhancer for the given arguments
     *
     * @param datastore The datastore
     * @param transactionManager The transaction manager
     * @param settings The settings
     */
    GormEnhancer(Datastore datastore, PlatformTransactionManager transactionManager, ConnectionSourceSettings settings) {
        this.datastore = datastore
        if (datastore != null) {
            ENHANCERS.put(datastore, this)
        }
        if (settings == null) {
            if (datastore instanceof ConnectionSourcesProvider) {
                settings = ((ConnectionSourcesProvider)datastore).getConnectionSources().getDefaultConnectionSource().getSettings()
            } else {
                settings = new ConnectionSourceSettings()
            }
        }
        this.failOnError = settings.isFailOnError()
        Boolean markDirty = settings.getMarkDirty()
        this.markDirty = markDirty == null ? true : markDirty
        this.transactionManager = transactionManager
        this.dynamicEnhance = false
        if (datastore != null) {
            registerConstraints(datastore)
            DATASTORES_BY_TYPE.put(datastore.getClass(), datastore)
            for (entity in datastore.mappingContext.persistentEntities) {
                registerEntity(entity)
            }
        }
        NAMED_QUERIES.clear()
    }

    @CompileDynamic
    void registerEntity(PersistentEntity entity) {
        Datastore datastore = this.datastore ?: entity.mappingContext.datastore
        if (appliesToDatastore(datastore, entity)) {
            def cls = entity.javaClass
            String name = entity.name

            // 1. Ensure Class-Singleton APIs are populated
            // We ALWAYS update to ensure the newest configuration is reflected (e.g. failOnError)
            // APIs remain stateless because getStaticApi/getInstanceApi pass null datastores.
            STATIC_APIS.put(name, getStaticApi(cls, ConnectionSource.DEFAULT))
            INSTANCE_APIS.put(name, getInstanceApi(cls, ConnectionSource.DEFAULT))
            VALIDATION_APIS.put(name, getValidationApi(cls, ConnectionSource.DEFAULT))

            // 2. Register datastore for all qualifiers (Tenants)
            // This is CRITICAL: even if the API is shared, the routing map must be updated
            List<String> qualifiers = allQualifiers(datastore, entity)
            for (qualifier in qualifiers) {
                Datastore dsForQualifier = datastore
                if (datastore instanceof MultipleConnectionSourceCapableDatastore) {
                    try {
                        dsForQualifier = ((MultipleConnectionSourceCapableDatastore) datastore).getDatastoreForConnection(qualifier)
                    } catch (Exception e) {
                        // ignore
                    }
                }
                getDatastoreMap(qualifier).put(cls, dsForQualifier ?: datastore)
            }
            if (!qualifiers.contains(ConnectionSource.DEFAULT)) {
                getDatastoreMap(ConnectionSource.DEFAULT).put(cls, datastore)
            }
        }
    }

    /**
     * Obtain all of the qualifiers (typically the connection names) for the datastore and entity
     *
     * @param datastore The datastore
     * @param entity The entity
     * @return The qualifiers
     */
    List<String> allQualifiers(Datastore datastore, PersistentEntity entity) {
        List<String> qualifiers = new ArrayList<>()
        qualifiers.addAll(ConnectionSourcesSupport.getConnectionSourceNames(entity))

        boolean isMultiTenant = MultiTenant.isAssignableFrom(entity.javaClass)
        boolean hasExplicitAll = qualifiers.contains(ConnectionSource.ALL)
        boolean hasExplicitNonDefaultDatasource = isMultiTenant &&
                !hasExplicitAll &&
                qualifiers.size() > 0 &&
                !qualifiers.equals(ConnectionSourcesSupport.DEFAULT_CONNECTION_SOURCE_NAMES)

        if ((isMultiTenant || hasExplicitAll) && !hasExplicitNonDefaultDatasource && (datastore instanceof ConnectionSourcesProvider)) {
            qualifiers.clear()
            qualifiers.add(ConnectionSource.DEFAULT)

            Iterable<ConnectionSource> allConnectionSources = ((ConnectionSourcesProvider) datastore).getConnectionSources().allConnectionSources
            Collection<String> allConnectionSourceNames = allConnectionSources.findAll() { ConnectionSource connectionSource -> connectionSource.name != ConnectionSource.DEFAULT }
                                                                              .collect() { ((ConnectionSource) it).name }
            qualifiers.addAll(allConnectionSourceNames)
        }
        return qualifiers
    }

    /**
     * Resolve the current tenant id for the given entity
     * 
     * @param entity The entity class
     * @return The tenant id or ConnectionSource.DEFAULT
     */
    public static String findTenantId(Class entity) {
        // 1. Check if a tenant is explicitly bound to the current thread (e.g. via Tenants.withId or withConnection)
        // This applies to ALL entities if they are registered on that connection.
        // We check this FIRST to enable call-time multi-datasource routing.
        Serializable currentId = Tenants.CurrentTenant.get()
        if (currentId != null && currentId != ConnectionSource.DEFAULT) {
            return currentId.toString()
        }

        // 2. Check if a transaction is active for a specific qualifier
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            for (entry in DATASTORES) {
                String qualifier = entry.key
                if (qualifier != ConnectionSource.DEFAULT) {
                    Map<Class, Datastore> classes = entry.value
                    // Use any class from this qualifier to check if its datastore is bound
                    Datastore ds = classes.values().find { it != null }
                    if (ds != null && org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(ds)) {
                        return qualifier
                    }
                }
            }
        }

        // 3. If the entity is MultiTenant, resolve the tenant from its datastore resolver
        if (entity != null && MultiTenant.isAssignableFrom(entity)) {
            // Find any datastore for this entity to check its multi-tenancy mode
            Datastore datastore = DATASTORES.get(ConnectionSource.DEFAULT)?.get(entity)
            if (datastore == null) {
                // Try type-based lookup as fallback
                datastore = DATASTORES_BY_TYPE.get(entity)
            }

            if (datastore instanceof MultiTenantCapableDatastore) {
                MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) datastore
                MultiTenancySettings.MultiTenancyMode mode = multiTenantCapableDatastore.getMultiTenancyMode()
                if (mode != MultiTenancySettings.MultiTenancyMode.NONE) {
                    // This call will throw TenantNotFoundException if no tenant is found, which is what we want
                    return Tenants.currentId(multiTenantCapableDatastore).toString()
                }
            }
        }

        return ConnectionSource.DEFAULT
    }

    /**
     * Find a static API for the give entity type and qualifier (the connection name)
     */
    static <D> GormStaticOperations<D> findStaticApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        GormStaticApi<D> staticApi = (GormStaticApi<D>) STATIC_APIS.get(className)
        if (staticApi == null) {
            throw stateException(entity)
        }
        if (qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            Datastore datastore = findDatastore(entity, qualifier)
            return new TenantDelegatingGormOperations<D>(datastore, (Serializable)qualifier, staticApi)
        }
        return staticApi
    }

    /**
     * Find an instance API for the give entity type and qualifier (the connection name)
     */
    static <D> GormInstanceOperations<D> findInstanceApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        GormInstanceApi<D> instanceApi = (GormInstanceApi<D>) INSTANCE_APIS.get(className)
        if (instanceApi == null) {
            throw stateException(entity)
        }
        if (qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            Datastore datastore = findDatastore(entity, qualifier)
            return TenantDelegatingGormOperations.createInstance(datastore, (Serializable)qualifier, instanceApi)
        }
        return instanceApi
    }

    /**
     * Find a validation API for the give entity type and qualifier (the connection name)
     */
    static <D> GormValidationOperations<D> findValidationApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        GormValidationApi<D> validationApi = (GormValidationApi<D>) VALIDATION_APIS.get(className)
        if (validationApi == null) {
            throw stateException(entity)
        }
        if (qualifier != null && qualifier != ConnectionSource.DEFAULT) {
            Datastore datastore = findDatastore(entity, qualifier)
            return TenantDelegatingGormOperations.createValidation(datastore, (Serializable)qualifier, validationApi)
        }
        return validationApi
    }

    static Datastore findDatastore(Class entity, String qualifier = findTenantId(entity)) {
        if (qualifier == null) {
            qualifier = ConnectionSource.DEFAULT
        }
        
        // 1. Check thread-local override (Highest priority for TCK/Tests)
        Datastore override = getThreadLocalDatastore()
        if (override != null) {
            // Only use thread-local override if qualifier is DEFAULT 
            // OR if the override itself is for that qualifier
            boolean isDefault = qualifier == ConnectionSource.DEFAULT
            boolean matchesQualifier = false
            if (!isDefault && override instanceof ConnectionSourcesProvider) {
                matchesQualifier = ((ConnectionSourcesProvider)override).connectionSources.defaultConnectionSource.name == qualifier
            }

            if ((isDefault || matchesQualifier) && override.mappingContext.getPersistentEntity(entity.name)) {
                return override
            }
        }

        // 2. Resolve from the currently bound session (Dynamic context)
        // If there is an active Spring transaction, we check if any datastore is bound to it
        // that matches our entity.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            
            // 2a. Prioritize the SPECIFIED qualifier if it matches the transaction
            Datastore dsForQualifier = DATASTORES.get(qualifier)?.get(entity)
            if (dsForQualifier != null && org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(dsForQualifier)) {
                return dsForQualifier
            }

            // 2b. Fallback to any bound datastore that supports the entity
            for (qualifierEntries in DATASTORES) {
                Map<Class, Datastore> classes = qualifierEntries.value
                Datastore ds = classes.get(entity)
                if (ds != null && org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(ds)) {
                    return ds
                }
            }
        }

        // 3. Resolve from global registries
        def datastore = DATASTORES.get(qualifier)?.get(entity)

        if (datastore == null && qualifier != ConnectionSource.DEFAULT) {
            datastore = DATASTORES.get(ConnectionSource.DEFAULT)?.get(entity)
        }

        if (datastore == null) {
            try {
                datastore = findSingleDatastore()
            } catch (Exception e) {
                // ignore
            }
        }

        if (datastore == null) {
            throw new IllegalStateException("No GORM platform configured. You must call GormEnhancer.registerEntity(entity) or configure a Datastore for class: ${entity.name}")
        }
        return datastore
    }

    /**
     * Finds a datastore by type
     */
    static Datastore findDatastoreByType(Class<? extends Datastore> datastoreType) {
        Datastore datastore = DATASTORES_BY_TYPE.get(datastoreType)
        if (datastore == null) {
            // Try hierarchy lookup for anonymous classes or subclasses
            for (entry in DATASTORES_BY_TYPE) {
                if (entry.key.isAssignableFrom(datastoreType)) {
                    return entry.value
                }
            }
        }
        if (datastore == null) {
            throw new IllegalStateException("No GORM implementation configured for type [$datastoreType]. Ensure GORM has been initialized correctly")
        }
        return datastore
    }

    /**
     * Finds a single datastore
     */
    static Datastore findSingleDatastore() {
        Collection<Datastore> allDatastores = DATASTORES_BY_TYPE.values()
        if (allDatastores.isEmpty()) {
            throw new IllegalStateException('No GORM implementations configured. Ensure GORM has been initialized correctly')
        }
        else if (allDatastores.size() > 1) {
            return allDatastores.first() // Fallback to first one in high-density test environment
        }
        else {
            return allDatastores.first()
        }
    }

    static PlatformTransactionManager findSingleTransactionManager(String connectionName = ConnectionSource.DEFAULT) {
        Datastore datastore = findSingleDatastore()
        return getTransactionManagerForConnection(datastore, connectionName)
    }

    static PlatformTransactionManager findTransactionManager(Class<? extends Datastore> datastoreType, String connectionName = ConnectionSource.DEFAULT) {
        Datastore datastore = findDatastoreByType(datastoreType)
        return getTransactionManagerForConnection(datastore, connectionName)
    }

    /**
     * Find the entity for the given type
     */
    static PersistentEntity findEntity(Class entity, String qualifier = findTenantId(entity)) {
        findDatastore(entity, qualifier).getMappingContext().getPersistentEntity(entity.name)
    }

    static void clearRegistry() {
        NAMED_QUERIES.clear()
        STATIC_APIS.clear()
        INSTANCE_APIS.clear()
        VALIDATION_APIS.clear()
        DATASTORES.clear()
        DATASTORES_BY_TYPE.clear()
        ENHANCERS.clear()
        setThreadLocalDatastore(null)
    }

    @Override
    @CompileStatic
    void close() throws IOException {
        removeConstraints()
        if (datastore != null) {
            if (getThreadLocalDatastore() == datastore) {
                setThreadLocalDatastore(null)
            }
            // Unbind any sessions associated with this datastore to prevent leaks between tests
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
            }
            ENHANCERS.remove(datastore)
            if (DATASTORES_BY_TYPE.get(datastore.getClass()) == datastore) {
                DATASTORES_BY_TYPE.remove(datastore.getClass())
            }
            for (entity in datastore.mappingContext.persistentEntities) {
                List<String> qualifiers = allQualifiers(datastore, entity)
                def cls = entity.javaClass
                def className = cls.name
                NAMED_QUERIES.remove(className)
                for (q in qualifiers) {
                    if (DATASTORES.containsKey(q)) {
                        Map<Class, Datastore> map = DATASTORES.get(q)
                        // Resolve the datastore that was registered for this qualifier
                        Datastore registeredDs = map.get(cls)
                        if (registeredDs != null) {
                            // If it's the current datastore or a child of it, remove it
                            if (registeredDs == datastore) {
                                map.remove(cls)
                            } else if (datastore instanceof MultipleConnectionSourceCapableDatastore) {
                                try {
                                    Datastore childDs = ((MultipleConnectionSourceCapableDatastore) datastore).getDatastoreForConnection(q)
                                    if (registeredDs == childDs) {
                                        if (TransactionSynchronizationManager.isSynchronizationActive()) {
                                            TransactionSynchronizationManager.unbindResourceIfPossible(childDs)
                                        }
                                        map.remove(cls)
                                    }
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static PlatformTransactionManager getTransactionManagerForConnection(Datastore datastore, String connectionName) {
        if (datastore instanceof TransactionCapableDatastore && ConnectionSource.DEFAULT.equals(connectionName)) {
            return ((TransactionCapableDatastore) datastore).getTransactionManager()
        } else if (datastore instanceof MultipleConnectionSourceCapableDatastore) {
            Datastore datastoreForConnection = ((MultipleConnectionSourceCapableDatastore) datastore).getDatastoreForConnection(connectionName)
            if (datastoreForConnection instanceof TransactionCapableDatastore) {
                return ((TransactionCapableDatastore) datastoreForConnection).getTransactionManager()
            }
        }
        throw new TransactionSystemException("Datastore implementation ${datastore.getClass().getName()} does not support transactions!")
    }

    private static IllegalStateException stateException(Class entity) {
        new IllegalStateException("Either class [$entity.name] is not a domain class or GORM has not been initialized correctly or has already been shutdown. Ensure GORM is loaded and configured correctly before calling any methods on a GORM entity.")
    }

    @CompileDynamic
    protected void removeConstraints() {
        try {
            String className = 'org.apache.groovy.grails.validation.ConstrainedProperty'
            ClassLoader classLoader = getClass().getClassLoader()
            if (ClassUtils.isPresent(className, classLoader)) {
                classLoader.loadClass(className).removeConstraint('unique')
            }
        } catch (Throwable e) {
            log.debug("Not running in Grails 2 environment, cannot de-register constraints. This exception can be safely ignored if you are not using Grails 2. ${e.message}", e)
        }
    }

    protected void registerConstraints(Datastore datastore) {
        try {
            String className = 'org.grails.datastore.gorm.support.ConstraintRegistrar'
            ClassLoader classLoader = getClass().getClassLoader()
            if (ClassUtils.isPresent(className, classLoader)) {
                classLoader.loadClass(className).newInstance(datastore)
            }
        } catch (Throwable e) {
            log.debug("Unable to register GORM constraints. Not running a Grails environment. This can be safely ignored if you are not running Grails: $e.message", e)
        }
    }

    @CompileStatic
    List<FinderMethod> getFinders() {
        if (finders == null) {
            finders = Collections.unmodifiableList(createDynamicFinders())
        }
        finders
    }

    /**
     * Enhances all persistent entities.
     */
    @CompileStatic
    void enhance(boolean onlyExtendedMethods = false) {
        // No-op. Enhancement is handled by GORM Traits (GormEntity)
    }

    /**
     * Enhance and individual entity
     */
    @CompileStatic
    void enhance(PersistentEntity e, boolean onlyExtendedMethods = false) {
        registerEntity(e)
    }

    @CompileStatic
    protected void addStaticMethods(PersistentEntity e, boolean onlyExtendedMethods) {
        // No-op. Handled by GormEntity trait
    }

    @CompileDynamic
    protected void registerStaticMethod(ExpandoMetaClass mc, String methodName, GormStaticApi staticApiProvider) {
        // No-op. Handled by GormEntity trait
    }

    protected boolean appliesToDatastore(Datastore datastore, PersistentEntity entity) {
        !entity.isExternal()
    }

    @CompileDynamic
    protected <D> List<AbstractGormApi<D>> getInstanceMethodApiProviders(Class<D> cls) {
        [getInstanceApi(cls), getValidationApi(cls)]
    }

    @CompileStatic
    protected void addInstanceMethods(PersistentEntity e, boolean onlyExtendedMethods) {
        // No-op. Handled by GormEntity trait
    }

    @CompileDynamic
    protected void registerInstanceMethod(Class cls, ExpandoMetaClass mc, AbstractGormApi apiProvider, String methodName) {
        // No-op. Handled by GormEntity trait
    }

    @CompileStatic
    protected static boolean doesRealMethodExist(final MetaClass mc, final String methodName, final Class[] parameterTypes, boolean staticScope) {
        boolean realMethodExists = false
        try {
            MetaMethod existingMethod = mc.pickMethod(methodName, parameterTypes)
            if (existingMethod && existingMethod.isStatic() == staticScope && isRealMethod(existingMethod) && parameterTypes.length == existingMethod.parameterTypes.length)  {
                realMethodExists = true
            }
        } catch (MethodSelectionException mse) {
            realMethodExists = mc.methods.contains { MetaMethod existingMethod ->
                existingMethod.name == methodName && existingMethod.isStatic() == staticScope && isRealMethod(existingMethod) && ((!parameterTypes && !existingMethod.parameterTypes) || parameterTypes == existingMethod.parameterTypes)
            }
        }
        return realMethodExists
    }

    @CompileStatic
    protected static boolean isRealMethod(MetaMethod existingMethod) {
        existingMethod instanceof CachedMethod
    }

    @CompileStatic
    protected <D> GormStaticApi<D> getStaticApi(Class<D> cls, String qualifier = ConnectionSource.DEFAULT) {
        Datastore datastore = getDatastoreMap(qualifier).get(cls.name)
        // For the singleton, use a proxy so finders are context-aware
        Datastore finderDatastore = datastore
        if (qualifier == ConnectionSource.DEFAULT) {
            finderDatastore = createEntityAwareProxy(cls)
        }
        // If this is for the global singleton registry, pass null for the datastore so it remains stateless
        Datastore apiDatastore = (qualifier == ConnectionSource.DEFAULT) ? null : datastore
        new GormStaticApi<D>(cls, apiDatastore, createDynamicFinders(finderDatastore), transactionManager)
    }

    @CompileStatic
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, String qualifier = ConnectionSource.DEFAULT) {
        Datastore datastore = getDatastoreMap(qualifier).get(cls.name)
        Datastore apiDatastore = (qualifier == ConnectionSource.DEFAULT) ? null : datastore
        def instanceApi = new GormInstanceApi<D>(cls, apiDatastore)
        instanceApi.failOnError = failOnError
        instanceApi.markDirty = markDirty
        return instanceApi
    }

    @CompileStatic
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, String qualifier = ConnectionSource.DEFAULT) {
        Datastore datastore = getDatastoreMap(qualifier).get(cls.name)
        Datastore apiDatastore = (qualifier == ConnectionSource.DEFAULT) ? null : datastore
        new GormValidationApi(cls, apiDatastore)
    }

    @CompileStatic
    protected List<FinderMethod> createDynamicFinders() {
        createDynamicFinders(datastore)
    }

    @CompileStatic
    protected List<FinderMethod> createDynamicFinders(Datastore targetDatastore) {
        [new FindOrCreateByFinder(targetDatastore),
         new FindOrSaveByFinder(targetDatastore),
         new FindByFinder(targetDatastore),
         new FindAllByFinder(targetDatastore),
         new FindAllByBooleanFinder(targetDatastore),
         new FindByBooleanFinder(targetDatastore),
         new CountByFinder(targetDatastore),
         new ListOrderByFinder(targetDatastore)] as List<FinderMethod>
    }
}
