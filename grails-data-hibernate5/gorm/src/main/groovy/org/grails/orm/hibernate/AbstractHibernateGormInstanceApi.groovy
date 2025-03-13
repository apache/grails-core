/*
 * Copyright 2013 the original author or authors.
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
package org.grails.orm.hibernate

import grails.gorm.validation.CascadingValidator
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.support.HibernateRuntimeUtils
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToOne
import org.hibernate.*
import org.springframework.beans.BeanWrapperImpl
import org.springframework.beans.InvalidPropertyException
import org.springframework.dao.DataAccessException
import org.springframework.validation.Errors
import org.springframework.validation.Validator


/**
 * Abstract extension of the {@link GormInstanceApi} class that provides common logic shared by Hibernate 3 and Hibernate 4
 *
 * @author Graeme Rocher
 * @param <D>
 */
@CompileStatic
abstract class AbstractHibernateGormInstanceApi<D> extends GormInstanceApi<D> {
    private static final String ARGUMENT_VALIDATE = "validate"
    private static final String ARGUMENT_DEEP_VALIDATE = "deepValidate"
    private static final String ARGUMENT_FLUSH = "flush"
    private static final String ARGUMENT_INSERT = "insert"
    private static final String ARGUMENT_MERGE = "merge"
    private static final String ARGUMENT_FAIL_ON_ERROR = "failOnError"
    private static final Class DEFERRED_BINDING

    static {
        try {
            DEFERRED_BINDING = Class.forName('grails.validation.DeferredBindingActions')
        } catch (Throwable e) {
            DEFERRED_BINDING = null
        }
    }

    protected static final Object[] EMPTY_ARRAY = []
    /**
     * When a domain instance is saved without validation, we put it
     * into this thread local variable. Any code that needs to know
     * whether the domain instance should be validated can just check
     * the value. Note that this only works because the session is
     * flushed when a domain instance is saved without validation.
     */
    static final ThreadLocal<Boolean> insertActiveThreadLocal = new ThreadLocal<Boolean>()



    protected SessionFactory sessionFactory
    protected ClassLoader classLoader
    protected IHibernateTemplate hibernateTemplate
    protected ProxyHandler proxyHandler

    boolean autoFlush

    protected AbstractHibernateGormInstanceApi(Class<D> persistentClass, AbstractHibernateDatastore datastore, ClassLoader classLoader, IHibernateTemplate hibernateTemplate) {
        super(persistentClass, datastore)
        this.classLoader = classLoader
        sessionFactory = datastore.getSessionFactory()
        this.hibernateTemplate = hibernateTemplate
        this.proxyHandler = datastore.mappingContext.getProxyHandler()
        this.autoFlush = datastore.autoFlush
        this.failOnError = datastore.failOnError
        this.markDirty = datastore.markDirty
    }

    @Override
    D save(D target, Map arguments) {

        PersistentEntity domainClass = persistentEntity
        runDeferredBinding()
        boolean shouldFlush = shouldFlush(arguments)
        boolean shouldValidate = shouldValidate(arguments, persistentEntity)

        HibernateRuntimeUtils.autoAssociateBidirectionalOneToOnes(domainClass, target)

        boolean deepValidate = true
        if (arguments?.containsKey(ARGUMENT_DEEP_VALIDATE)) {
            deepValidate = ClassUtils.getBooleanFromMap(ARGUMENT_DEEP_VALIDATE, arguments)
        }

        if (shouldValidate) {
            Validator validator = datastore.mappingContext.getEntityValidator(domainClass)

            Errors errors = HibernateRuntimeUtils.setupErrorsProperty(target)

            if (validator) {
                datastore.applicationEventPublisher?.publishEvent new ValidationEvent(datastore, target)

                if (validator instanceof CascadingValidator) {
                    ((CascadingValidator)validator).validate target, errors, deepValidate
                } else if (validator instanceof org.grails.datastore.gorm.validation.CascadingValidator) {
                    ((org.grails.datastore.gorm.validation.CascadingValidator) validator).validate target, errors, deepValidate
                } else {
                    validator.validate target, errors
                }

                if (errors.hasErrors()) {
                    handleValidationError(domainClass,target,errors)
                    if (shouldFail(arguments)) {
                        throw validationException.newInstance("Validation Error(s) occurred during save()", errors)
                    }
                    return null
                }

                setObjectToReadWrite(target)
            }
        }

        // this piece of code will retrieve a persistent instant
        // of a domain class property is only the id is set thus
        // relieving this burden off the developer
        autoRetrieveAssociations datastore, domainClass, target

        // Once we get here we've either validated this object or skipped validation, either way
        // we don't need to validate again for the rest of this save.
        GormValidateable validateable = (GormValidateable) target
        validateable.skipValidation(true)

        try {
            if (shouldInsert(arguments)) {
                return performInsert(target, shouldFlush)
            }
            else if(shouldMerge(arguments)) {
                return performMerge(target, shouldFlush)
            }
            else {
                if (target instanceof DirtyCheckable && markDirty) {
                    target.markDirty()
                }
                return performSave(target, shouldFlush)
            }
        } finally {
            // After save, we have to make sure this entity is setup to validate again. It's possible it will
            // be validated again if this save didn't flush, but without checking it's dirty state we can't really
            // know for sure that it hasn't changed and need to err on the side of caution.
            validateable.skipValidation(false)
        }
    }

