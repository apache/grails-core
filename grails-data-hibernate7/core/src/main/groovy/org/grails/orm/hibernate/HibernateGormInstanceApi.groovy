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

import org.codehaus.groovy.runtime.InvokerHelper

import jakarta.persistence.GenerationType

import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.generator.Assigned
import org.hibernate.generator.Generator

import grails.gorm.validation.CascadingValidator
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import jakarta.persistence.FlushModeType
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.PersistEvent
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.grails.orm.hibernate.query.HibernateHqlQuery
import org.grails.orm.hibernate.support.HibernateRuntimeUtils

import org.hibernate.HibernateException
import org.hibernate.LockMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.EntityEntry
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.entity.EntityPersister
import org.hibernate.tuple.NonIdentifierAttribute
import org.springframework.beans.BeanWrapperImpl
import org.springframework.beans.InvalidPropertyException
import org.springframework.dao.DataAccessException
import org.springframework.validation.Errors
import org.springframework.validation.Validator

/**
 * The implementation of the GORM instance API contract for Hibernate.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormInstanceApi<D> extends GormInstanceApi<D> {

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
    static final ThreadLocal<Boolean> insertActiveThreadLocal = new ThreadLocal<Boolean>()

    protected SessionFactory sessionFactory
    protected ClassLoader classLoader
    protected IHibernateTemplate hibernateTemplate

    boolean autoFlush

    protected InstanceApiHelper instanceApiHelper

    HibernateGormInstanceApi(Class<D> persistentClass, HibernateDatastore datastore, ClassLoader classLoader) {
        super(persistentClass, datastore as Datastore)
        this.classLoader = classLoader
        this.sessionFactory = datastore.getSessionFactory()
        this.hibernateTemplate = new GrailsHibernateTemplate(sessionFactory, datastore)
        this.autoFlush = datastore.autoFlush
        this.failOnError = datastore.failOnError
        this.markDirty = datastore.markDirty
        this.instanceApiHelper = new InstanceApiHelper((GrailsHibernateTemplate) this.hibernateTemplate)
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

        autoRetrieveAssociations datastore, domainClass, target

        GormValidateable validateable = (GormValidateable) target
        validateable.skipValidation(true)

        try {
            String idPropertyName = domainClass.identity?.name ?: "id"
            Object idVal = InvokerHelper.getProperty(target, idPropertyName)
            if (idVal == null) {
                return performPersist(target, shouldFlush)
            } else {
                return performMerge(target, shouldFlush)
            }
        } finally {
            validateable.skipValidation(false)
        }
    }


    private boolean isAssignedId(PersistentEntity entity) {
        return ((SessionFactoryImplementor) sessionFactory)
                .getMappingMetamodel()
                .getEntityDescriptor(entity.getName())
                .getGenerator() instanceof Assigned
    }


    private Long nextId() {
        String hql = "select max(e.id) from ${persistentEntity.name} e"

        def result = (Long) HibernateHqlQuery.createHqlQuery(
                (HibernateDatastore) datastore,
                sessionFactory,
                persistentEntity,
                hql,
                false,
                false,
                null,
                null
                , null
                , (GrailsHibernateTemplate) hibernateTemplate).singleResult() ?: 0
        Random random = new Random()
        return result + random.nextInt(100) + 1
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
                session.remove instance
                if(flush) {
                    session.flush()
                }
            }
        }
        catch (DataAccessException e) {
            try {
                hibernateTemplate.execute { Session session ->
                    session.flushMode = FlushModeType.COMMIT
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
    D lock(D instance) {
        hibernateTemplate.lock(instance, LockMode.PESSIMISTIC_WRITE)
        instance
    }

    @Override
    D attach(D instance) {
        return (D) hibernateTemplate.execute { Session session ->
            return session.merge(instance)
        }
    }

    @Override
    D refresh(D instance) {
        hibernateTemplate.refresh(instance)
        return instance
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

    protected D performPersist(final D target, final boolean shouldFlush) {
        hibernateTemplate.execute { Session session ->
            try {
                markInsertActive()
                session.persist target
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
            session.setFlushMode FlushModeType.COMMIT
            throw e
        }
    }

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

    protected Object handleValidationError(PersistentEntity entity, final Object target, Errors errors) {
        setObjectToReadOnly target
        if (entity) {
            for (Association association in entity.associations) {
                if (association instanceof ToOne && !association instanceof Embedded) {
                        def bean = new BeanWrapperImpl(target)
                        def propertyValue = bean.getPropertyValue(association.name)
                        if (propertyValue != null) {
                            setObjectToReadOnly propertyValue
                        }
                }
            }
        }
        setErrorsOnInstance target, errors
        return null
    }

    @CompileDynamic
    protected void setErrorsOnInstance(Object target, Errors errors) {
        if(target instanceof GormValidateable) {
            ((GormValidateable)target).setErrors(errors)
        }
        else {
            target."$GormProperties.ERRORS" = errors
        }
    }

    static void markInsertActive() {
        insertActiveThreadLocal.set(Boolean.TRUE);
    }

    static void resetInsertActive() {
        insertActiveThreadLocal.remove();
    }

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

    /**
     * Checks whether a field is dirty
     *
     * @param instance The instance
     * @param fieldName The name of the field
     *
     * @return true if the field is dirty
     */

    @CompileDynamic
    boolean isDirty(D instance, String fieldName) {
        SessionImplementor session = (SessionImplementor)sessionFactory.currentSession
        def entry = findEntityEntry(instance, session)
        if (!entry || !entry.loadedState) {
            return false
        }

        EntityPersister persister = entry.persister
        Object[] values = persister.getPropertyValues(instance)
        def dirtyProperties = findDirty(persister, values, entry, instance, session)
        if(dirtyProperties == null) {
            return false
        }
        else {
            int fieldIndex = persister.getEntityMetamodel().getProperties().findIndexOf { NonIdentifierAttribute attribute -> fieldName == attribute.name }
            return fieldIndex in dirtyProperties
        }
    }

    @CompileDynamic // required for Hibernate 5.2 compatibility
    private def findDirty(EntityPersister persister, Object[] values, EntityEntry entry, D instance, SessionImplementor session) {
        persister.findDirty(values, entry.loadedState, instance, session)
    }

    /**
     * Checks whether an entity is dirty
     *
     * @param instance The instance
     * @return true if it is dirty
     */
    @CompileDynamic
    boolean isDirty(D instance) {
        SessionImplementor session = (SessionImplementor)sessionFactory.currentSession
        def entry = findEntityEntry(instance, session)
        if (!entry || !entry.loadedState) {
            return false
        }
        EntityPersister persister = entry.persister
        Object[] currentState = persister.getPropertyValues(instance)
        def dirtyPropertyIndexes = findDirty(persister, currentState, entry, instance, session)
        return dirtyPropertyIndexes != null
    }

    /**
     * Obtains a list of property names that are dirty
     *
     * @param instance The instance
     * @return A list of property names that are dirty
     */

    @CompileDynamic
    List getDirtyPropertyNames(D instance) {
        SessionImplementor session = (SessionImplementor)sessionFactory.currentSession
        def entry = findEntityEntry(instance, session)
        if (!entry || !entry.loadedState) {
            return []
        }

        EntityPersister persister = entry.persister
        Object[] currentState = persister.getPropertyValues(instance)
        int[] dirtyPropertyIndexes = findDirty(persister, currentState, entry, instance, session)
        List<String> names = []
        def entityProperties = persister.getEntityMetamodel().getProperties()
        for (index in dirtyPropertyIndexes) {
            names.add entityProperties[index].name
        }
        return names
    }

    /**
     * Gets the original persisted value of a field.
     *
     * @param fieldName The field name
     * @return The original persisted value
     */
    Object getPersistentValue(D instance, String fieldName) {
        SessionImplementor session = (SessionImplementor)sessionFactory.currentSession
        def entry = findEntityEntry(instance, session, false)
        if (!entry || !entry.loadedState) {
            return null
        }

        EntityPersister persister = entry.persister
        int fieldIndex = persister.getEntityMetamodel().getProperties().findIndexOf {
            NonIdentifierAttribute attribute -> fieldName == attribute.name
        }
        return fieldIndex == -1 ? null : entry.loadedState[fieldIndex]
    }


    protected EntityEntry findEntityEntry(D instance, SessionImplementor session, boolean forDirtyCheck = true) {
        def entry = session.persistenceContext.getEntry(instance)
        if (!entry) {
            return null
        }

        if (forDirtyCheck && !entry.requiresDirtyCheck(instance) && entry.loadedState) {
            return null
        }

        return entry
    }

    void setObjectToReadWrite(Object target) {
        GrailsHibernateUtil.setObjectToReadWrite(target, sessionFactory)
    }

    void setObjectToReadOnly(Object target) {
        GrailsHibernateUtil.setObjectToReadyOnly(target, sessionFactory)
    }
}
