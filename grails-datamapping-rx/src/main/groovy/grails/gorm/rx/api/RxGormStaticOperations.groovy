package grails.gorm.rx.api

import grails.gorm.api.GormAllOperations
import grails.gorm.rx.CriteriaBuilder
import grails.gorm.rx.DetachedCriteria
import grails.gorm.rx.proxy.ObservableProxy
import rx.Observable

/**
 * Static methods allowed by RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface RxGormStaticOperations<D> {
    /**
     * @return A new instance of this RxEntity
     */
    D create()

    /**
     * Retrieve an instance by id
     *
     * @param id The id of the instance
     * @return An observable
     */
    Observable<D> get(Serializable id)

    /**
     * Retrieve an instance by id for the given arguments
     *
     * @param id The id
     * @param queryArgs The query arguments
     *
     * @return An observable
     */
    Observable<D> get(Serializable id, Map queryArgs)


    /**
     * Retrieve a proxy to an instance by id
     *
     * @param id The id of the instance
     * @return An observable
     */
    ObservableProxy<D> proxy(Serializable id)

    /**
     * Retrieve a proxy to an for the given arguments
     *
     * @param id The id
     * @param queryArgs The query arguments
     *
     * @return An observable
     */
    ObservableProxy<D> proxy(Serializable id, Map queryArgs)


    /**
     * Retrieve a proxy to an instance by id
     *
     * @param query The query
     * @return An observable
     */
    ObservableProxy<D> proxy(DetachedCriteria<D> query)

    /**
     * Retrieve a proxy to an for the given arguments
     *
     * @param query The query
     * @param queryArgs The query arguments
     *
     * @return An observable
     */
    ObservableProxy<D> proxy(DetachedCriteria<D> query, Map queryArgs)

    /**
     * @return Counts the number of instances
     */
    Observable<Number> count()

    /**
     * Batch deletes a number of objects in one go
     *
     * @param objects The objects to delete
     * @return The number of objects actually deleted
     */
    Observable<Number> deleteAll(D...objects)

    /**
     * Batch deletes a number of objects in one go
     *
     * @param objects The objects to delete
     * @return The number of objects actually deleted
     */
    Observable<Number> deleteAll(Iterable<D> objects)

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    Observable<List<Serializable>> saveAll(Iterable<D> objects)

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @params arguments The arguments to save
     *
     * @return An observable that emits the identifiers of the saved objects
     */
    Observable<List<Serializable>> saveAll(Iterable<D> objects, Map arguments)

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    Observable<List<Serializable>> saveAll(D... objects)

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    Observable<List<Serializable>> insertAll(Iterable<D> objects)

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @params arguments The arguments to save
     *
     * @return An observable that emits the identifiers of the saved objects
     */
    Observable<List<Serializable>> insertAll(Iterable<D> objects, Map arguments)

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    Observable<List<Serializable>> insertAll(D... objects)

    /**
     * Check whether an entity exists for the given id
     *
     * @param id
     * @return
     */
    Observable<Boolean> exists(Serializable id)

    /**
     * Finds the first object using the natural sort order
     *
     * @return A single that will emit the first object, if it exists
     */
    Observable<D> first()

    /**
     * Finds the first object sorted by propertyName
     *
     * @param propertyName the name of the property to sort by
     *
     * @return A single that will emit the first object, if it exists
     */
    Observable<D> first(String propertyName)

    /**
     * Finds the first object.  If queryParams includes 'sort', that will
     * dictate the sort order, otherwise natural sort order will be used.
     * queryParams may include any of the same parameters that might be passed
     * to the list(Map) method.  This method will ignore 'order' and 'max' as
     * those are always 'asc' and 1, respectively.
     *
     * @return the first object in the datastore, null if none exist
     */
    Observable<D> first(Map queryParams)

    /**
     * Finds the last object using the natural sort order
     *
     * @return A single that will emit the last object, if it exists
     */
    Observable<D> last()

    /**
     * Finds the last object sorted by propertyName
     *
     * @param propertyName the name of the property to sort by
     *
     * @return A single that will emit the last object, if it exists
     */
    Observable<D> last(String propertyName)

    /**
     * Finds the last object.  If queryParams includes 'sort', that will
     * dictate the sort order, otherwise natural sort order will be used.
     * queryParams may include any of the same parameters that might be passed
     * to the list(Map) method.  This method will ignore 'order' and 'max' as
     * those are always 'desc' and 1, respectively.
     *
     * @return A single that will emit the last object, if it exists
     */
    Observable<D> last(Map params)



    /**
     * List all entities and return an observable
     *
     * @return An observable with all results
     */
    Observable<List<D>> list()

    /**
     * List all entities and return an observable
     *
     * @return An observable with all results
     */
    Observable<List<D>> list(Map args)

    /**
     * List all entities and return an observable
     *
     * @return An observable with all results
     */
    Observable<D> findAll()

    /**
     * List all entities and return an observable
     *
     * @return An observable with all results
     */
    Observable<D> findAll(Map args)

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    Observable<D> findWhere(Map queryMap)

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @param args The Query arguments
     *
     * @return A single result
     */
    Observable<D> findWhere(Map queryMap, Map args)

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand").  If
     * a matching persistent entity is not found a new entity is created and returned.
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    Observable<D> findOrCreateWhere(Map queryMap)

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand").  If
     * a matching persistent entity is not found a new entity is created, saved and returned.
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    Observable<D> findOrSaveWhere(Map queryMap)

    /**
     * Finds all results matching all of the given conditions. Eg. Book.findAllWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @return A list of results
     */
    Observable<D> findAllWhere(Map queryMap)

    /**
     * Finds all results matching all of the given conditions. Eg. Book.findAllWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @param args The Query arguments
     *
     * @return A list of results
     */
    Observable<D> findAllWhere(Map queryMap, Map args)
    /**
     * Uses detached criteria to build a query and then execute it returning an observable
     *
     * @param callable The callable
     * @return The observable
     */
    Observable<D> findAll(Closure callable)

    /**
     * Uses detached criteria to build a query and then execute it returning an observable
     *
     * @param callable The callable
     * @return The observable
     */
    Observable<D> find(Closure callable)

    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance
     */
    DetachedCriteria<D> where(Closure callable)

    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance that is lazily initialized
     */
    DetachedCriteria<D> whereLazy(Closure callable)

    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance
     */
    DetachedCriteria<D> whereAny(Closure callable)

    /**
     * Creates a criteria builder instance
     */
    CriteriaBuilder<D> createCriteria()

    /**
     * Creates a criteria builder instance
     */
    Observable withCriteria(@DelegatesTo(CriteriaBuilder) Closure callable)

    /**
     * Creates a criteria builder instance
     */
    Observable withCriteria(Map builderArgs, @DelegatesTo(CriteriaBuilder) Closure callable)
    /**
     * Handles dynamic finders
     *
     * @param methodName The method name
     * @param arg The argument to the method
     *
     * @return An observable with the result
     */
    Observable<D> staticMethodMissing(String methodName, arg)

    /**
     * Static property missing implementation
     *
     * @param property The property
     * @return The property value of a MissingPropertyException
     */
    Object staticPropertyMissing(String property)


    /**
     * Execute the closure with the given tenantId
     *
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    def <T> T withTenant(Serializable tenantId, @DelegatesTo(RxGormAllOperations) Closure<T> callable)


    /**
     * Execute the closure for each tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    RxGormAllOperations<D> eachTenant(@DelegatesTo(RxGormAllOperations) Closure callable)

    /**
     * Return the {@link GormAllOperations} for the given tenant id
     *
     * @param tenantId The tenant id
     * @return The operations
     */
    RxGormAllOperations<D> withTenant(Serializable tenantId)
}