    @CompileDynamic
    private void runDeferredBinding() {
        DEFERRED_BINDING?.runActions()
    }

    @Override
    D merge(D instance, Map params) {
        Map args = new HashMap(params)
        args[ARGUMENT_MERGE] = true
        return save(instance, args)
    }

    @Override
    D insert(D instance, Map params) {
        Map args = new HashMap(params)
        args[ARGUMENT_INSERT] = true
        return save(instance, args)
    }

    @Override
    void discard(D instance) {
        hibernateTemplate.evict instance
    }

    @Override
    void delete(D instance, Map params = Collections.emptyMap()) {
        boolean flush = shouldFlush(params)
        try {
            hibernateTemplate.execute { Session session ->
                session.delete instance
                if(flush) {
                    session.flush()
                }
            }
        }
        catch (DataAccessException e) {
            try {
                hibernateTemplate.execute { Session session ->
                    session.flushMode = FlushMode.MANUAL
                }
            }
            finally {
                throw e
            }
        }
    }

    @Override
    boolean isAttached(D instance) {
        hibernateTemplate.contains instance
    }

    @Override
    boolean instanceOf(D instance, Class cls) {
        return proxyHandler.unwrap(instance) in cls
    }

    @Override
    D lock(D instance) {
        hibernateTemplate.lock(instance, LockMode.PESSIMISTIC_WRITE)
        instance
    }

    @Override
    D attach(D instance) {
        hibernateTemplate.lock(instance, LockMode.NONE)
        return instance
    }

    @Override
    D refresh(D instance) {
        hibernateTemplate.refresh(instance)
        return instance
    }

    protected D performSave(final D target, final boolean flush) {
        hibernateTemplate.execute { Session session ->
            session.saveOrUpdate target
            if (flush) {
                flushSession session
            }
            return target
        }
    }

    protected D performMerge(final D target, final boolean flush) {
        hibernateTemplate.execute { Session session ->
            Object merged = session.merge(target)
            session.lock(merged, LockMode.NONE)
            if (flush) {
                flushSession session
            }
            return (D)merged
        }
    }

    protected D performInsert(final D target, final boolean shouldFlush) {
        hibernateTemplate.execute { Session session ->
            try {
                markInsertActive()
                session.save target
                if (shouldFlush) {
                    flushSession session
                }
                return target
            } finally {
                resetInsertActive()
            }

        }
    }

    protected void flushSession(Session session) throws HibernateException {
        try {
            session.flush()
        } catch (HibernateException e) {
            // session should not be flushed again after a data access exception!
            session.setFlushMode FlushMode.MANUAL
            throw e
        }
    }
    /**
     * Performs automatic association retrieval
     * @param entity The domain class to retrieve associations for
     * @param target The target object
     */
    @SuppressWarnings("unchecked")
    private void autoRetrieveAssociations(Datastore datastore, PersistentEntity entity, Object target) {
        EntityReflector reflector = datastore.mappingContext.getEntityReflector(entity)
        IHibernateTemplate t = this.hibernateTemplate
        for (PersistentProperty prop in entity.associations) {
            if(prop instanceof ToOne && !(prop instanceof Embedded)) {
                ToOne toOne = (ToOne)prop

                def propertyName = prop.name
                def propValue = reflector.getProperty(target, propertyName)
                if (propValue == null || t.contains(propValue)) {
                    continue
                }

                PersistentEntity otherSide = toOne.associatedEntity
                if (otherSide == null) {
                    continue
                }

                def identity = otherSide.identity
                if(identity == null) {
                    continue
                }

                def otherSideReflector = datastore.mappingContext.getEntityReflector(otherSide)
                try {
                    def id = (Serializable)otherSideReflector.getProperty(propValue, identity.name);
                    if (id) {
                        final Object associatedInstance = t.get(prop.type, id)
                        if (associatedInstance) {
                            reflector.setProperty(target, propertyName, associatedInstance)
                        }
                    }
                }
                catch (InvalidPropertyException ipe) {
                    // property is not accessable
                }
            }

        }
    }

