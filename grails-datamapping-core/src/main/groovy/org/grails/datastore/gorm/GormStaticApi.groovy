/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm

import grails.gorm.CriteriaBuilder
import grails.gorm.DetachedCriteria
import grails.gorm.MultiTenant
import grails.gorm.PagedResultList
import grails.gorm.api.GormAllOperations
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.GrailsTransactionTemplate
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import org.codehaus.groovy.runtime.InvokerHelper
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.multitenancy.TenantDelegatingGormOperations
import org.grails.datastore.gorm.query.NamedCriteriaProxy
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.StatelessDatastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.query.api.Criteria
import org.springframework.beans.PropertyAccessorFactory
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.util.Assert

/**
 * Static methods of the GORM API.
 *
 * @author Graeme Rocher
 * @param <D> the entity/domain class
 */
@CompileStatic
class GormStaticApi<D> extends AbstractGormApi<D> implements GormAllOperations<D> {

    protected final List<FinderMethod> gormDynamicFinders

    protected final PlatformTransactionManager transactionManager
    protected final String defaultQualifier
    protected final MultiTenancyMode multiTenancyMode
    protected final ConnectionSources connectionSources

    GormStaticApi(Class<D> persistentClass, Datastore datastore, List<FinderMethod> finders) {
        this(persistentClass, datastore, finders, null)
    }

    GormStaticApi(Class<D> persistentClass, Datastore datastore, List<FinderMethod> finders, PlatformTransactionManager transactionManager) {
        super(persistentClass, datastore)
        gormDynamicFinders = finders
        this.transactionManager = transactionManager
        String qualifier = ConnectionSource.DEFAULT
        if(datastore instanceof ConnectionSourcesProvider) {
            this.connectionSources = ((ConnectionSourcesProvider) datastore).connectionSources
            ConnectionSource<?, ? extends ConnectionSourceSettings> defaultConnectionSource = connectionSources.defaultConnectionSource
            qualifier = defaultConnectionSource.name
            multiTenancyMode = defaultConnectionSource.settings.multiTenancy.mode

        }
        else {
            connectionSources = null
            multiTenancyMode = MultiTenancyMode.NONE
        }
        this.defaultQualifier = qualifier
    }

    /**
     * @return The PersistentEntity for this class
     */
    PersistentEntity getGormPersistentEntity() {
        persistentEntity
    }

    List<FinderMethod> getGormDynamicFinders() {
        gormDynamicFinders
    }

    /**
     * Property missing handler
     *
     * @param name The name of the property
     */
    def propertyMissing(String name) {
        if(datastore instanceof ConnectionSourcesProvider) {
            return GormEnhancer.findStaticApi(persistentClass, name)
        }
        else {
            throw new MissingPropertyException(name, persistentClass)
        }
    }

    /**
     * Property missing handler
     *
     * @param name The name of the property
     */
    void propertyMissing(String name, value) {
        throw new MissingPropertyException(name, persistentClass)
    }

    /**
     * Method missing handler that deals with the invocation of dynamic finders
     *
     * @param methodName The method name
     * @param args The arguments
     * @return The result of the method call
     */
    @CompileDynamic
    def methodMissing(String methodName, Object args) {
        FinderMethod method = gormDynamicFinders.find { FinderMethod f -> f.isMethodMatch(methodName) }
        if (!method) {
            if(args && args[-1] instanceof Closure) {
                NamedCriteriaProxy proxy = GormEnhancer.createNamedQuery(persistentClass, methodName)
                if(proxy != null) {
                    return proxy.call(args)
                }
                else {
                    throw new MissingMethodException(methodName, persistentClass, args)
                }
            }
            else {
                throw new MissingMethodException(methodName, persistentClass, args)
            }
        }

        // if the class is multi tenant, don't cache the method because the tenant will need to be resolved
        // for each method call
        if(!MultiTenant.isAssignableFrom(persistentClass)) {

            def mc = persistentClass.getMetaClass()

            // register the method invocation for next time
            mc.static."$methodName" = { Object[] varArgs ->
                // FYI... This is relevant to http://jira.grails.org/browse/GRAILS-3463 and may
                // become problematic if http://jira.codehaus.org/browse/GROOVY-5876 is addressed...
                final argumentsForMethod
                if(varArgs == null) {
                    argumentsForMethod = [null] as Object[]
                }
                // if the argument component type is not an Object then we have an array passed that is the actual argument
                else if(varArgs.getClass().componentType != Object) {
                    // so we wrap it in an object array
                    argumentsForMethod = [varArgs] as Object[]
                }
                else {

                    if(varArgs.length == 1 && varArgs[0].getClass().isArray()) {
                        argumentsForMethod = varArgs[0]
                    } else {

                        argumentsForMethod = varArgs
                    }
                }
                method.invoke(delegate, methodName, argumentsForMethod)
            }
        }

        return method.invoke(persistentClass, methodName, args)
    }

    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance
     */
    DetachedCriteria<D> where(Closure callable) {
        new DetachedCriteria<D>(persistentClass).build(callable)
    }

    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance that is lazily initialized
     */
    DetachedCriteria<D> whereLazy(Closure callable) {
        new DetachedCriteria<D>(persistentClass).buildLazy(callable)
    }
    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance
     */
    DetachedCriteria<D> whereAny(Closure callable) {
        (DetachedCriteria<D>)new DetachedCriteria<D>(persistentClass).or(callable)
    }

