package grails.gorm.rx

import grails.gorm.rx.api.RxGormAllOperations
import grails.gorm.rx.api.RxGormInstanceOperations
import grails.gorm.rx.api.RxGormOperations
import grails.gorm.rx.api.RxGormStaticOperations
import grails.gorm.rx.proxy.ObservableProxy
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.validation.ValidationException
import org.grails.gorm.rx.api.RxGormEnhancer
import org.grails.gorm.rx.api.RxGormInstanceApi
import org.grails.gorm.rx.api.RxGormStaticApi
import rx.Observable
import rx.Subscriber

/**
 * Represents a reactive GORM entity
 *
 * @author Graeme Rocher
 * @since 6.0
 *
 * @param <D> The entity type
 */
@CompileStatic
trait RxEntity<D> implements RxGormOperations<D>, GormValidateable, DirtyCheckable, Serializable {
    @Override
    boolean validate(Map arguments) {
        RxGormEnhancer.findValidationApi((Class<D>)getClass()).validate((D)this, arguments)
    }

    @Override
    boolean validate(List fields) {
        RxGormEnhancer.findValidationApi((Class<D>)getClass()).validate((D)this, fields)
    }

    @Override
    boolean validate() {
        RxGormEnhancer.findValidationApi((Class<D>)getClass()).validate((D)this)
    }

    @Override
    Observable<D> insert(Map arguments = Collections.emptyMap()) {
        return doSave(arguments, true)
    }
    /**
     * Save an instance and return an observable
     *
     * @return An observable
     */
    Observable<D> save() {
        save(Collections.emptyMap())
    }

    /**
     * Save an instance and return an observable
     *
     * @return An observable
     */
    Observable<D> save(Map arguments) {
        return doSave(arguments, false)
    }

    private Observable<D> doSave(Map arguments, boolean isInsert) {
        boolean shouldValidate = arguments?.containsKey("validate") ? arguments.validate : true
        if (shouldValidate) {
            def hasErrors = !validate()
            if (hasErrors) {
                throw ValidationException.newInstance("Validation error occurred during call to save() for entity [$this]", errors)
            } else {
                if (isInsert) {
                    return currentRxGormInstanceApi().insert(this, arguments)
                } else {
                    return currentRxGormInstanceApi().save(this, arguments)
                }

            }
        } else {
            skipValidation(true)
            clearErrors()
            if (isInsert) {
                return currentRxGormInstanceApi().insert(this, arguments)
            } else {
                return currentRxGormInstanceApi().save(this, arguments)
            }
        }
    }

    /**
     * Returns the objects identifier
     */
    Serializable ident() {
        currentRxGormInstanceApi().ident this
    }

    /**
     * Deletes an entity
     *
     * @return An observable that returns a boolean true if successful
     */
    @Override
    Observable<Boolean> delete(Map arguments = Collections.emptyMap()) {
        currentRxGormInstanceApi().delete this, arguments
    }


    /**
     * Checks whether a field is dirty
     *
     * @param instance The instance
     * @param fieldName The name of the field
     *
     * @return true if the field is dirty
     */
    boolean isDirty(String fieldName) {
        hasChanged(fieldName)
    }

    /**
     * Checks whether an entity is dirty
     *
     * @param instance The instance
     * @return true if it is dirty
     */
    boolean isDirty() {
        hasChanged()
    }

    /**
     * Removes the given value to given association ensuring both sides are correctly disassociated
     *
     * @param associationName The association name
     * @param arg The value
     * @return This domain instance
     */
    D removeFrom(String associationName, Object arg) {
        final PersistentEntity entity = getGormPersistentEntity()
        def prop = entity.getPropertyByName(associationName)
        final MappingContext mappingContext = entity.mappingContext
        final EntityReflector entityReflector = mappingContext.getEntityReflector(entity)

        if(prop instanceof Association) {
            Association association = (Association)prop
            Class javaClass = association.associatedEntity?.javaClass
            final boolean isBasic = association instanceof Basic
            if(isBasic) {
                javaClass = ((Basic)association).componentType
            }

            if (javaClass.isInstance(arg)) {
                final propertyName = prop.name

                Collection currentValue = (Collection)entityReflector.getProperty(this, propertyName)
                currentValue?.remove(arg)
                markDirty(propertyName)

                if (association.bidirectional) {
                    def otherSide = association.inverseSide
                    def associationReflector = mappingContext.getEntityReflector(association.associatedEntity)
                    if (otherSide instanceof ManyToMany) {
                        Collection otherSideValue = (Collection) associationReflector.getProperty(arg, otherSide.name)
                        otherSideValue?.remove(this)

                    }
                    else {
                        associationReflector.setProperty(arg, otherSide.name, null)
                    }
                }
            }
            else {
                throw new IllegalArgumentException("Argument is not an instance of $javaClass")
            }

        }
        return (D)this
    }

