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

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.reflection.CachedMethod
import org.codehaus.groovy.runtime.metaclass.ClosureStaticMetaMethod
import org.codehaus.groovy.runtime.metaclass.MethodSelectionException
import org.grails.datastore.gorm.finders.*
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionSystemException

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhances a class with GORM behavior
 *
 * @author Graeme Rocher
 */
@Slf4j
@CompileStatic
class GormEnhancer implements Closeable {

    private static final Map<String, Map<String, Closure>> NAMED_QUERIES = new ConcurrentHashMap<>()

    private static final Map<String, Map<String, GormStaticApi>> STATIC_APIS = new ConcurrentHashMap<String, Map<String, GormStaticApi>>().withDefault { String key ->
        return new ConcurrentHashMap() as Map<String, GormStaticApi>
    }
    private static final Map<String, Map<String, GormInstanceApi>> INSTANCE_APIS = new ConcurrentHashMap<String, Map<String, GormInstanceApi>>().withDefault { String key ->
        return new ConcurrentHashMap() as Map<String, GormInstanceApi>
    }
    private static final Map<String, Map<String, GormValidationApi>> VALIDATION_APIS = new ConcurrentHashMap<String, Map<String, GormValidationApi>>().withDefault { String key ->
        return new ConcurrentHashMap() as Map<String, GormValidationApi>
    }
    private static final Map<String, Map<String, Datastore>> DATASTORES = new ConcurrentHashMap<String, Map<String, Datastore>>().withDefault { String key ->
        return new ConcurrentHashMap() as Map<String, Datastore>
    }

    private static final Map<Class, Datastore> DATASTORES_BY_TYPE = new ConcurrentHashMap<Class, Datastore>()

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
        this.failOnError = settings.isFailOnError()
        Boolean markDirty = settings.getMarkDirty()
        this.markDirty = markDirty == null ? true : markDirty
        this.transactionManager = transactionManager
        this.dynamicEnhance = false
        if(datastore != null) {
            registerConstraints(datastore)
        }
        NAMED_QUERIES.clear()
        DATASTORES_BY_TYPE.put(datastore.getClass(), datastore)