    /**
     * Uses detached criteria to build a query and then execute it returning a list
     *
     * @param callable The callable
     * @return A List of entities
     */
    List<D> findAll(Closure callable) {
        def criteria = new DetachedCriteria<D>(persistentClass).build(callable)
        return criteria.list()
    }

    /**
     * Uses detached criteria to build a query and then execute it returning a list
     *
     * @param args pagination parameters
     * @param callable The callable
     * @return A List of entities
     */
    List<D> findAll(Map args, Closure callable) {
        def criteria = new DetachedCriteria<D>(persistentClass).build(callable)
        return criteria.list(args)
    }

    /**
     * Uses detached criteria to build a query and then execute it returning a list
     *
     * @param callable The callable
     * @return A single entity
     */
    D find(Closure callable) {
        def criteria = new DetachedCriteria<D>(persistentClass).build(callable)
        return criteria.find()
    }

    /**
     * Saves a list of objects in one go
     * @param objectsToSave The objects to save
     * @return A list of object identifiers
     */
    List<Serializable> saveAll(Object... objectsToSave) {
        (List<Serializable>)execute({ Session session ->
            session.persist Arrays.asList(objectsToSave)
        } as SessionCallback)
    }

    /**
     * Saves a list of objects in one go
     * @param objectToSave Collection of objects to save
     * @return A list of object identifiers
     */
    List<Serializable> saveAll(Iterable<?> objectsToSave) {
        (List<Serializable>)execute({ Session session ->
            session.persist objectsToSave
        } as SessionCallback)
    }

    /**
     * Deletes a list of objects in one go
     * @param objectsToDelete The objects to delete
     */
    void deleteAll(Object... objectsToDelete) {
        execute({ Session session ->
            session.delete Arrays.asList(objectsToDelete)
        } as SessionCallback)
    }

    /**
     * Deletes a list of objects in one go and flushes when param is set
     * @param objectsToDelete The objects to delete
     */
    void deleteAll(Map params, Object... objectsToDelete) {
        execute({ Session session ->
            session.delete Arrays.asList(objectsToDelete)
            if(params?.flush) {
                session.flush()
            }
        } as SessionCallback)
    }

    /**
     * Deletes a list of objects in one go
     * @param objectsToDelete Collection of objects to delete
     */
    void deleteAll(Iterable objectToDelete) {
        execute({ Session session ->
            session.delete objectToDelete
        } as SessionCallback)
    }

    /**
     * Deletes a list of objects in one go and flushes when param is set
     * @param objectsToDelete Collection of objects to delete
     */
    void deleteAll(Map params, Iterable objectToDelete) {
        execute({ Session session ->
            session.delete objectToDelete
            if(params?.flush) {
                session.flush()
            }
        } as SessionCallback)
    }

    /**
     * Creates an instance of this class
     * @return The created instance
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    D create() {
        D d = persistentClass.newInstance()

        def applicationContext = datastore.applicationContext

        if(applicationContext != null) {
            applicationContext.autowireCapableBeanFactory.autowireBeanProperties(
                    d, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
        }

        return d
    }

    /**
     * Retrieves an object from the datastore. eg. Book.get(1)
     */
    D get(Serializable id) {
        (D)execute({ Session session ->
            session.retrieve((Class)persistentClass, id)
        } as SessionCallback)
    }