    /**
     * Obtains the id of an association without initialising the association
     *
     * @param associationName The association name
     * @return The id of the association or null if it doesn't have one
     */
    Serializable getAssociationId(String associationName) {
        PersistentEntity entity = getGormPersistentEntity()
        def association = entity.getPropertyByName(associationName)
        if(association instanceof ToOne) {
            MappingContext mappingContext = currentRxGormStaticApi().datastoreClient.mappingContext
            def proxyHandler = mappingContext.getProxyHandler()
            def entityReflector = mappingContext.getEntityReflector(entity)
            def value = entityReflector.getProperty(this, associationName)
            if(value != null) {

                if(proxyHandler.isProxy(value)) {
                    return proxyHandler.getIdentifier(value)
                }
                else {
                    return mappingContext.getEntityReflector(association.associatedEntity).getIdentifier(value)
                }
            }
        }
        return null
    }
    /**
     * Adds the given value to given association ensuring both sides are correctly associated
     *
     * @param associationName The association name
     * @param arg The value
     * @return This domain instance
     */
    D addTo(String associationName, Object arg) {
        final PersistentEntity entity = getGormPersistentEntity()
        final def prop = entity.getPropertyByName(associationName)
        final D targetObject = (D)this

        final MappingContext mappingContext = entity.mappingContext
        final EntityReflector reflector = mappingContext.getEntityReflector(entity)
        if(reflector != null && (prop instanceof Association)) {

            final Association association = (Association)prop
            final propertyName = association.name

            def obj
            def currentValue = reflector.getProperty(targetObject, propertyName)
            if (currentValue == null) {
                currentValue = [].asType(prop.type)
                reflector.setProperty(targetObject, propertyName, currentValue)
            }

            Class javaClass = association.associatedEntity?.javaClass
            final boolean isBasic = association instanceof Basic
            if(isBasic) {
                javaClass = ((Basic)association).componentType
            }

            if (arg instanceof Map) {
                obj = javaClass.newInstance(arg)
            }
            else if (javaClass.isInstance(arg)) {
                obj = arg
            }
            else {
                def conversionService = mappingContext.conversionService
                if(conversionService.canConvert(arg.getClass(), javaClass)) {
                    obj = conversionService.convert(arg, javaClass)
                }
                else {
                    throw new IllegalArgumentException("Cannot add value [$arg] to collection [$propertyName] with type [$javaClass.name]")
                }
            }

            def coll = (Collection)currentValue
            coll.add(obj)
            markDirty(propertyName)

            if (isBasic) {
                return targetObject
            }

            if (association.bidirectional && association.inverseSide) {
                def otherSide = association.inverseSide
                String name = otherSide.name
                def associationReflector = mappingContext.getEntityReflector(association.associatedEntity)
                if (otherSide instanceof OneToMany || otherSide instanceof ManyToMany) {

                    Collection otherSideValue = (Collection)associationReflector.getProperty(obj, name)
                    if (otherSideValue == null) {
                        otherSideValue =  (Collection)( [].asType(otherSide.type) )
                        associationReflector.setProperty(obj, name, otherSideValue)
                    }
                    otherSideValue.add(targetObject)
                    if(obj instanceof DirtyCheckable) {
                        ((DirtyCheckable)obj).markDirty(name)
                    }
                }

                else {
                    associationReflector?.setProperty(obj, name, targetObject)
                }
            }
            targetObject
        }

        return targetObject
    }

    /**
     * @return A new instance of this RxEntity
     */
    static D create() {
        (D)this.newInstance()
    }

    /**
     * Retrieve an instance by id
     *
     * @param id The id of the instance
     * @return An observable
     */
    static Observable<D> get(Serializable id, Map args = Collections.emptyMap()) {
        currentRxGormStaticApi().get(id, args)
    }

    /**
     * Obtain a proxy to the given instance
     *
     * @param id The id of the instance
     * @return An observable
     */
    static ObservableProxy<D> proxy(Serializable id, Map args = Collections.emptyMap()) {
        currentRxGormStaticApi().proxy(id, args)
    }