        for(entity in datastore.mappingContext.persistentEntities) {
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
            def cls = entity.javaClass

            List<String> qualifiers = allQualifiers(this.datastore, entity)
            if(!qualifiers.contains(ConnectionSource.DEFAULT)) {
                def firstQualifier = qualifiers.first()
                def staticApi = getStaticApi(cls, firstQualifier)
                def name = entity.name
                STATIC_APIS.get(ConnectionSource.DEFAULT).put(name, staticApi)
                def instanceApi = getInstanceApi(cls, firstQualifier)
                INSTANCE_APIS.get(ConnectionSource.DEFAULT).put(name, instanceApi)
                def validationApi = getValidationApi(cls, firstQualifier)
                VALIDATION_APIS.get(ConnectionSource.DEFAULT).put(name, validationApi)
                DATASTORES.get(ConnectionSource.DEFAULT).put(name, this.datastore)

            }
            for (qualifier in qualifiers) {
                def staticApi = getStaticApi(cls, qualifier)
                def name = entity.name
                STATIC_APIS.get(qualifier).put(name, staticApi)
                def instanceApi = getInstanceApi(cls, qualifier)
                INSTANCE_APIS.get(qualifier).put(name, instanceApi)
                def validationApi = getValidationApi(cls, qualifier)
                VALIDATION_APIS.get(qualifier).put(name, validationApi)
                DATASTORES.get(qualifier).put(name, this.datastore)
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
        qualifiers.addAll ConnectionSourcesSupport.getConnectionSourceNames(entity)
        if((MultiTenant.isAssignableFrom(entity.javaClass) || qualifiers.contains(ConnectionSource.ALL)) && (datastore instanceof ConnectionSourcesProvider)) {
            qualifiers.clear()
            qualifiers.add(ConnectionSource.DEFAULT)

            Iterable<ConnectionSource> allConnectionSources = ((ConnectionSourcesProvider) datastore).getConnectionSources().allConnectionSources
            Collection<String> allConnectionSourceNames = allConnectionSources.findAll() { ConnectionSource connectionSource -> connectionSource.name != ConnectionSource.DEFAULT }
                                                                              .collect() { ((ConnectionSource)it).name }
            qualifiers.addAll allConnectionSourceNames
        }
        return qualifiers
    }


    /**
     * Find the tenant id for the given entity
     *
     * @param entity
     * @return
     */
    protected static String findTenantId(Class entity) {
        if(MultiTenant.isAssignableFrom(entity)) {
            Datastore defaultDatastore = findDatastore(entity, ConnectionSource.DEFAULT)
            if((defaultDatastore instanceof MultiTenantCapableDatastore)) {

                MultiTenantCapableDatastore multiTenantCapableDatastore = (MultiTenantCapableDatastore)defaultDatastore
                if(multiTenantCapableDatastore.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DATABASE) {
                    return Tenants.currentId( multiTenantCapableDatastore )
                }
                else {
                    return ConnectionSource.DEFAULT
                }
            }
            else {
                log.debug("Return default tenant id for non-multitenant capable datastore")
                return ConnectionSource.DEFAULT
            }
        }
        else {
            log.debug "Returning default tenant id for non-multitenant class [$entity]"
            return ConnectionSource.DEFAULT
        }
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
    static <D> GormStaticApi<D> findStaticApi(Class<D> entity, String qualifier = findTenantId(entity)) {
        String className = NameUtils.getClassName(entity)
        def staticApi = STATIC_APIS.get(qualifier)?.get(className)
        if(staticApi == null) {
            throw stateException(entity)
        }
        return staticApi
    }

    /**
     * Find an instance API for the give entity type and qualifier (the connection name)
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return An instance API
     *
     * @throws IllegalStateException if no instance API is found for the type
     */
    static <D> GormInstanceApi<D> findInstanceApi(Class<D> entity, String qualifier = findTenantId(entity)) {
        def instanceApi = INSTANCE_APIS.get(qualifier)?.get(NameUtils.getClassName(entity))
        if(instanceApi == null) {
            throw stateException(entity)
        }
        return instanceApi
    }

    /**
     * Find a validation API for the give entity type and qualifier (the connection name)
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return A validation API
     *
     * @throws IllegalStateException if no validation API is found for the type
     */
    static <D> GormValidationApi<D> findValidationApi(Class<D> entity, String qualifier = findTenantId(entity)) {
        def instanceApi = VALIDATION_APIS.get(qualifier)?.get(NameUtils.getClassName(entity))
        if(instanceApi == null) {
            throw stateException(entity)
        }
        return instanceApi
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
    static Datastore findDatastore(Class entity, String qualifier = findTenantId(entity)) {
        def datastore = DATASTORES.get(qualifier)?.get(entity.name)
        if(datastore == null) {
            throw stateException(entity)
        }
        return datastore
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
        Datastore datastore = DATASTORES_BY_TYPE.get(datastoreType)
        if(datastore == null) {
            throw new IllegalStateException("No GORM implementation configured for type [$datastoreType]. Ensure GORM has been initialized correctly")
        }
        return datastore
    }

    /**
     * Finds a single datastore
     *
     * @throws IllegalStateException If no datastore is found or more than one is configured
     */
    static Datastore findSingleDatastore() {
        Collection<Datastore> allDatastores = DATASTORES_BY_TYPE.values()
        if(allDatastores.isEmpty()) {
            throw new IllegalStateException("No GORM implementations configured. Ensure GORM has been initialized correctly")
        }
        else if(allDatastores.size() > 1) {
            throw new IllegalStateException("More than one GORM implementation is configured. Specific the datastore type!")
        }
        else {
            return allDatastores.first()
        }
    }

    /**
     * Finds a single available transaction manager
     *
     * @return The transaction manager
     *
     * @throws TransactionSystemException If the current implementation does not support transactions
     * @throws IllegalStateException If no GORM implementation has been bootstrapped and configured
     */
    static PlatformTransactionManager findSingleTransactionManager(String connectionName = ConnectionSource.DEFAULT) {
        Datastore datastore = findSingleDatastore()
        return getTransactionManagerForConnection(datastore, connectionName)
    }

    /**
     * Finds a single available transaction manager
     *
     * @return The transaction manager
     *
     * @throws TransactionSystemException If the current implementation does not support transactions
     * @throws IllegalStateException If no GORM implementation has been bootstrapped and configured
     */
    static PlatformTransactionManager findTransactionManager(Class<? extends Datastore> datastoreType, String connectionName = ConnectionSource.DEFAULT) {
        Datastore datastore = findDatastoreByType(datastoreType)
        return getTransactionManagerForConnection(datastore, connectionName)
    }

    /**
     * Find the entity for the given type
     *
     * @param entity The entity class
     * @param qualifier The qualifier
     * @return A entity
     *
     * @throws IllegalStateException if no entity is found for the type
     */
    static PersistentEntity findEntity(Class entity, String qualifier = findTenantId(entity)) {
        findDatastore(entity, qualifier).getMappingContext().getPersistentEntity(entity.name)
    }

    /**
     * Closes the enhancer clearing any stored static state
     *
     * @throws IOException
     */
    @Override
    @CompileStatic
    void close() throws IOException {
        removeConstraints()
        DATASTORES_BY_TYPE.clear()
        def registry = GroovySystem.metaClassRegistry
        for(entity in datastore.mappingContext.persistentEntities) {

            List<String> qualifiers = allQualifiers(datastore, entity)
            def cls = entity.javaClass
            def className = cls.name
            for(q in qualifiers) {
                NAMED_QUERIES.remove(className)
                STATIC_APIS.get(q)?.remove(className)
                INSTANCE_APIS.get(q)?.remove(className)
                VALIDATION_APIS.get(q)?.remove(className)
                DATASTORES.get(q)?.remove(datastore)
            }
            registry.removeMetaClass(cls)
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
            String className = "org.apache.groovy.grails.validation.ConstrainedProperty"
            ClassLoader classLoader = getClass().getClassLoader()
            if(ClassUtils.isPresent(className, classLoader)) {
                classLoader.loadClass(className).removeConstraint('unique')
            }
        } catch (Throwable e) {
            log.debug("Not running in Grails 2 environment, cannot de-register constraints. This exception can be safely ignored if you are not using Grails 2. ${e.message}", e)
        }
    }

    protected void registerConstraints(Datastore datastore) {
        try {
            String className = "org.grails.datastore.gorm.support.ConstraintRegistrar"
            ClassLoader classLoader = getClass().getClassLoader()
            if(ClassUtils.isPresent(className, classLoader)) {
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
     *
     * @param onlyExtendedMethods If only to add additional methods provides by subclasses of the GORM APIs
     */
    @CompileStatic
    void enhance(boolean onlyExtendedMethods = false) {
        if(dynamicEnhance) {
            for (PersistentEntity e in datastore.mappingContext.persistentEntities) {
                if(e.external && !includeExternal) continue
                enhance e, onlyExtendedMethods
            }
        }
    }

    /**
     * Enhance and individual entity
     *
     * @param e The entity
     * @param onlyExtendedMethods If only to add additional methods provides by subclasses of the GORM APIs
     */
    @CompileStatic
    void enhance(PersistentEntity e, boolean onlyExtendedMethods = false) {
        registerEntity(e)

        if(!(GroovyObject.isAssignableFrom(e.javaClass) ) || dynamicEnhance) {
            addInstanceMethods(e, onlyExtendedMethods)

            addStaticMethods(e, onlyExtendedMethods)
        }
    }

    @CompileStatic
    protected void addStaticMethods(PersistentEntity e, boolean onlyExtendedMethods) {
        def cls = e.javaClass
        ExpandoMetaClass mc = MetaClassUtils.getExpandoMetaClass(cls)
        def staticApiProvider = getStaticApi(cls)
        for (Method m in (onlyExtendedMethods ? staticApiProvider.extendedMethods : staticApiProvider.methods)) {
            def method = m
            if (method != null) {
                def methodName = method.name
                def parameterTypes = method.parameterTypes
                if (parameterTypes != null) {
                    boolean realMethodExists = doesRealMethodExist(mc, methodName, parameterTypes, true)
                    if(!realMethodExists) {
                        registerStaticMethod(mc, methodName, parameterTypes, staticApiProvider)
                    }
                }
            }
        }
    }

    @CompileDynamic
    protected void registerStaticMethod(ExpandoMetaClass mc, String methodName, Class<?>[] parameterTypes, GormStaticApi staticApiProvider) {
        def callable = new StaticMethodInvokingClosure(staticApiProvider, methodName, parameterTypes)
        mc.static."$methodName" = callable
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
        Class cls = e.javaClass
        ExpandoMetaClass mc = MetaClassUtils.getExpandoMetaClass(cls)
        for (AbstractGormApi apiProvider in getInstanceMethodApiProviders(cls)) {

            for (Method method in (onlyExtendedMethods ? apiProvider.extendedMethods : apiProvider.methods)) {
                def methodName = method.name
                Class[] parameterTypes = method.parameterTypes

                if (parameterTypes) {
                    parameterTypes = (parameterTypes.length == 1 ? [] : parameterTypes[1..-1]) as Class[]

                    boolean realMethodExists = doesRealMethodExist(mc, methodName, parameterTypes, false)

                    if(!realMethodExists) {
                        registerInstanceMethod(cls, mc, apiProvider, methodName, parameterTypes)
                    }
                }
            }
        }
    }

    protected registerInstanceMethod(Class cls, ExpandoMetaClass mc, AbstractGormApi apiProvider, String methodName, Class[] parameterTypes) {
        // use fake object just so we have the right method signature
        final tooCall = new InstanceMethodInvokingClosure(apiProvider, cls, methodName, parameterTypes)
        def pt = parameterTypes
        // Hack to workaround http://jira.codehaus.org/browse/GROOVY-4720
        final closureMethod = new ClosureStaticMetaMethod(methodName, cls, tooCall, pt) {
                    @Override
                    int getModifiers() { Modifier.PUBLIC }
                }
        mc.registerInstanceMethod(closureMethod)
    }

    @CompileStatic
    protected static boolean doesRealMethodExist(final MetaClass mc, final String methodName, final Class[] parameterTypes, boolean staticScope) {
        boolean realMethodExists = false
        try {
            MetaMethod existingMethod = mc.pickMethod(methodName, parameterTypes)
            if(existingMethod && existingMethod.isStatic()==staticScope && isRealMethod(existingMethod) && parameterTypes.length==existingMethod.parameterTypes.length)  {
                realMethodExists = true
            }
        } catch (MethodSelectionException mse) {
            // the metamethod already exists with multiple signatures, must check if the exact method exists
            realMethodExists = mc.methods.contains { MetaMethod existingMethod ->
                existingMethod.name == methodName && existingMethod.isStatic()==staticScope && isRealMethod(existingMethod) && ((!parameterTypes && !existingMethod.parameterTypes) || parameterTypes==existingMethod.parameterTypes)
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
        new GormStaticApi<D>(cls, datastore, getFinders(), transactionManager)
    }

    @CompileStatic
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, String qualifier = ConnectionSource.DEFAULT) {
        def instanceApi = new GormInstanceApi<D>(cls, datastore)
        instanceApi.failOnError = failOnError
        instanceApi.markDirty = markDirty
        return instanceApi
    }

    @CompileStatic
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, String qualifier = ConnectionSource.DEFAULT) {
        new GormValidationApi(cls, datastore)
    }

    @CompileStatic
    protected List<FinderMethod> createDynamicFinders() {
        Datastore targetDatastore = datastore
        createDynamicFinders(targetDatastore)
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
