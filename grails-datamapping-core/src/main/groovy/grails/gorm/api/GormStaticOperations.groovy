package grails.gorm.api

import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.query.GormQueryOperations
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.query.api.Criteria
import org.springframework.transaction.TransactionDefinition

/**
 * Interface for the default static methods in GORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface GormStaticOperations<D> {

    /**
     * @return The PersistentEntity for this class
     */
    PersistentEntity getGormPersistentEntity()

    /**
     * @return The GORM dynamic finders
     */
    List<FinderMethod> getGormDynamicFinders()

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
     * Uses detached criteria to build a query and then execute it returning a list
     *
     * @param callable The callable
     * @return A List of entities
     */
    List<D> findAll(Closure callable)

    /**
     * Uses detached criteria to build a query and then execute it returning a list
     *
     * @param args pagination parameters
     * @param callable The callable
     * @return A List of entities
     */
    List<D> findAll(Map args, Closure callable)

    /**
     * Uses detached criteria to build a query and then execute it returning a list
     *
     * @param callable The callable
     * @return A single entity
     */
    D find(Closure callable)


    /**
     * Saves a list of objects in one go
     * @param objectsToSave The objects to save
     * @return A list of object identifiers
     */
    List<Serializable> saveAll(Object... objectsToSave)

    /**
     * Saves a list of objects in one go
     * @param objectToSave Collection of objects to save
     * @return A list of object identifiers
     */
    List<Serializable> saveAll(Iterable<?> objectsToSave)

    /**
     * Deletes a list of objects in one go
     * @param objectsToDelete The objects to delete
     */
    void deleteAll(Object... objectsToDelete)

    /**
     * Deletes a list of objects in one go
     * @param objectsToDelete Collection of objects to delete
     */
    void deleteAll(Iterable objectToDelete)

    /**
     * Creates an instance of this class
     * @return The created instance
     */
    D create()

    /**
     * Retrieves an object from the datastore. eg. Book.get(1)
     */
    D get(Serializable id)

    /**
     * Retrieves an object from the datastore. eg. Book.read(1)
     *
     * Since the datastore abstraction doesn't support dirty checking yet this
     * just delegates to {@link #get(Serializable)}
     */
    D read(Serializable id)

    /**
     * Retrieves an object from the datastore as a proxy. eg. Book.load(1)
     */
    D load(Serializable id)

    /**
     * Retrieves an object from the datastore as a proxy. eg. Book.proxy(1)
     */
    D proxy(Serializable id)

    /**
     * Retrieve all the objects for the given identifiers
     * @param ids The identifiers to operate against
     * @return A list of identifiers
     */
    List<D> getAll(Iterable<Serializable> ids)

    /**
     * Retrieve all the objects for the given identifiers
     * @param ids The identifiers to operate against
     * @return A list of identifiers
     */
    List<D> getAll(Serializable... ids)

    /**
     * @return Synonym for {@link #list()}
     */
    List<D> getAll()

    /**
     * Creates a criteria builder instance
     */
    BuildableCriteria createCriteria()

    /**
     * Creates a criteria builder instance
     */
    public <T> T withCriteria(@DelegatesTo(Criteria) Closure<T> callable)

    /**
     * Creates a criteria builder instance
     */
    public <T> T withCriteria(Map builderArgs, @DelegatesTo(Criteria) Closure callable)

    /**
     * Locks an instance for an update
     * @param id The identifier
     * @return The instance
     */
    D lock(Serializable id)

    /**
     * Merges an instance with the current session
     * @param d The object to merge
     * @return The instance
     */
    D merge(D d)

    /**
     * Counts the number of persisted entities
     * @return The number of persisted entities
     */
    Integer count()

    /**
     * Same as {@link #count()} but allows property-style syntax (Foo.count)
     */
    Integer getCount()

    /**
     * Checks whether an entity exists
     */
    boolean exists(Serializable id)

    /**
     * Lists objects in the datastore. eg. Book.list(max:10)
     *
     * @param params Any parameters such as offset, max etc.
     * @return A list of results
     */
    List<D> list(Map params)
    /**
     * List all entities
     *
     * @return The list of all entities
     */
    List<D> list()

    /**
     * The same as {@link #list()}
     *
     * @return The list of all entities
     */
    List<D> findAll(Map params)

    /**
     * The same as {@link #list()}
     *
     */
    List<D> findAll()

    /**
     * Finds an object by example
     *
     * @param example The example
     * @return A list of matching results
     */
    List<D> findAll(D example)

    /**
     * Finds an object by example using the given arguments for pagination
     *
     * @param example The example
     * @param args The arguments
     *
     * @return A list of matching results
     */
    List<D> findAll(D example, Map args)

    /**
     * Finds the first object using the natural sort order
     *
     * @return the first object in the datastore, null if none exist
     */
    D first()

    /**
     * Finds the first object sorted by propertyName
     *
     * @param propertyName the name of the property to sort by
     *
     * @return the first object in the datastore sorted by propertyName, null if none exist
     */
    D first(String propertyName)

    /**
     * Finds the first object.  If queryParams includes 'sort', that will
     * dictate the sort order, otherwise natural sort order will be used.
     * queryParams may include any of the same parameters that might be passed
     * to the list(Map) method.  This method will ignore 'order' and 'max' as
     * those are always 'asc' and 1, respectively.
     *
     * @return the first object in the datastore, null if none exist
     */
    D first(Map queryParams)

    /**
     * Finds the last object using the natural sort order
     *
     * @return the last object in the datastore, null if none exist
     */
    D last()

    /**
     * Finds the last object sorted by propertyName
     *
     * @param propertyName the name of the property to sort by
     *
     * @return the last object in the datastore sorted by propertyName, null if none exist
     */
    D last(String propertyName)

    /**
     * Handles static method missing for dynamic finders
     *
     * @param methodName The name of the method
     * @param arg the argument to the method
     * @return The value
     */
    Object methodMissing(String methodName, arg)

    /**
     * Handles property missing, does nothing by default, sub traits to override
     *
     * @param property The property
     * @return The value if an exception if the property doesn't exist
     */
    Object propertyMissing(String property)

    /**
     * Handles property missing, does nothing by default, sub traits to override
     *
     * @param property The property
     * @param value The value of the property
     * @return The value if an exception if the property doesn't exist
     */
    void propertyMissing(String property, value)

    /**
     * Finds the last object.  If queryParams includes 'sort', that will
     * dictate the sort order, otherwise natural sort order will be used.
     * queryParams may include any of the same parameters that might be passed
     * to the list(Map) method.  This method will ignore 'order' and 'max' as
     * those are always 'asc' and 1, respectively.
     *
     * @return the last object in the datastore, null if none exist
     */
    D last(Map queryParams)

    /**
     * Finds all results matching all of the given conditions. Eg. Book.findAllWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @return A list of results
     */
    List<D> findAllWhere(Map queryMap)

    /**
     * Finds all results matching all of the given conditions. Eg. Book.findAllWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @param args The Query arguments
     *
     * @return A list of results
     */
    List<D> findAllWhere(Map queryMap, Map args)

    /**
     * Finds an object by example
     *
     * @param example The example
     * @return A list of matching results
     */
    D find(D example)

    /**
     * Finds an object by example using the given arguments for pagination
     *
     * @param example The example
     * @param args The arguments
     *
     * @return A list of matching results
     */
    D find(D example, Map args)

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    D findWhere(Map queryMap)

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @param args The Query arguments
     *
     * @return A single result
     */
    D findWhere(Map queryMap, Map args)

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand").  If
     * a matching persistent entity is not found a new entity is created and returned.
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    D findOrCreateWhere(Map queryMap)

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand").  If
     * a matching persistent entity is not found a new entity is created, saved and returned.
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    D findOrSaveWhere(Map queryMap)

    /**
     * Execute a closure whose first argument is a reference to the current session.
     *
     * @param callable the closure
     * @return The result of the closure
     */
    public <T> T  withSession(Closure<T> callable)

    /**
     * Same as withSession, but present for the case where withSession is overridden to use the Hibernate session
     *
     * @param callable the closure
     * @return The result of the closure
     */
    public <T> T  withDatastoreSession(Closure<T> callable)

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
    public <T> T  withTransaction(Closure<T> callable)

    /**
     * Executes the closure within the context of a new transaction
     *
     * @param callable The closure to call
     * @return The result of the closure execution
     * @see #withTransaction(Closure)
     * @see #withTransaction(Map, Closure)
     * @see #withNewTransaction(Map, Closure)
     */
    public <T> T  withNewTransaction(Closure<T> callable)

    /**
     * Executes the closure within the context of a transaction which is
     * configured with the properties contained in transactionProperties.
     * transactionProperties may contain any properties supported by
     * {@link org.springframework.transaction.support.DefaultTransactionDefinition}.
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
     * @see org.springframework.transaction.support.DefaultTransactionDefinition
     * @see #withNewTransaction(Closure)
     * @see #withNewTransaction(Map, Closure)
     * @see #withTransaction(Closure)
     */
    public <T> T  withTransaction(Map transactionProperties, Closure<T> callable)

    /**
     * Executes the closure within the context of a new transaction which is
     * configured with the properties contained in transactionProperties.
     * transactionProperties may contain any properties supported by
     * {@link org.springframework.transaction.support.DefaultTransactionDefinition}.  Note that if transactionProperties
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
     * @see org.springframework.transaction.support.DefaultTransactionDefinition
     * @see #withNewTransaction(Closure)
     * @see #withTransaction(Closure)
     * @see #withTransaction(Map, Closure)
     */
    public <T> T  withNewTransaction(Map transactionProperties, Closure<T> callable)

    /**
     * Executes the closure within the context of a transaction for the given {@link org.springframework.transaction.TransactionDefinition}
     *
     * @param callable The closure to call
     * @return The result of the closure execution
     */
    public <T> T withTransaction(TransactionDefinition definition, Closure<T> callable)

    /**
     * Creates and binds a new session for the scope of the given closure
     */
    public <T> T  withNewSession(Closure<T> callable)

    /**
     * Creates and binds a new session for the scope of the given closure
     */
    public <T> T withStatelessSession(Closure<T> callable)


    /**
     * Executes a query for the given String
     *
     * @param query The query represented by the given string
     * @return A list of results
     */
    List executeQuery(CharSequence query)

    /**
     * Executes a query for the given String
     *
     * @param query The query represented by the given string
     * @param args The arguments to the query
     *
     * @return A list of results
     *
     */
    List executeQuery(CharSequence query, Map args)

    /**
     * Executes a query for the given String
     *
     * @param query The query represented by the given string
     * @param params The named parameters to the query
     * @param args The arguments to the query
     *
     * @return A list of results
     *
     */
    List executeQuery(CharSequence query, Map params, Map args)

    /**
     * Executes a query for the given String
     *
     * @param query The query represented by the given string
     * @param params The positional parameters to the query
     *
     * @return A list of results
     *
     */
    List executeQuery(CharSequence query, Collection params)

    /**
     * Executes a query for the given String
     *
     * @param query The query represented by the given string
     * @param params The positional parameters to the query
     *
     * @return A list of results
     *
     */
    List executeQuery(CharSequence query, Object...params)

    /**
     * Executes a query for the given String
     *
     * @param query The query represented by the given string
     * @param params The positional parameters to the query
     * @param args The arguments to the query
     *
     * @return A list of results
     *
     */
    List executeQuery(CharSequence query, Collection params, Map args)

    /**
     * Executes an update for the given String
     *
     * @param query The query represented by the given string
     *
     * @return The number of entities updated
     *
     */
    Integer executeUpdate(CharSequence query)

    /**
     * Executes an update for the given String
     *
     * @param query The query represented by the given string
     * @param params The parameters to the query
     *
     * @return The number of entities updated
     *
     */
    Integer executeUpdate(CharSequence query, Map args)

    /**
     * Executes an update for the given String
     *
     * @param query The query represented by the given string
     * @param params The parameters to the query
     * @param args The arguments to the query
     *
     * @return The number of entities updated
     *
     */
    Integer executeUpdate(CharSequence query, Map params, Map args)

    /**
     * Executes an update for the given String
     *
     * @param query The query represented by the given string
     * @param params The positional parameters to the query
     *
     * @return The number of entities updated
     *
     */
    Integer executeUpdate(CharSequence query, Collection params)

    /**
     * Executes an update for the given String
     *
     * @param query The query represented by the given string
     * @param params The positional parameters to the query
     *
     * @return The number of entities updated
     *
     */
    Integer executeUpdate(CharSequence query, Object...params)

    /**
     * Executes an update for the given String
     *
     * @param query The query represented by the given string
     * @param params The positional parameters to the query
     * @param args The arguments to the query
     *
     * @return The number of entities updated
     *
     */
    Integer executeUpdate(CharSequence query, Collection params, Map args)

    /**
     * Finds an object for the given string-based query
     *
     * @param query The query
     * @return The object
     */
    D find(CharSequence query)

    /**
     * Finds an object for the given string-based query and named parameters
     *
     * @param query The query
     * @param params The parameters
     *
     * @return The object
     */
    D find(CharSequence query, Map params)

    /**
     * Finds an object for the given string-based query, named parameters and arguments
     *
     * @param query The query
     * @param params The parameters
     * @params args The arguments
     *
     * @return The object
     */
    D find(CharSequence query, Map params, Map args)

    /**
     * Finds an object for the given string-based query and positional parameters
     *
     * @param query The query
     * @param params The parameters
     *
     * @return The object
     */
    D find(CharSequence query, Collection params)

    /**
     * Finds an object for the given string-based query and positional parameters
     *
     * @param query The query
     * @param params The parameters
     * @params args The arguments
     *
     * @return The object
     */
    D find(CharSequence query, Object[] params)

    /**
     * Finds an object for the given string-based query, positional parameters and arguments
     *
     * @param query The query
     * @param params The parameters
     * @params args The arguments
     *
     * @return The object
     */
    D find(CharSequence query, Collection params, Map args)

    /**
     * Finds all objects for the given string-based query
     *
     * @param query The query
     *
     * @return The object
     */
    List<D> findAll(CharSequence query)

    /**
     * Finds all objects for the given string-based query and named parameters
     *
     * @param query The query
     * @param params The parameters
     *
     * @return The objects
     */
    List<D> findAll(CharSequence query, Map params)

    /**
     * Finds all objects for the given string-based query, named parameters and arguments
     *
     * @param query The query
     * @param params The parameters
     * @params args The arguments
     *
     * @return The objects
     */
    List<D> findAll(CharSequence query, Map params, Map args)

    /**
     * Finds all objects for the given string-based query and positional parameters
     *
     * @param query The query
     * @param params The parameters
     *
     * @return The objects
     */
    List<D> findAll(CharSequence query, Collection params)

    /**
     * Finds all objects for the given string-based query and positional parameters
     *
     * @param query The query
     * @param params The parameters
     *
     * @return The objects
     */
    List<D> findAll(CharSequence query, Object[] params)

    /**
     * Finds all objects for the given string-based query, positional parameters and arguments
     *
     * @param query The query
     * @param params The parameters
     * @params args The arguments
     *
     * @return The objects
     */
    List<D> findAll(CharSequence query, Collection params, Map args)


    /**
     * Execute the closure with the given tenantId
     *
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    def <T> T withTenant(Serializable tenantId, Closure<T> callable)


    /**
     * Execute the closure for each tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    GormAllOperations<D> eachTenant(Closure callable)

    /**
     * Return the {@link GormAllOperations} for the given tenant id
     *
     * @param tenantId The tenant id
     * @return The operations
     */
    GormAllOperations<D> withTenant(Serializable tenantId)
}