    /**
     * Retrieves an object from the datastore. eg. Book.read(1)
     *
     * Since the datastore abstraction doesn't support dirty checking yet this
     * just delegates to {@link #get(Serializable)}
     */
    D read(Serializable id) {
        (D)execute ({ Session session ->
            session.retrieve((Class)persistentClass, id)
        } as SessionCallback)
    }

    /**
     * Retrieves an object from the datastore as a proxy. eg. Book.load(1)
     */
    D load(Serializable id) {
        (D)execute ({ Session session ->
            session.proxy((Class)persistentClass, id)
        } as SessionCallback)
    }

    /**
     * Retrieves an object from the datastore as a proxy. eg. Book.proxy(1)
     */
    D proxy(Serializable id) {
        load(id)
    }

    /**
     * Retrieve all the objects for the given identifiers
     * @param ids The identifiers to operate against
     * @return A list of identifiers
     */
    List<D> getAll(Iterable<Serializable> ids) {
        return getAll(ids as Serializable[])
    }

    /**
     * Retrieve all the objects for the given identifiers
     * @param ids The identifiers to operate against
     * @return A list of identifiers
     */
    List<D> getAll(Serializable... ids) {
        (List<D>)execute ({ Session session ->
            session.retrieveAll(persistentClass, ids.flatten())
        } as SessionCallback)
    }

    /**
     * @return Synonym for {@link #list()}
     */
    List<D> getAll() {
        list()
    }

    /**
     * Creates a criteria builder instance
     */
    BuildableCriteria createCriteria() {
        new CriteriaBuilder(persistentClass, datastore.currentSession)
    }

    /**
     * Creates a criteria builder instance
     */
    def withCriteria(@DelegatesTo(Criteria) Closure callable) {
        execute({ Session session ->
            InvokerHelper.invokeMethod(createCriteria(), 'call', callable)
        } as SessionCallback )
    }

    /**
     * Creates a criteria builder instance
     */
    def withCriteria(Map builderArgs, @DelegatesTo(Criteria) Closure callable) {
        def criteriaBuilder = createCriteria()
        def builderBean = PropertyAccessorFactory.forBeanPropertyAccess(criteriaBuilder)
        for (entry in builderArgs.entrySet()) {
            String propertyName = entry.key.toString()
            if (builderBean.isWritableProperty(propertyName)) {
                builderBean.setPropertyValue(propertyName, entry.value)
            }
        }

        if(builderArgs?.uniqueResult) {
            execute({ Session session ->
                InvokerHelper.invokeMethod(criteriaBuilder, 'get', callable)
            } as SessionCallback )

        }
        else {
            execute({ Session session ->
                InvokerHelper.invokeMethod(criteriaBuilder, 'list', callable)
            } as SessionCallback )
        }

    }

    /**
     * Locks an instance for an update
     * @param id The identifier
     * @return The instance
     */
    D lock(Serializable id) {
        (D)execute ({ Session session ->
            session.lock((Class)persistentClass, id)
        } as SessionCallback)
    }

