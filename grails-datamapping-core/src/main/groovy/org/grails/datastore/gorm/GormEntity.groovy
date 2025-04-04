/*
 * Copyright 2014 the original author or authors.
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

import grails.gorm.DetachedCriteria
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.query.GormQueryOperations
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.query.api.Criteria
import org.grails.datastore.mapping.reflect.EntityReflector
import org.springframework.transaction.TransactionDefinition

import jakarta.persistence.Transient

/**
 *
 * A trait that turns any class into a GORM entity
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 * @since 4.0
 */
@CompileStatic
trait GormEntity<D> implements GormValidateable, DirtyCheckable, GormEntityApi<D> {


    /**
     * Allow access to datasource by name
     *
     * @param instance The instance
     * @param name The property name
     * @return The property value
     */
    @CompileDynamic
    def propertyMissing(String name) {
        GormEnhancer.findInstanceApi(getClass()).propertyMissing(this, name)
    }

    /**
     * Proxy aware instanceOf implementation.
     */
    boolean instanceOf(Class cls) {
        currentGormInstanceApi().instanceOf this, cls
    }

    /**
     * Upgrades an existing persistence instance to a write lock
     * @return The instance
     */
    D lock() {
        currentGormInstanceApi().lock this
    }

    /**
     * Locks the instance for updates for the scope of the passed closure
     *
     * @param callable The closure
     * @return The result of the closure
     */
    def mutex(Closure callable) {
        currentGormInstanceApi().mutex this, callable
    }

    /**
     * Refreshes the state of the current instance
     * @return The instance
     */
    D refresh() {
        currentGormInstanceApi().refresh this
    }

    /**
     * Saves an object the datastore
     * @return Returns the instance
     */
    D save() {
        currentGormInstanceApi().save this
    }

    /**
     * Forces an insert of an object to the datastore
     * @return Returns the instance
     */
    D insert() {
        currentGormInstanceApi().insert this
    }

    /**
     * Forces an insert of an object to the datastore
     * @return Returns the instance
     */
    D insert(Map params) {
        currentGormInstanceApi().insert this, params
    }

    /**
     * Saves an object the datastore
     * @return Returns the instance
     */
    D merge() {
        currentGormInstanceApi().merge this
    }

    /**
     * Saves an object the datastore
     * @return Returns the instance
     */
    D merge(Map params) {
        currentGormInstanceApi().merge this, params
    }

    /**
     * Save method that takes a boolean which indicates whether to perform validation or not
     *
     * @param validate Whether to perform validation
     *
     * @return The instance or null if validation fails
     */
    D save(boolean validate) {
        currentGormInstanceApi().save this, validate
    }

    /**
     * Saves an object with the given parameters
     * @param instance The instance
     * @param params The parameters
     * @return The instance
     */
    D save(Map params) {
        currentGormInstanceApi().save this, params
    }

    /**
     * Returns the objects identifier
     */
    Serializable ident() {
        currentGormInstanceApi().ident this
    }

    /**
     * Attaches an instance to an existing session. Requries a session-based model
     * @return
     */
    D attach() {
        currentGormInstanceApi().attach this
    }

    /**
     * No concept of session-based model so defaults to true
     */
    @Transient
    boolean isAttached() {
        currentGormInstanceApi().isAttached this
    }

    /**
     * Discards any pending changes. Requires a session-based model.
     */
    void discard() {
        currentGormInstanceApi().discard this
    }

    /**
     * Deletes an instance from the datastore
     */
    void delete() {
        currentGormInstanceApi().delete this
    }

    /**
     * Deletes an instance from the datastore
     */
    void delete(Map params) {
        currentGormInstanceApi().delete this, params
    }

    /**
     * Checks whether a field is dirty. Note that unlike {@link #hasChanged()} this method will inspect the
     * persistence context (session) which may or may not contain a more accurate reflection of the initial loaded state
     * depending on the underlying GORM implementation.
     *
     * @param instance The instance
     * @param fieldName The name of the field
     *
     * @return true if the field is dirty
     */
    boolean isDirty(String fieldName) {
        currentGormInstanceApi().isDirty this, fieldName
    }

    /**
     * Checks whether an entity is dirty. Note that unlike {@link #hasChanged()} this method will inspect the
     * persistence context (session) which may or may not contain a more accurate reflection of the initial loaded state
     * depending on the underlying GORM implementation.
     *
     * @param instance The instance
     * @return true if it is dirty
     */
    @Transient
    boolean isDirty() {
        currentGormInstanceApi().isDirty this
    }

