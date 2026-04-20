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

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.reflection.CachedMethod
import org.codehaus.groovy.runtime.metaclass.ClosureStaticMetaMethod
import org.codehaus.groovy.runtime.metaclass.MethodSelectionException

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionSystemException

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants
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
    private static final Map<String, Map<String, Datastore>> DATASTORES = new ConcurrentHashMap<String, Map<String, Datastore>>()

    private static final Map<Class, Datastore> DATASTORES_BY_TYPE = new ConcurrentHashMap<Class, Datastore>()
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
     * @return The thread-local datastore override, or null
     */
    static Datastore getThreadLocalDatastore() {
        THREAD_LOCAL_DATASTORE.get()
    }

    private static Map<String, Datastore> getDatastoreMap(String qualifier) {
        return DATASTORES.computeIfAbsent(qualifier) { new ConcurrentHashMap<String, Datastore>() }
    }

    /**
     * Finds the finders for the given datastore
     * @param datastore The datastore
     * @return The finders
     */
    static List<FinderMethod> findFinders(Datastore datastore) {
        GormEnhancer get = ENHANCERS.get(datastore)
        return get?.finders ?: Collections.<FinderMethod>emptyList()
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
                getDatastoreMap(qualifier).put(name, datastore)
            }
            if (!qualifiers.contains(ConnectionSource.DEFAULT)) {
                getDatastoreMap(ConnectionSource.DEFAULT).put(name, datastore)
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

    protected static String findTenantId(Class entity) {
        if (MultiTenant.isAssignableFrom(entity)) {
            Datastore defaultDatastore = findDatastore(entity, ConnectionSource.DEFAULT)
            if ((defaultDatastore instanceof MultiTenantCapableDatastore)) {

                MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore) defaultDatastore
                MultiTenancySettings.MultiTenancyMode mode = multiTenantCapableDatastore.getMultiTenancyMode()
                if (mode == MultiTenancySettings.MultiTenancyMode.DATABASE) {
                    return Tenants.currentId(multiTenantCapableDatastore).toString()
                }
                else {
                    return ConnectionSource.DEFAULT
                }
            }
            else {
                return ConnectionSource.DEFAULT
            }
        }
        else {
            return ConnectionSource.DEFAULT
        }
    }

    /**
     * Find a static API for the give entity type and qualifier (the connection name)
     */
    static <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        def staticApi = STATIC_APIS.get(className)
        if (staticApi == null) {
            throw stateException(entity)
        }
        return (GormStaticApi<D>) staticApi
    }

    /**
     * Find an instance API for the give entity type and qualifier (the connection name)
     */
    static <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        def instanceApi = INSTANCE_APIS.get(className)
        if (instanceApi == null) {
            throw stateException(entity)
        }
        return (GormInstanceApi<D>) instanceApi
    }

    /**
     * Find a validation API for the give entity type and qualifier (the connection name)
     */
    static <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier = null) {
        String className = NameUtils.getClassName(entity)
        def validationApi = VALIDATION_APIS.get(className)
        if (validationApi == null) {
            throw stateException(entity)
        }
        return (GormValidationApi<D>) validationApi
    }

    static Datastore findDatastore(Class entity, String qualifier = findTenantId(entity)) {
        // 1. Check thread-local override (Highest priority for TCK/Tests)
        Datastore override = getThreadLocalDatastore()
        if (override != null) {
            // Check if the entity is persistent in the override datastore
            if (override.mappingContext.getPersistentEntity(entity.name)) {
                return override
            }
        }

        String className = entity.name
        def datastore = DATASTORES.get(qualifier)?.get(className)
        if (datastore == null && qualifier != ConnectionSource.DEFAULT) {
            datastore = DATASTORES.get(ConnectionSource.DEFAULT)?.get(className)
        }
        if (datastore == null) {
            throw stateException(entity)
        }
        return datastore
    }

    /**
     * Finds a datastore by type
     */
    static Datastore findDatastoreByType(Class<? extends Datastore> datastoreType) {
        Datastore datastore = DATASTORES_BY_TYPE.get(datastoreType)
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
            throw new IllegalStateException('More than one GORM implementation is configured. Specific the datastore type!')
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
        ENHANCERS.remove(datastore)
        if (datastore != null) {
            DATASTORES_BY_TYPE.remove(datastore.getClass())
            for (entity in datastore.mappingContext.persistentEntities) {
                List<String> qualifiers = allQualifiers(datastore, entity)
                def cls = entity.javaClass
                def className = cls.name
                for (q in qualifiers) {
                    NAMED_QUERIES.remove(className)
                    if (DATASTORES.containsKey(q)) {
                        DATASTORES.get(q).remove(className)
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
        new GormStaticApi<D>(cls, null, getFinders(), transactionManager)
    }

    @CompileStatic
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, String qualifier = ConnectionSource.DEFAULT) {
        def instanceApi = new GormInstanceApi<D>(cls, null)
        instanceApi.failOnError = failOnError
        instanceApi.markDirty = markDirty
        return instanceApi
    }

    @CompileStatic
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, String qualifier = ConnectionSource.DEFAULT) {
        new GormValidationApi(cls, null)
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