    /**
     * Merges an instance with the current session
     * @param d The object to merge
     * @return The instance
     */
    @Override
    def propertyMissing(D instance, String name) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).propertyMissing(instance, name)
    }

    @Override
    boolean instanceOf(D instance, Class cls) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).instanceOf(instance,cls)
    }

    @Override
    D lock(D instance) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).lock(instance)
    }

    @Override
    def <T> T mutex(D instance, Closure<T> callable) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).mutex(instance, callable)
    }

    @Override
    D refresh(D instance) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).refresh(instance)
    }

    @Override
    D save(D instance) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).save(instance)
    }

    @Override
    D insert(D instance) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).insert(instance)
    }

    @Override
    D insert(D instance, Map params) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).insert(instance, params)
    }

    D merge(D d) {
        execute ({ Session session ->
            session.persist(d)
            return d
        } as SessionCallback)
    }

    @Override
    D merge(D instance, Map params) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).merge(instance, params)
    }

    @Override
    D save(D instance, boolean validate) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).save(instance, validate)
    }

    @Override
    D save(D instance, Map params) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).save(instance, params)
    }

    @Override
    Serializable ident(D instance) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).ident(instance)
    }

    @Override
    D attach(D instance) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).attach(instance)
    }

    @Override
    boolean isAttached(D instance) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).isAttached(instance)
    }

    @Override
    void discard(D instance) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).discard(instance)
    }

    @Override
    void delete(D instance) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).delete(instance)
    }

    @Override
    void delete(D instance, Map params) {
        GormEnhancer.findInstanceApi(persistentClass, defaultQualifier).delete(instance, params)
    }
    /**
     * Counts the number of persisted entities
     * @return The number of persisted entities
     */
    Integer count() {
        (Integer)execute ({ Session session ->

            def q = session.createQuery(persistentClass)
            q.projections().count()
            def result = q.singleResult()
            if (!(result instanceof Number)) {
                result = result.toString()
            }
            try {
                return result as Integer
            }
            catch (NumberFormatException e) {
                return 0
            }
        } as SessionCallback)
    }

    /**
     * Same as {@link #count()} but allows property-style syntax (Foo.count)
     */
    Integer getCount() {
        count()
    }

    /**
     * Checks whether an entity exists
     */
    boolean exists(Serializable id) {
        get(id) != null
    }

    /**
     * Lists objects in the datastore. eg. Book.list(max:10)
     *
     * @param params Any parameters such as offset, max etc.
     * @return A list of results
     */
    List<D> list(Map params) {
        (List<D>)execute ({ Session session ->
            Query q = session.createQuery(persistentClass)
            DynamicFinder.populateArgumentsForCriteria(persistentClass, q, params)
            if (params?.max) {
                return new PagedResultList(q)
            }
            return q.list()
        } as SessionCallback)
    }

    /**
     * List all entities
     *
     * @return The list of all entities
     */
    List<D> list() {
        (List<D>)execute ({ Session session ->
            session.createQuery(persistentClass).list()
        } as SessionCallback)
    }

    /**
     * The same as {@link #list()}
     *
     * @return The list of all entities
     */
    List<D> findAll(Map params = Collections.emptyMap()) {
        list(params)
    }

    /**
     * Finds an object by example
     *
     * @param example The example
     * @return A list of matching results
     */
    List<D> findAll(D example) {
        findAll(example, Collections.emptyMap())
    }

    /**
     * Finds an object by example using the given arguments for pagination
     *
     * @param example The example
     * @param args The arguments
     *
     * @return A list of matching results
     */
    List<D> findAll(D example, Map args) {
        if (!persistentEntity.isInstance(example)) {
            return Collections.emptyList()
        }

        def queryMap = createQueryMapForExample(persistentEntity, example)
        return findAllWhere(queryMap, args)
    }

    /**
     * Finds the first object using the natural sort order
     *
     * @return the first object in the datastore, null if none exist
     */
    D first() {
        first([:])
    }

    /**
     * Finds the first object sorted by propertyName
     *
     * @param propertyName the name of the property to sort by
     *
     * @return the first object in the datastore sorted by propertyName, null if none exist
     */
    D first(String propertyName) {
        first(sort: propertyName)
    }

    /**
     * Finds the first object.  If queryParams includes 'sort', that will
     * dictate the sort order, otherwise natural sort order will be used.
     * queryParams may include any of the same parameters that might be passed
     * to the list(Map) method.  This method will ignore 'order' and 'max' as
     * those are always 'asc' and 1, respectively.
     *
     * @return the first object in the datastore, null if none exist
     */
    D first(Map queryParams) {
        queryParams.max = 1
        queryParams.order = 'asc'
        if (!queryParams.containsKey('sort')) {
            def idPropertyName = persistentEntity.identity?.name
            if (idPropertyName) {
                queryParams.sort = idPropertyName
            }
        }
        def resultList = list(queryParams)
        resultList ? resultList[0] : null
    }

    /**
     * Finds the last object using the natural sort order
     *
     * @return the last object in the datastore, null if none exist
     */
    D last() {
        last([:])
    }

    /**
     * Finds the last object sorted by propertyName
     *
     * @param propertyName the name of the property to sort by
     *
     * @return the last object in the datastore sorted by propertyName, null if none exist
     */
    D last(String propertyName) {
        last(sort: propertyName)
    }