    /**
     * Obtains a list of property names that are dirty. Note that unlike {@link #listDirtyPropertyNames()} this method will inspect the
     * persistence context (session) which may or may not contain a more accurate reflection of the initial loaded state
     * depending on the underlying GORM implementation.
     *
     * @param instance The instance
     * @return A list of property names that are dirty
     */
    @Transient
    List getDirtyPropertyNames() {
        currentGormInstanceApi().getDirtyPropertyNames this
    }

    /**
     * Gets the original persisted value of a field. Note that unlike {@link #getOriginalValue(java.lang.String)} this method will inspect the
     * persistence context (session) which may or may not contain a more accurate reflection of the initial loaded state
     * depending on the underlying GORM implementation.
     *
     * @param fieldName The field name
     * @return The original persisted value
     */
    Object getPersistentValue(String fieldName) {
        currentGormInstanceApi().getPersistentValue this, fieldName
    }

    /**
     * Obtains the id of an association without initialising the association
     *
     * @param associationName The association name
     * @return The id of the association or null if it doesn't have one
     */
    Serializable getAssociationId(String associationName) {
        PersistentEntity entity = getGormPersistentEntity()
        MappingContext mappingContext = entity.mappingContext
        EntityReflector entityReflector = mappingContext.getEntityReflector(entity)
        def association = entity.getPropertyByName(associationName)
        if(association instanceof ToOne) {
            def proxyHandler = mappingContext.getProxyHandler()
            def value = entityReflector.getProperty(this, associationName)
            if(proxyHandler.isProxy(value)) {
                return proxyHandler.getIdentifier(value)
            }
            else {
                PersistentEntity associatedEntity = ((ToOne)association).getAssociatedEntity()
                if(associatedEntity != null) {
                    return associatedEntity.getReflector().getIdentifier(value)
                }
            }
        }
        return null
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
                        if (arg instanceof DirtyCheckable) {
                            ((DirtyCheckable) arg).markDirty(otherSide.name)
                        }
                        associationReflector.setProperty(arg, otherSide.name, null)
                    }
                }
            }
            else {
                throw new IllegalArgumentException("")
            }

        }
        return (D)this
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
                    if(obj instanceof DirtyCheckable) {
                        ((DirtyCheckable)obj).markDirty(name)
                    }
                }
            }
            targetObject
        }

        return targetObject
    }

    private MappingContext lookupMappingContext() {
        currentGormStaticApi().datastore.mappingContext
    }


    /**
     * @return The PersistentEntity for this class
     */
    static PersistentEntity getGormPersistentEntity() {
        currentGormStaticApi().persistentEntity
    }

    static List<FinderMethod> getGormDynamicFinders() {
        currentGormStaticApi().gormDynamicFinders
    }
    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance
     */
    static DetachedCriteria<D> where(Closure callable) {
        currentGormStaticApi().where callable
    }

    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance that is lazily initialized
     */
    static DetachedCriteria<D> whereLazy(Closure callable) {
        currentGormStaticApi().whereLazy callable
    }

    /**
     *
     * @param callable Callable closure containing detached criteria definition
     * @return The DetachedCriteria instance
     */
    static DetachedCriteria<D> whereAny(Closure callable) {
        currentGormStaticApi().whereAny callable
    }

    /**
     * Uses detached criteria to build a query and then execute it returning a list
     *
     * @param callable The callable
     * @return A List of entities
     */
    static List<D> findAll(Closure callable) {
        currentGormStaticApi().findAll callable
    }

    /**
     * Uses detached criteria to build a query and then execute it returning a list
     *
     * @param args pagination parameters
     * @param callable The callable
     * @return A List of entities
     */
    static List<D> findAll(Map args, Closure callable) {
        currentGormStaticApi().findAll args, callable
    }

    /**
     * Uses detached criteria to build a query and then execute it returning a list
     *
     * @param callable The callable
     * @return A single entity
     */
    static D find(Closure callable) {
        currentGormStaticApi().find callable
    }


    /**
     * Saves a list of objects in one go
     * @param objectsToSave The objects to save
     * @return A list of object identifiers
     */
    static List<Serializable> saveAll(Object... objectsToSave) {
        currentGormStaticApi().saveAll objectsToSave
    }

    /**
     * Saves a list of objects in one go
     * @param objectToSave Collection of objects to save
     * @return A list of object identifiers
     */
    static List<Serializable> saveAll(Iterable<?> objectsToSave) {
        currentGormStaticApi().saveAll objectsToSave
    }

    /**
     * Deletes a list of objects in one go
     * @param objectsToDelete The objects to delete
     */
    static void deleteAll(Object... objectsToDelete) {
        currentGormStaticApi().deleteAll objectsToDelete
    }

    /**
     * Deletes a list of objects in one go and flushes when param is set
     * @param objectsToDelete The objects to delete
     */
    static void deleteAll(Map params, Object... objectsToDelete) {
        currentGormStaticApi().deleteAll params, objectsToDelete
    }

    /**
     * Deletes a list of objects in one go
     * @param objectsToDelete Collection of objects to delete
     */
    static void deleteAll(Iterable objectToDelete) {
        currentGormStaticApi().deleteAll objectToDelete
    }

    /**
     * Deletes a list of objects in one go and flushes when param is set
     * @param objectsToDelete Collection of objects to delete
     */
    static void deleteAll(Map params, Iterable objectToDelete) {
        currentGormStaticApi().deleteAll params, objectToDelete
    }

    /**
     * Creates an instance of this class
     * @return The created instance
     */
    static D create() {
        currentGormStaticApi().create()
    }

    /**
     * Retrieves an object from the datastore. eg. Book.get(1)
     */
    static D get(Serializable id) {
        currentGormStaticApi().get id
    }

    /**
     * Retrieves an object from the datastore. eg. Book.read(1)
     *
     * Since the datastore abstraction doesn't support dirty checking yet this
     * just delegates to {@link #get(Serializable)}
     */
    static D read(Serializable id) {
        currentGormStaticApi().read id
    }

    /**
     * Retrieves an object from the datastore as a proxy. eg. Book.load(1)
     */
    static D load(Serializable id) {
        currentGormStaticApi().load id
    }

    /**
     * Retrieves an object from the datastore as a proxy. eg. Book.proxy(1)
     */
    static D proxy(Serializable id) {
        currentGormStaticApi().proxy id
    }

    /**
     * Retrieve all the objects for the given identifiers
     * @param ids The identifiers to operate against
     * @return A list of identifiers
     */
    static List<D> getAll(Iterable<Serializable> ids) {
        currentGormStaticApi().getAll ids
    }

    /**
     * Retrieve all the objects for the given identifiers
     * @param ids The identifiers to operate against
     * @return A list of identifiers
     */
    static List<D> getAll(Serializable... ids) {
        currentGormStaticApi().getAll ids
    }

    /**
     * @return Synonym for {@link #list()}
     */
    static List<D> getAll() {
        list()
    }

    /**
     * Creates a criteria builder instance
     */
    static BuildableCriteria createCriteria() {
        currentGormStaticApi().createCriteria()
    }

    /**
     * Creates a criteria builder instance
     */
    static withCriteria(@DelegatesTo(Criteria) Closure callable) {
        currentGormStaticApi().withCriteria callable
    }

    /**
     * Creates a criteria builder instance
     */
    static withCriteria(Map builderArgs, @DelegatesTo(Criteria) Closure callable) {
        currentGormStaticApi().withCriteria builderArgs, callable
    }

    /**
     * Locks an instance for an update
     * @param id The identifier
     * @return The instance
     */
    static D lock(Serializable id) {
        currentGormStaticApi().lock id
    }

    /**
     * Merges an instance with the current session
     * @param d The object to merge
     * @return The instance
     */
    static D merge(D d) {
        currentGormStaticApi().merge d
    }

    /**
     * Counts the number of persisted entities
     * @return The number of persisted entities
     */
    static Integer count() {
        currentGormStaticApi().count()
    }

    /**
     * Same as {@link #count()} but allows property-style syntax (Foo.count)
     */
    static Integer getCount() {
        currentGormStaticApi().getCount()
    }

    /**
     * Checks whether an entity exists
     */
    static boolean exists(Serializable id) {
        currentGormStaticApi().exists id
    }

    /**
     * Lists objects in the datastore. eg. Book.list(max:10)
     *
     * @param params Any parameters such as offset, max etc.
     * @return A list of results
     */
    static List<D> list(Map params) {
        currentGormStaticApi().list params
    }

    /**
     * List all entities
     *
     * @return The list of all entities
     */
    static List<D> list() {
        currentGormStaticApi().list()
    }

    /**
     * The same as {@link #list()}
     *
     * @return The list of all entities
     */
    static List<D> findAll(Map params = Collections.emptyMap()) {
        currentGormStaticApi().findAll params
    }

    /**
     * Finds an object by example
     *
     * @param example The example
     * @return A list of matching results
     */
    static List<D> findAll(D example) {
        currentGormStaticApi().findAll example
    }

    /**
     * Finds an object by example using the given arguments for pagination
     *
     * @param example The example
     * @param args The arguments
     *
     * @return A list of matching results
     */
    static List<D> findAll(D example, Map args) {
        currentGormStaticApi().findAll example, args
    }

    /**
     * Finds the first object using the natural sort order
     *
     * @return the first object in the datastore, null if none exist
     */
    static D first() {
        currentGormStaticApi().first()
    }

    /**
     * Finds the first object sorted by propertyName
     *
     * @param propertyName the name of the property to sort by
     *
     * @return the first object in the datastore sorted by propertyName, null if none exist
     */
    static D first(String propertyName) {
        currentGormStaticApi().first propertyName
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
    static D first(Map queryParams) {
        currentGormStaticApi().first queryParams
    }

    /**
     * Finds the last object using the natural sort order
     *
     * @return the last object in the datastore, null if none exist
     */
    static D last() {
        currentGormStaticApi().last()
    }

    /**
     * Finds the last object sorted by propertyName
     *
     * @param propertyName the name of the property to sort by
     *
     * @return the last object in the datastore sorted by propertyName, null if none exist
     */
    static D last(String propertyName) {
        currentGormStaticApi().last propertyName
    }

    /**
     * Handles static method missing for dynamic finders
     *
     * @param methodName The name of the method
     * @param arg the argument to the method
     * @return The value
     */
    static Object staticMethodMissing(String methodName, arg) {
        currentGormStaticApi().methodMissing(methodName, arg)
    }

    /**
     * Handles property missing, does nothing by default, sub traits to override
     *
     * @param property The property
     * @return The value if an exception if the property doesn't exist
     */
    static Object staticPropertyMissing(String property) {
        try {
            currentGormStaticApi().propertyMissing(property)
        } catch (IllegalStateException e) {
            throw new MissingPropertyException(property, this)
        }
    }

    /**
     * Handles property missing, does nothing by default, sub traits to override
     *
     * @param property The property
     * @param value The value of the property
     * @return The value if an exception if the property doesn't exist
     */
    static void staticPropertyMissing(String property, value) {
        try {
            currentGormStaticApi().propertyMissing(property, value)
        } catch (IllegalStateException e) {
            throw new MissingPropertyException(property, this)
        }
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
    static D last(Map queryParams) {
        currentGormStaticApi().last queryParams
    }

    /**
     * Finds all results matching all of the given conditions. Eg. Book.findAllWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @return A list of results
     */
    static List<D> findAllWhere(Map queryMap) {
        currentGormStaticApi().findAllWhere queryMap
    }

    /**
     * Finds all results matching all of the given conditions. Eg. Book.findAllWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @param args The Query arguments
     *
     * @return A list of results
     */
    static List<D> findAllWhere(Map queryMap, Map args) {
        currentGormStaticApi().findAllWhere queryMap, args
    }

    /**
     * Finds an object by example
     *
     * @param example The example
     * @return A list of matching results
     */
    static D find(D example) {
        currentGormStaticApi().find example
    }

    /**
     * Finds an object by example using the given arguments for pagination
     *
     * @param example The example
     * @param args The arguments
     *
     * @return A list of matching results
     */
    static D find(D example, Map args) {
        currentGormStaticApi().find example, args
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    static D findWhere(Map queryMap) {
        currentGormStaticApi().findWhere queryMap
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand")
     *
     * @param queryMap The map of conditions
     * @param args The Query arguments
     *
     * @return A single result
     */
    static D findWhere(Map queryMap, Map args) {
        currentGormStaticApi().findWhere queryMap, args
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand").  If
     * a matching persistent entity is not found a new entity is created and returned.
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    static D findOrCreateWhere(Map queryMap) {
        currentGormStaticApi().findOrCreateWhere queryMap
    }

    /**
     * Finds a single result matching all of the given conditions. Eg. Book.findWhere(author:"Stephen King", title:"The Stand").  If
     * a matching persistent entity is not found a new entity is created, saved and returned.
     *
     * @param queryMap The map of conditions
     * @return A single result
     */
    static D findOrSaveWhere(Map queryMap) {
        currentGormStaticApi().findOrSaveWhere queryMap
    }

    /**
     * Execute a closure whose first argument is a reference to the current session.
     *
     * @param callable the closure
     * @return The result of the closure
     */
    static <T> T withSession(Closure<T> callable) {
        currentGormStaticApi().withSession callable
    }

    /**
     * Same as withSession, but present for the case where withSession is overridden to use the Hibernate session
     *
     * @param callable the closure
     * @return The result of the closure
     */
    static <T> T withDatastoreSession(Closure<T> callable) {
        currentGormStaticApi().withDatastoreSession callable
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
    static <T> T withTransaction(Closure<T> callable) {
        currentGormStaticApi().withTransaction callable
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
    static <T> T withNewTransaction(Closure<T> callable) {
        currentGormStaticApi().withNewTransaction callable
    }

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
    static <T> T withTransaction(Map transactionProperties, Closure<T> callable) {
        currentGormStaticApi().withTransaction transactionProperties, callable
    }

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
    static <T> T withNewTransaction(Map transactionProperties, Closure<T> callable) {
        currentGormStaticApi().withNewTransaction transactionProperties, callable
    }

    /**
     * Executes the closure within the context of a transaction for the given {@link org.springframework.transaction.TransactionDefinition}
     *
     * @param callable The closure to call
     * @return The result of the closure execution
     */
    static <T> T withTransaction(TransactionDefinition definition, Closure<T> callable) {
        currentGormStaticApi().withTransaction definition, callable
    }

    /**
     * Creates and binds a new session for the scope of the given closure
     */
    static <T> T withNewSession(Closure<T> callable) {
        currentGormStaticApi().withNewSession callable
    }

    /**
     * Creates and binds a new session for the scope of the given closure
     */
    static <T> T withStatelessSession(Closure<T> callable) {
        currentGormStaticApi().withStatelessSession callable
    }


    /**
     * Executes a query for the given String
     *
     * @param query The query represented by the given string
     * @return A list of results
     */
    static List executeQuery(CharSequence query) {
        currentGormStaticApi().executeQuery query
    }

    /**
     * Executes a query for the given String
     *
     * @param query The query represented by the given string
     * @param args The arguments to the query
     *
     * @return A list of results
     *
     */
    static List executeQuery(CharSequence query, Map args) {
        currentGormStaticApi().executeQuery query, args
    }

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
    static List executeQuery(CharSequence query, Map params, Map args) {
        currentGormStaticApi().executeQuery query, params, args
    }

    /**
     * Executes a query for the given String
     *
     * @param query The query represented by the given string
     * @param params The positional parameters to the query
     *
     * @return A list of results
     *
     */
    static List executeQuery(CharSequence query, Collection params) {
        currentGormStaticApi().executeQuery query, params
    }

    /**
     * Executes a query for the given String
     *
     * @param query The query represented by the given string
     * @param params The positional parameters to the query
     *
     * @return A list of results
     *
     */
    static List executeQuery(CharSequence query, Object...params) {
        currentGormStaticApi().executeQuery query, params
    }

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
    static List executeQuery(CharSequence query, Collection params, Map args) {
        currentGormStaticApi().executeQuery query, params, args
    }

    /**
     * Executes an update for the given String
     *
     * @param query The query represented by the given string
     *
     * @return The number of entities updated
     *
     */
    static Integer executeUpdate(CharSequence query) {
        currentGormStaticApi().executeUpdate query
    }

    /**
     * Executes an update for the given String
     *
     * @param query The query represented by the given string
     * @param params The parameters to the query
     *
     * @return The number of entities updated
     *
     */
    static Integer executeUpdate(CharSequence query, Map args) {
        currentGormStaticApi().executeUpdate query, args
    }

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
    static Integer executeUpdate(CharSequence query, Map params, Map args) {
        currentGormStaticApi().executeUpdate query, params, args
    }

    /**
     * Executes an update for the given String
     *
     * @param query The query represented by the given string
     * @param params The positional parameters to the query
     *
     * @return The number of entities updated
     *
     */
    static Integer executeUpdate(CharSequence query, Collection params) {
        currentGormStaticApi().executeUpdate query, params
    }

    /**
     * Executes an update for the given String
     *
     * @param query The query represented by the given string
     * @param params The positional parameters to the query
     *
     * @return The number of entities updated
     *
     */
    static Integer executeUpdate(CharSequence query, Object...params) {
        currentGormStaticApi().executeUpdate query, params
    }

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
    static Integer executeUpdate(CharSequence query, Collection params, Map args) {
        currentGormStaticApi().executeUpdate query, params, args
    }

    /**
     * Finds an object for the given string-based query
     *
     * @param query The query
     * @return The object
     */
    static D find(CharSequence query) {
        currentGormStaticApi().find query
    }

    /**
     * Finds an object for the given string-based query and named parameters
     *
     * @param query The query
     * @param params The parameters
     *
     * @return The object
     */
    static D find(CharSequence query, Map params) {
        currentGormStaticApi().find query, params
    }

    /**
     * Finds an object for the given string-based query, named parameters and arguments
     *
     * @param query The query
     * @param params The parameters
     * @params args The arguments
     *
     * @return The object
     */
    static D find(CharSequence query, Map params, Map args) {
        currentGormStaticApi().find query, params, args
    }

    /**
     * Finds an object for the given string-based query and positional parameters
     *
     * @param query The query
     * @param params The parameters
     *
     * @return The object
     */
    static D find(CharSequence query, Collection params) {
        currentGormStaticApi().find query, params
    }

    /**
     * Finds an object for the given string-based query and positional parameters
     *
     * @param query The query
     * @param params The parameters
     * @params args The arguments
     *
     * @return The object
     */
    static D find(CharSequence query, Object[] params) {
        currentGormStaticApi().find query, params
    }

    /**
     * Finds an object for the given string-based query, positional parameters and arguments
     *
     * @param query The query
     * @param params The parameters
     * @params args The arguments
     *
     * @return The object
     */
    static D find(CharSequence query, Collection params, Map args) {
        currentGormStaticApi().find query, params, args
    }

    /**
     * Finds all objects for the given string-based query
     *
     * @param query The query
     *
     * @return The object
     */
    static List<D> findAll(CharSequence query) {
        currentGormStaticApi().findAll query
    }

    /**
     * Finds all objects for the given string-based query and named parameters
     *
     * @param query The query
     * @param params The parameters
     *
     * @return The objects
     */
    static List<D> findAll(CharSequence query, Map params) {
        currentGormStaticApi().findAll query, params
    }

    /**
     * Finds all objects for the given string-based query, named parameters and arguments
     *
     * @param query The query
     * @param params The parameters
     * @params args The arguments
     *
     * @return The objects
     */
    static List<D> findAll(CharSequence query, Map params, Map args) {
        currentGormStaticApi().findAll query, params, args
    }

    /**
     * Finds all objects for the given string-based query and positional parameters
     *
     * @param query The query
     * @param params The parameters
     *
     * @return The objects
     */
    static List<D> findAll(CharSequence query, Collection params) {
        currentGormStaticApi().findAll query, params
    }

    /**
     * Finds all objects for the given string-based query and positional parameters
     *
     * @param query The query
     * @param params The parameters
     *
     * @return The objects
     */
    static List<D> findAll(CharSequence query, Object[] params) {
        currentGormStaticApi().findAll query, params
    }

    /**
     * Finds all objects for the given string-based query, positional parameters and arguments
     *
     * @param query The query
     * @param params The parameters
     * @params args The arguments
     *
     * @return The objects
     */
    static List<D> findAll(CharSequence query, Collection params, Map args) {
        currentGormStaticApi().findAll query, params, args
    }

    /**
     * Looks up a named query
     *
     * @param queryName The name of the query
     * @return The query or null
     *
     * @deprecated Named queries are deprecated, use where queries instead
     */
    @Deprecated
    static GormQueryOperations<D> getNamedQuery(String queryName) {
        GormEnhancer.createNamedQuery(this, queryName)
    }
    /**
     * Looks up a named query
     *
     * @param queryName The name of the query
     * @return The query or null
     *
     * @deprecated Named queries are deprecated, use where queries instead
     */
    @Deprecated
    static GormQueryOperations<D> getNamedQuery(String queryName, Object...args) {
        GormEnhancer.createNamedQuery(this, queryName, args)
    }

    private GormInstanceApi<D> currentGormInstanceApi() {
        (GormInstanceApi<D>)GormEnhancer.findInstanceApi(getClass())
    }

    private static GormStaticApi<D> currentGormStaticApi() {
        (GormStaticApi<D>)GormEnhancer.findStaticApi(this)
    }
}