    /**
     * Obtain a proxy to the given instance
     *
     * @param query The query that returns the instance
     * @return An observable
     */
    static ObservableProxy<D> proxy(DetachedCriteria<D> query, Map args = Collections.emptyMap()) {
        currentRxGormStaticApi().proxy(query, args)
    }
    /**
     * @return Counts the number of instances
     */
    static Observable<Number> count() {
        currentRxGormStaticApi().count()
    }

    /**
     * Batch deletes a number of objects in one go
     *
     * @param objects The objects to delete
     * @return The number of objects actually deleted
     */
    static Observable<Number> deleteAll(D...objects) {
        deleteAll( (Iterable<D>)Arrays.asList(objects) )
    }

    /**
     * Batch deletes a number of objects in one go
     *
     * @param objects The objects to delete
     * @return The number of objects actually deleted
     */
    static Observable<Number> deleteAll(Iterable<D> objects) {
        currentRxGormStaticApi().deleteAll(objects)
    }

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    static Observable<List<Serializable>> saveAll(Iterable<D> objects, Map arguments = Collections.emptyMap()) {
        currentRxGormStaticApi().saveAll(objects, arguments)
    }

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    static Observable<List<Serializable>> saveAll(D... objects) {
        saveAll((Iterable<D>)Arrays.asList(objects))
    }

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    static Observable<List<Serializable>> insertAll(Iterable<D> objects, Map arguments = Collections.emptyMap()) {
        currentRxGormStaticApi().insertAll(objects, arguments)
    }

    /**
     * Batch saves all of the given objects
     *
     * @param objects The objects to save
     * @return An observable that emits the identifiers of the saved objects
     */
    static Observable<List<Serializable>> insertAll(D... objects) {
        insertAll((Iterable<D>)Arrays.asList(objects))
    }

    /**
     * Check whether an entity exists for the given id
     *
     * @param id
     * @return
     */
    static Observable<Boolean> exists(Serializable id) {
        get(id).map { D o ->
            o != null
        }.switchIfEmpty(Observable.create( { Subscriber s ->
            s.onNext(false)
        } as Observable.OnSubscribe))
    }

    /**
     * Finds the first object using the natural sort order
     *
     * @return A single that will emit the first object, if it exists
     */
    static Observable<D> first() {
        currentRxGormStaticApi().first()
    }