/**
 * Finds the last object.  If queryParams includes 'sort', that will
 * dictate the sort order, otherwise natural sort order will be used.
 * queryParams may include any of the same parameters that might be passed
 * to the list(Map) method.  This method will ignore 'order' and 'max' as
 * those are always 'asc' and 1, respectively.
 *
 * @return the last object in the datastore, null if none exist
 */
    D last(Map queryParams) {
        queryParams.max = 1
        queryParams.order = 'desc'
        if (!queryParams.containsKey('sort')) {
            def idPropertyName = persistentEntity.identity?.name
            if (idPropertyName) {
                queryParams.sort = idPropertyName
            }
        }
        def resultList = list(queryParams)
        resultList ? resultList[0] : null
    }

    /**
     * Finds all results matching all of the given conditions. Eg. Book.findAllWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @return A list of results
     */
    List<D> findAllWhere(Map queryMap) {
        findAllWhere(queryMap, Collections.emptyMap())
    }

    /**
     * Finds all results matching all of the given conditions. Eg. Book.findAllWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @param args The Query arguments
     *
     * @return A list of results
     */
    List<D> findAllWhere(Map queryMap, Map args) {
        (List<D>)execute ({ Session session ->
            Query q = session.createQuery(persistentClass)

            Map<String, Object> processedQueryMap = [:]
            queryMap.each{ key, value -> processedQueryMap[key.toString()] = value }
            q.allEq(processedQueryMap)

            DynamicFinder.populateArgumentsForCriteria persistentClass, q, args
            q.list()
        } as SessionCallback<List>)
    }

    /**
     * Finds an object by example
     *
     * @param example The example
     * @return A list of matching results
     */
    D find(D example) {
        find(example, Collections.emptyMap())
    }

    /**
     * Finds an object by example using the given arguments for pagination
     *
     * @param example The example
     * @param args The arguments
     *
     * @return A list of matching results
     */
    D find(D example, Map args) {
        if (persistentEntity.isInstance(example)) {
            def queryMap = createQueryMapForExample(persistentEntity, example)
            return findWhere(queryMap, args)
        }
        return null
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    D findWhere(Map queryMap) {
        findWhere(queryMap, Collections.emptyMap())
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @param args The Query arguments
     *
     * @return A single result
     */
    D findWhere(Map queryMap, Map args) {
        execute({ Session session ->
            Query q = session.createQuery(persistentClass)
            if (queryMap) {
                Map<String, Object> processedQueryMap = [:]
                queryMap.each{ key, value -> processedQueryMap[key.toString()] = value }
                q.allEq(processedQueryMap)
            }
            DynamicFinder.populateArgumentsForCriteria persistentClass, q, args
            q.singleResult()
        } as SessionCallback)
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand").  If
     * a matching persistent entity is not found a new entity is created and returned.
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    D findOrCreateWhere(Map queryMap) {
        internalFindOrCreate(queryMap, false)
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand").  If
     * a matching persistent entity is not found a new entity is created, saved and returned.
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    D findOrSaveWhere(Map queryMap) {
        internalFindOrCreate(queryMap, true)
    }

    /**
     * Execute a closure whose first argument is a reference to the current session.
     *
     * @param callable the closure
     * @return The result of the closure
     */
    public <T> T  withSession(Closure<T> callable) {
        execute ({ Session session ->
            callable.call session
        } as SessionCallback)
    }

    /**
     * Same as withSession, but present for the case where withSession is overridden to use the Hibernate session
     *
     * @param callable the closure
     * @return The result of the closure
     */
    public <T> T  withDatastoreSession(Closure<T> callable) {
        execute ({ Session session ->
            callable.call session
        } as SessionCallback)
    }

    /**
     * Executes the closure within the context of a transaction, creating one if none is present or joining
     * an existing transaction if one is already present.
     *
     * @param callable The closure to call
     * @return The result of the closure execution
     * @see #withTransaction(Map, Closure)
     * @see #withNewTransaction(Closure)
     * @see #withNewTransaction(Map, Closure)
     */
    public <T> T withTransaction(Closure<T> callable) {
        withTransaction(new DefaultTransactionDefinition(), callable)
    }

    @Override
    def <T> T withTenant(Serializable tenantId, Closure<T> callable) {
        if(multiTenancyMode == MultiTenancyMode.DATABASE) {
            Tenants.withId((Class<Datastore>)GormEnhancer.findDatastore(persistentClass, tenantId.toString()).getClass(), tenantId, callable)
        }
        else if(multiTenancyMode.isSharedConnection()) {
            Tenants.withId((Class<Datastore>)GormEnhancer.findDatastore(persistentClass, ConnectionSource.DEFAULT).getClass(), tenantId, callable)
        }
        else {
            throw new UnsupportedOperationException("Method not supported in multi tenancy mode $multiTenancyMode")
        }
    }

    @Override
    GormAllOperations<D> eachTenant(Closure callable) {
        if(multiTenancyMode != MultiTenancyMode.NONE) {
            Tenants.eachTenant callable
            return this
        }
        else {
            throw new UnsupportedOperationException("Method not supported in multi tenancy mode $multiTenancyMode")
        }
    }

    @Override
    GormAllOperations<D> withTenant(Serializable tenantId) {
        if(multiTenancyMode == MultiTenancyMode.DATABASE) {
            return GormEnhancer.findStaticApi(persistentClass, tenantId.toString())
        }
        else if(multiTenancyMode.isSharedConnection()) {
            def staticApi = GormEnhancer.findStaticApi(persistentClass, ConnectionSource.DEFAULT)
            return new TenantDelegatingGormOperations<D>(datastore, tenantId, staticApi)
        }
        else {
            throw new UnsupportedOperationException("Method not supported in multi tenancy mode $multiTenancyMode")
        }
    }
    /**
     * Executes the closure within the context of a new transaction
     *
     * @param callable The closure to call
     * @return The result of the closure execution
     * @see #withTransaction(Closure)
     * @see #withTransaction(Map, Closure)
     * @see #withNewTransaction(Map, Closure)
     */
    public <T> T  withNewTransaction(Closure<T> callable) {
        withTransaction([propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW], callable)
    }

    /**
     * Executes the closure within the context of a transaction which is
     * configured with the properties contained in transactionProperties.
     * transactionProperties may contain any properties supported by
     * {@link DefaultTransactionDefinition}.
     *
     * <blockquote>
     * <pre>
     * SomeEntity.withTransaction([propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW,
     *                             isolationLevel: TransactionDefinition.ISOLATION_REPEATABLE_READ]) {
     *     // ...
     * }
     * </pre>
     * </blockquote>
     *
     * @param transactionProperties properties to configure the transaction properties
     * @param callable The closure to call
     * @return The result of the closure execution
     * @see DefaultTransactionDefinition
     * @see #withNewTransaction(Closure)
     * @see #withNewTransaction(Map, Closure)
     * @see #withTransaction(Closure)
     */
    public <T> T  withTransaction(Map transactionProperties, Closure<T> callable) {
        def transactionDefinition = new DefaultTransactionDefinition()
        transactionProperties.each { k, v ->
            if(v instanceof CharSequence && !(v instanceof String)) {
                v = v.toString()
            }
            try {
                transactionDefinition[k as String] = v
            } catch (MissingPropertyException mpe) {
                throw new IllegalArgumentException("[${k}] is not a valid transaction property.")
            }
        }

        withTransaction(transactionDefinition, callable)
    }

    /**
     * Executes the closure within the context of a new transaction which is
     * configured with the properties contained in transactionProperties.
     * transactionProperties may contain any properties supported by
     * {@link DefaultTransactionDefinition}.  Note that if transactionProperties
     * includes entries for propagationBehavior or propagationName, those values
     * will be ignored.  This method always sets the propagation level to
     * TransactionDefinition.REQUIRES_NEW.
     *
     * <blockquote>
     * <pre>
     * SomeEntity.withNewTransaction([isolationLevel: TransactionDefinition.ISOLATION_REPEATABLE_READ]) {
     *     // ...
     * }
     * </pre>
     * </blockquote>
     *
     * @param transactionProperties properties to configure the transaction properties
     * @param callable The closure to call
     * @return The result of the closure execution
     * @see DefaultTransactionDefinition
     * @see #withNewTransaction(Closure)
     * @see #withTransaction(Closure)
     * @see #withTransaction(Map, Closure)
     */
    public <T> T withNewTransaction(Map transactionProperties, Closure<T> callable) {
        def props = new HashMap(transactionProperties)
        props.remove 'propagationName'
        props.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        withTransaction(props, callable)
    }

    /**
     * Executes the closure within the context of a transaction for the given {@link TransactionDefinition}
     *
     * @param callable The closure to call
     * @return The result of the closure execution
     */
    public <T> T withTransaction(TransactionDefinition definition, Closure<T> callable) {
        Assert.notNull transactionManager, "No transactionManager bean configured"

        if (!callable) {
            return
        }

        new GrailsTransactionTemplate(transactionManager, definition).execute(callable)
    }

    /**
     * Creates and binds a new session for the scope of the given closure
     */
    public <T> T withNewSession(Closure<T> callable) {
        def session = datastore.connect()
        try {
            DatastoreUtils.bindNewSession session
            return callable?.call(session)
        }
        finally {
            DatastoreUtils.unbindSession session
        }
    }

    /**
     * Creates and binds a new session for the scope of the given closure
     */
    public <T> T  withStatelessSession(Closure<T> callable) {
        if(datastore instanceof StatelessDatastore) {
            def session = datastore.connectStateless()
            try {
                DatastoreUtils.bindNewSession session
                return callable?.call(session)
            }
            finally {
                DatastoreUtils.unbindSession session
            }
        }
        else {
            throw new UnsupportedOperationException("Stateless sessions not supported by implementation")
        }
    }

    @Override
    List executeQuery(CharSequence query) {
        executeQuery(query, Collections.emptyMap(), Collections.emptyMap())
    }

    @Override
    List executeQuery(CharSequence query, Map args) {
        executeQuery(query, args, args)
    }

    @Override
    List executeQuery(CharSequence query, Map params, Map args) {
        unsupported("executeQuery")
        return null
    }

    @Override
    List executeQuery(CharSequence query, Collection params) {
        executeQuery(query, params, Collections.emptyMap())
    }

    @Override
    List executeQuery(CharSequence query, Object...params) {
        executeQuery(query, params.toList(), Collections.emptyMap())
    }

    @Override
    List executeQuery(CharSequence query, Collection params, Map args) {
        unsupported("executeQuery")
        return null
    }

    @Override
    Integer executeUpdate(CharSequence query) {
        executeUpdate(query, Collections.emptyMap(), Collections.emptyMap())
    }

    @Override
    Integer executeUpdate(CharSequence query, Map args) {
        executeUpdate(query, args, args)
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {
        unsupported("executeUpdate")
        return null
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params) {
        executeUpdate(query, params, Collections.emptyMap())
    }

    @Override
    Integer executeUpdate(CharSequence query, Object...params) {
        executeUpdate(query, params.toList(), Collections.emptyMap())
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params, Map args) {
        unsupported("executeUpdate")
        return null
    }

    @Override
    D find(CharSequence query) {
        find(query, Collections.emptyMap())
    }

    @Override
    D find(CharSequence query, Map params) {
        find(query, params, params)
    }

    @Override
    D find(CharSequence query, Map params, Map args) {
        unsupported("find")
        return null
    }

    @Override
    D find(CharSequence query, Collection params) {
        find(query, params, Collections.emptyMap())
    }

    @Override
    D find(CharSequence query, Object[] params) {
        find(query, params.toList(), Collections.emptyMap())
    }

    @Override
    D find(CharSequence query, Collection params, Map args) {
        unsupported("find")
        return null
    }

    @Override
    List<D> findAll(CharSequence query) {
        findAll(query, Collections.emptyMap(), Collections.emptyMap())
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        findAll(query, params, params)
    }

    @Override
    List<D> findAll(CharSequence query, Map params, Map args) {
        unsupported("findAll")
        return null
    }

    @Override
    List<D> findAll(CharSequence query, Collection params) {
        findAll(query, params, Collections.emptyMap())
    }

    @Override
    List<D> findAll(CharSequence query, Object[] params) {
        findAll(query, params.toList(), Collections.emptyMap())
    }

    @Override
    List<D> findAll(CharSequence query, Collection params, Map args) {
        unsupported("findAll")
        return null
    }

    protected void unsupported(method) {
        throw new UnsupportedOperationException("String-based queries like [$method] are currently not supported in this implementation of GORM. Use criteria instead.")
    }

    private Map createQueryMapForExample(PersistentEntity persistentEntity, D example) {
        def props = persistentEntity.persistentProperties.findAll { PersistentProperty prop ->
            !(prop instanceof Association)
        }

        def queryMap = [:]
        for (PersistentProperty prop in props) {
            def val = example[prop.name]
            if (val != null) {
                queryMap[prop.name] = val
            }
        }
        return queryMap
    }

    private D internalFindOrCreate(Map queryMap, boolean shouldSave) {
        D result = findWhere(queryMap)
        if (!result) {
            def persistentMetaClass = GroovySystem.metaClassRegistry.getMetaClass(persistentClass)
            result = (D)persistentMetaClass.invokeConstructor(queryMap)
            if (shouldSave) {
                InvokerHelper.invokeMethod(result, "save", null)
            }
        }
        result
    }
}