    /**
     * Checks whether validation should be performed
     * @return true if the domain class should be validated
     * @param arguments  The arguments to the validate method
     * @param domainClass The domain class
     */
    private boolean shouldValidate(Map arguments, PersistentEntity entity) {
        if (!entity) {
            return false
        }

        if (arguments?.containsKey(ARGUMENT_VALIDATE)) {
            return ClassUtils.getBooleanFromMap(ARGUMENT_VALIDATE, arguments)
        }
        return true
    }

    private boolean shouldInsert(Map arguments) {
        ClassUtils.getBooleanFromMap(ARGUMENT_INSERT, arguments)
    }

    private boolean shouldMerge(Map arguments) {
        ClassUtils.getBooleanFromMap(ARGUMENT_MERGE, arguments)
    }


    protected boolean shouldFlush(Map map) {
        if (map?.containsKey(ARGUMENT_FLUSH)) {
            return ClassUtils.getBooleanFromMap(ARGUMENT_FLUSH, map)
        }
        return autoFlush
    }

    protected boolean shouldFail(Map map) {
        if (map?.containsKey(ARGUMENT_FAIL_ON_ERROR)) {
            return ClassUtils.getBooleanFromMap(ARGUMENT_FAIL_ON_ERROR, map)
        }
        return failOnError
    }

    /**
     * Sets the flush mode to manual. which ensures that the database changes are not persisted to the database
     * if a validation error occurs. If save() is called again and validation passes the code will check if there
     * is a manual flush mode and flush manually if necessary
     *
     * @param domainClass The domain class
     * @param target The target object that failed validation
     * @param errors The Errors instance  @return This method will return null signaling a validation failure
     */
    protected Object handleValidationError(PersistentEntity entity, final Object target, Errors errors) {
        // if a validation error occurs set the object to read-only to prevent a flush
        setObjectToReadOnly target
        if (entity) {
            for (Association association in entity.associations) {
                if (association instanceof ToOne && !association instanceof Embedded) {
                    if(proxyHandler.isInitialized(target, association.name)) {
                        def bean = new BeanWrapperImpl(target)
                        def propertyValue = bean.getPropertyValue(association.name)
                        if (propertyValue != null) {
                            setObjectToReadOnly propertyValue
                        }
                    }
                }
            }
        }
        setErrorsOnInstance target, errors
        return null
    }

    /**
     * Sets the target object to read-only using the given SessionFactory instance. This
     * avoids Hibernate performing any dirty checking on the object
     *
     *
     * @param target The target object
     * @param sessionFactory The SessionFactory instance
     */
    void setObjectToReadOnly(Object target) {
        hibernateTemplate.execute { Session session ->
            if (session.contains(target) && proxyHandler.isInitialized(target)) {
                target = proxyHandler.unwrap(target)
                session.setReadOnly target, true
                session.flushMode = FlushMode.MANUAL
            }
        }
    }
    /**
     * Sets the target object to read-write, allowing Hibernate to dirty check it and auto-flush changes.
     *
     * @see #setObjectToReadOnly(Object)
     *
     * @param target The target object
     * @param sessionFactory The SessionFactory instance
     */
    abstract void setObjectToReadWrite(Object target)

    /**
     * Associates the Errors object on the instance
     *
     * @param target The target instance
     * @param errors The Errors object
     */
    @CompileDynamic
    protected void setErrorsOnInstance(Object target, Errors errors) {
        if(target instanceof GormValidateable) {
            ((GormValidateable)target).setErrors(errors)
        }
        else {
            target."$GormProperties.ERRORS" = errors
        }
    }

    /**
     * Called by org.grails.orm.hibernate.metaclass.SavePersistentMethod's performInsert
     * to set a ThreadLocal variable that determines the value for getAssumedUnsaved().
     */
    static void markInsertActive() {
        insertActiveThreadLocal.set(Boolean.TRUE);
    }

    /**
     * Clears the ThreadLocal variable set by markInsertActive().
     */
    static void resetInsertActive() {
        insertActiveThreadLocal.remove();
    }

    /**
     * Increments the entities version number in order to force an update
     * @param target The target entity
     */
    @CompileDynamic
    protected void incrementVersion(Object target) {
        if (target.hasProperty(GormProperties.VERSION)) {
            Object version = target."${GormProperties.VERSION}"
            if (version instanceof Long) {
                target."${GormProperties.VERSION}" = ++((Long)version)
            }
        }
    }

    SessionFactory getSessionFactory() {
        return this.sessionFactory
    }
}