    /**
     * Finds the first object sorted by propertyName
     *
     * @param propertyName the name of the property to sort by
     *
     * @return A single that will emit the first object, if it exists
     */
    static Observable<D> first(String propertyName) {
        currentRxGormStaticApi().first propertyName
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
    static Observable<D> first(Map queryParams) {
        currentRxGormStaticApi().first queryParams
    }

    /**
     * Finds the last object using the natural sort order
     *
     * @return A single that will emit the last object, if it exists
     */
    static Observable<D> last() {
        currentRxGormStaticApi().last()
    }

    /**
     * Finds the last object sorted by propertyName
     *
     * @param propertyName the name of the property to sort by
     *
     * @return A single that will emit the last object, if it exists
     */
    static Observable<D> last(String propertyName) {
        currentRxGormStaticApi().last propertyName
    }

    /**
     * Finds the last object.  If queryParams includes 'sort', that will
     * dictate the sort order, otherwise natural sort order will be used.
     * queryParams may include any of the same parameters that might be passed
     * to the list(Map) method.  This method will ignore 'order' and 'max' as
     * those are always 'desc' and 1, respectively.
     *
     * @return A single that will emit the last object, if it exists
     */
    static Observable<D> last(Map<String,Object> params) {
        currentRxGormStaticApi().last params
    }



    /**
     * List all entities and return an observable
     *
     * @return An observable with all results
     */
    static Observable<List<D>> list() {
        currentRxGormStaticApi().list()
    }

    /**
     * List all entities and return an observable
     *
     * @return An observable with all results
     */
    static Observable<List<D>> list(Map args) {
        currentRxGormStaticApi().list(args)
    }

    /**
     * List all entities and return an observable
     *
     * @return An observable with all results
     */
    static Observable<D> findAll() {
        findAll(Collections.<String, Object> emptyMap())
    }

    /**
     * List all entities and return an observable
     *
     * @return An observable with all results
     */
    static Observable<D> findAll(Map args) {
        currentRxGormStaticApi().findAll(args)
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    static Observable<D> findWhere(Map queryMap) {
        currentRxGormStaticApi().findWhere queryMap
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @param args The Query arguments
     *
     * @return A single result
     */
    static Observable<D> findWhere(Map queryMap, Map args) {
        currentRxGormStaticApi().findWhere queryMap, args
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand").  If
     * a matching persistent entity is not found a new entity is created and returned.
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    static Observable<D> findOrCreateWhere(Map queryMap) {
        currentRxGormStaticApi().findOrCreateWhere queryMap
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand").  If
     * a matching persistent entity is not found a new entity is created, saved and returned.
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    static Observable<D> findOrSaveWhere(Map queryMap) {
        currentRxGormStaticApi().findOrSaveWhere queryMap
    }

    /**
     * Finds all results matching all of the given conditions. Eg. Book.findAllWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @return A list of results
     */
    static Observable<D> findAllWhere(Map queryMap) {
        currentRxGormStaticApi().findAllWhere queryMap
    }

    /**
     * Finds all results matching all of the given conditions. Eg. Book.findAllWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @param args The Query arguments
     *
     * @return A list of results
     */
    static Observable<D> findAllWhere(Map queryMap, Map args) {
        currentRxGormStaticApi().findAllWhere queryMap, args
    }

    /**
     * Uses detached criteria to build a query and then execute it returning an observable
     *
     * @param callable The callable
     * @return The observable
     */
    static Observable<D> findAll(@DelegatesTo(DetachedCriteria) Closure callable) {
        currentRxGormStaticApi().findAll callable
    }

    /**
     * Uses detached criteria to build a query and then execute it returning an observable
     *
     * @param callable The callable
     * @return The observable
     */
    static Observable<D> find(@DelegatesTo(DetachedCriteria) Closure callable) {
        currentRxGormStaticApi().find callable
    }
    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance
     */
    static DetachedCriteria<D> where(@DelegatesTo(DetachedCriteria) Closure callable) {
        currentRxGormStaticApi().where callable
    }

    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance that is lazily initialized
     */
    static DetachedCriteria<D> whereLazy(@DelegatesTo(DetachedCriteria) Closure callable) {
        currentRxGormStaticApi().whereLazy callable
    }

    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance
     */
    static DetachedCriteria<D> whereAny(@DelegatesTo(DetachedCriteria) Closure callable) {
        currentRxGormStaticApi().whereAny callable
    }

    /**
     * Creates a criteria builder instance
     */
    static CriteriaBuilder<D> createCriteria() {
        currentRxGormStaticApi().createCriteria()
    }

    /**
     * Creates a criteria builder instance
     */
    static Observable withCriteria(@DelegatesTo(CriteriaBuilder) Closure callable) {
        currentRxGormStaticApi().withCriteria callable
    }

    /**
     * Creates a criteria builder instance
     */
    static Observable withCriteria(Map builderArgs, @DelegatesTo(CriteriaBuilder) Closure callable) {
        currentRxGormStaticApi().withCriteria builderArgs, callable
    }

    /**
     * Perform operations with the given connection
     *
     * @param connectionName The name of the connection
     * @return The {@link RxGormStaticOperations}    instance
     */
    RxGormAllOperations<D> withConnection(String connectionName) {
        return (RxGormAllOperations<D>)RxGormEnhancer.findStaticApi(getClass(), connectionName)
    }

    /**
     * Switches to given named connection within the context of the closure. The delegate of the closure is used to resolve
     * operations against the connection.
     *
     * @param connectionName The name of the connection
     * @param callable The closure
     * @return
     */
    static <T> T withConnection(String connectionName, @DelegatesTo(RxGormAllOperations) Closure<T> callable ) {
        def staticOperations = (RxGormAllOperations<D>) RxGormEnhancer.findStaticApi(this, connectionName)
        callable.setDelegate(staticOperations)
        return callable.call()
    }

    /**
     * Handles dynamic finders
     *
     * @param methodName The method name
     * @param arg The argument to the method
     *
     * @return An observable with the result
     */
    static Observable<D> staticMethodMissing(String methodName, arg) {
        currentRxGormStaticApi().methodMissing(methodName, arg)
    }

    static Object staticPropertyMissing(String property) {
        currentRxGormStaticApi().propertyMissing(property)
    }

    /**
     * @return The dynamic finders for this domain class
     */
    static List<FinderMethod> getGormDynamicFinders() {
        currentRxGormStaticApi().gormDynamicFinders
    }

    static PersistentEntity getGormPersistentEntity() {
        currentRxGormStaticApi().entity
    }

    private RxGormInstanceApi<D> currentRxGormInstanceApi() {
        (RxGormInstanceApi<D>)RxGormEnhancer.findInstanceApi(this.getClass())
    }

    private static RxGormStaticApi<D> currentRxGormStaticApi() {
        (RxGormStaticApi<D>)RxGormEnhancer.findStaticApi(this)
    }
}
