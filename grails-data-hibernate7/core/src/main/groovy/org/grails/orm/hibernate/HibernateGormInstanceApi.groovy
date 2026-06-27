/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
/*
 * Copyright 2013-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper

import jakarta.persistence.FlushModeType
import jakarta.persistence.LockModeType

import org.hibernate.HibernateException
import org.hibernate.LockMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.engine.spi.EntityEntry
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.entity.EntityPersister

import org.springframework.beans.BeanWrapperImpl
import org.springframework.beans.InvalidPropertyException
import org.springframework.dao.DataAccessException
import org.springframework.validation.Errors
import org.springframework.validation.Validator

import grails.gorm.validation.CascadingValidator
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.grails.orm.hibernate.support.HibernateRuntimeUtils

/**
 * The implementation of the GORM instance API contract for Hibernate 7.
 */
@CompileStatic
class HibernateGormInstanceApi<D> extends GormInstanceApi<D> {

    private static final String ARGUMENT_VALIDATE = 'validate'
    private static final String ARGUMENT_DEEP_VALIDATE = 'deepValidate'
    private static final String ARGUMENT_FLUSH = 'flush'
    private static final String ARGUMENT_INSERT = 'insert'
    private static final String ARGUMENT_MERGE = 'merge'
    private static final String ARGUMENT_FAIL_ON_ERROR = 'failOnError'
    private static final Class DEFERRED_BINDING

    static {
        try {
            DEFERRED_BINDING = Class.forName('grails.validation.DeferredBindingActions')
        } catch (Throwable ignored) {
            DEFERRED_BINDING = null
        }
    }

    static final ThreadLocal<Boolean> insertActiveThreadLocal = new ThreadLocal<Boolean>()

    protected SessionFactory sessionFactory
    protected ClassLoader classLoader
    protected IHibernateTemplate hibernateTemplate
    boolean autoFlush
    protected InstanceApiHelper instanceApiHelper
    protected PersistentEntity persistentEntity

    HibernateGormInstanceApi(Class<D> persistentClass, HibernateDatastore datastore, ClassLoader classLoader) {
        super(persistentClass, datastore as Datastore)
        this.classLoader = classLoader
        this.sessionFactory = datastore.getSessionFactory()
        this.hibernateTemplate = (GrailsHibernateTemplate) datastore.getHibernateTemplate()
        this.autoFlush = datastore.autoFlush
        this.failOnError = datastore.failOnError
        this.markDirty = datastore.markDirty
        this.instanceApiHelper = datastore.getInstanceApiHelper()
        this.persistentEntity = datastore.mappingContext.getPersistentEntity(persistentClass.name)
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
                    ((CascadingValidator) validator).validate target, errors, deepValidate
                } else if (validator instanceof org.grails.datastore.gorm.validation.CascadingValidator) {
                    ((org.grails.datastore.gorm.validation.CascadingValidator) validator).validate target, errors, deepValidate
                } else {
                    validator.validate target, errors
                }

                if (errors.hasErrors()) {
                    handleValidationError(domainClass, target, errors)
                    if (shouldFail(arguments)) {
                        throw validationException.newInstance('Validation Error(s) occurred during save()', errors)
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
            return performUpsert(target, shouldFlush)
        } finally {
            validateable.skipValidation(false)
        }
    }

    private static void runDeferredBinding() {
        if (DEFERRED_BINDING != null) {
            DEFERRED_BINDING.getMethod('runActions').invoke(null)
        }
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
                if (flush) {
                    session.flush()
                }
            }
        }
        catch (DataAccessException e) {
            try {
                hibernateTemplate.execute { Session session ->
                    session.setFlushMode(FlushModeType.COMMIT)
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
        hibernateTemplate.execute { Session session ->
            HibernateAttachSupport.attach(instance, session)
        }
        return instance
    }

    @Override
    D refresh(D instance) {
        hibernateTemplate.refresh(instance)
        return instance
    }

    protected D performUpsert(D target, boolean shouldFlush) {
        PersistentEntity entity = persistentEntity
        String idPropertyName = entity.identity?.name ?: 'id'
        Object idVal = InvokerHelper.getProperty(target, idPropertyName)
        if (idVal == null) {
            return performPersist(target, shouldFlush)
        } else {
            return performMerge(target, shouldFlush)
        }
    }

    protected D performMerge(final D target, final boolean flush) {
        hibernateTemplate.execute { Session session ->
            D merged
            if (session.contains(target)) {
                // Entity is already managed in this session — merging would cause H7 to create
                // a second PersistentCollection for the same role+key ("two representations").
                // Just use the entity as-is; dirty-checking + cascade will handle children.
                merged = target
            } else {
                reconcileCollections(session, target)
                merged = (D) session.merge(target)
                session.lock(merged, LockModeType.NONE)
                // Sync id back immediately so target has an identity
                String idProp = persistentEntity.identity?.name ?: 'id'
                InvokerHelper.setProperty(target, idProp, InvokerHelper.getProperty(merged, idProp))
            }
            if (flush) {
                flushSession session
            }
            // Sync version after flush so the incremented value is captured
            PersistentProperty versionProperty = persistentEntity.version
            if (versionProperty != null) {
                InvokerHelper.setProperty(target, versionProperty.name, InvokerHelper.getProperty(merged, versionProperty.name))
            }
            return target
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

    /**
     * Reconciles collection fields on an entity before session.merge() to prevent H7's
     * "Found two representations of same collection" error.
     *
     * Two scenarios cause this error:
     *
     * 1. Stale PersistentCollection: the field holds a PersistentCollection from a previous
     *    (now closed) session. H7 merge in the new session sees two collection objects for the
     *    same role + key. Fix: copy the items to a plain collection so merge can create a fresh one.
     *
     * 2. Plain collection on a managed entity: addTo* created a new ArrayList on a managed entity
     *    that already has a session-tracked PersistentCollection for that field. Fix: handled
     *    upstream by HibernateEntity.addTo override; reconcileCollections handles any residual cases.
     */
    @SuppressWarnings('unchecked')
    private void reconcileCollections(Session session, D target) {
        EntityReflector reflector = datastore.mappingContext.getEntityReflector(persistentEntity)
        if (reflector == null) return

        SessionImplementor si = (SessionImplementor) session

        for (Association assoc in persistentEntity.associations) {
            if (!(assoc instanceof OneToMany) && !(assoc instanceof ManyToMany)) continue

            String propName = assoc.name
            Object fieldValue = reflector.getProperty(target, propName)
            if (fieldValue == null) continue

            if (fieldValue instanceof PersistentCollection) {
                PersistentCollection<?> pc = (PersistentCollection<?>) fieldValue
                // If this PersistentCollection belongs to a different (closed) session,
                // replace it with a plain collection so merge can create a fresh one.
                if (pc.getSession() != si) {
                    Collection<Object> plain = (Collection<Object>) [].asType(assoc.type)
                    if (pc.wasInitialized()) {
                        plain.addAll((Collection<Object>) pc)
                    }
                    reflector.setProperty(target, propName, plain)
                }
                // If it belongs to the current session, leave it alone — no issue.
            }
            // Plain (non-PersistentCollection) fields on managed entities should have been
            // handled by HibernateEntity.addTo; nothing more to do here.
        }
    }

    protected static void flushSession(Session session) throws HibernateException {
        try {
            session.flush()
        } catch (HibernateException e) {
            session.setFlushMode(FlushModeType.COMMIT)
            throw e
        }
    }

    @SuppressWarnings('unchecked')
    private void autoRetrieveAssociations(Datastore datastore, PersistentEntity entity, Object target) {
        EntityReflector reflector = datastore.mappingContext.getEntityReflector(entity)
        IHibernateTemplate t = this.hibernateTemplate
        for (PersistentProperty prop in entity.associations) {
            if (prop instanceof ToOne && !(prop instanceof Embedded)) {
                ToOne toOne = (ToOne) prop
                def propertyName = prop.name
                def propValue = reflector.getProperty(target, propertyName)
                if (propValue == null || t.contains(propValue)) {
                    continue
                }

                PersistentEntity otherSide = toOne.associatedEntity
                if (otherSide == null) continue

                def identity = otherSide.identity
                if (identity == null) continue

                def otherSideReflector = datastore.mappingContext.getEntityReflector(otherSide)
                try {
                    def id = (Serializable) otherSideReflector.getProperty(propValue, identity.name)
                    if (id) {
                        final Object associatedInstance = t.get(prop.type, id)
                        if (associatedInstance) {
                            reflector.setProperty(target, propertyName, associatedInstance)
                        }
                    }
                }
                catch (InvalidPropertyException ignored) {
                }
            }
        }
    }

    private static boolean shouldValidate(Map arguments, PersistentEntity entity) {
        if (!entity) return false
        if (arguments?.containsKey(ARGUMENT_VALIDATE)) {
            return ClassUtils.getBooleanFromMap(ARGUMENT_VALIDATE, arguments)
        }
        return true
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

    protected static void setErrorsOnInstance(Object target, Errors errors) {
        if (target instanceof GormValidateable) {
            ((GormValidateable) target).setErrors(errors)
        } else {
            ((GroovyObject) target).setProperty(GormProperties.ERRORS, errors)
        }
    }

    static void markInsertActive() {
        insertActiveThreadLocal.set(Boolean.TRUE)
    }

    static void resetInsertActive() {
        insertActiveThreadLocal.remove()
    }

    // --- Dirty Checking Logic ---

    boolean isDirty(D instance, String fieldName) {
        SessionImplementor session = (SessionImplementor) sessionFactory.currentSession
        EntityEntry entry = findEntityEntry(instance, session)
        if (!entry || !entry.loadedState) return false

        EntityPersister persister = entry.persister
        Object[] values = persister.getValues(instance)
        int[] dirtyProperties = findDirty(persister, values, entry, instance, session)
        if (dirtyProperties == null) return false

        String[] propertyNames = persister.getPropertyNames()
        int fieldIndex = -1
        for (int i = 0; i < propertyNames.length; i++) {
            if (propertyNames[i] == fieldName) {
                fieldIndex = i; break
            }
        }
        return fieldIndex in dirtyProperties
    }

    boolean isDirty(D instance) {
        SessionImplementor session = (SessionImplementor) sessionFactory.currentSession
        EntityEntry entry = findEntityEntry(instance, session)
        if (!entry || !entry.loadedState) return false

        EntityPersister persister = entry.persister
        Object[] currentState = persister.getValues(instance)
        int[] dirtyPropertyIndexes = findDirty(persister, currentState, entry, instance, session)
        return dirtyPropertyIndexes != null
    }

    List<String> getDirtyPropertyNames(D instance) {
        SessionImplementor session = (SessionImplementor) sessionFactory.currentSession
        EntityEntry entry = findEntityEntry(instance, session)
        if (!entry || !entry.loadedState) return []

        EntityPersister persister = entry.persister
        Object[] currentState = persister.getValues(instance)
        int[] dirtyPropertyIndexes = findDirty(persister, currentState, entry, instance, session)

        List<String> names = []
        String[] propertyNames = persister.getPropertyNames()
        if (dirtyPropertyIndexes != null) {
            for (int index : dirtyPropertyIndexes) {
                names.add(propertyNames[index])
            }
        }
        return names
    }

    Object getPersistentValue(D instance, String fieldName) {
        SessionImplementor session = (SessionImplementor) sessionFactory.currentSession
        def entry = findEntityEntry(instance, session, false)
        if (!entry || !entry.loadedState) return null

        EntityPersister persister = entry.persister
        String[] propertyNames = persister.getPropertyNames()
        int fieldIndex = propertyNames.findIndexOf { it == fieldName }
        return fieldIndex == -1 ? null : entry.loadedState[fieldIndex]
    }

    // --- Helper Methods using proper Generic definitions to satisfy stubs ---

    private static <T> int[] findDirty(EntityPersister persister, Object[] values, EntityEntry entry, T instance, SessionImplementor session) {
        persister.findDirty(values, entry.loadedState, instance, session)
    }

    protected static <T> EntityEntry findEntityEntry(T instance, SessionImplementor session, boolean forDirtyCheck = true) {
        def entry = session.persistenceContext.getEntry(instance)
        if (!entry) return null
        if (forDirtyCheck && !entry.requiresDirtyCheck(instance) && entry.loadedState) return null
        return entry
    }

    void setObjectToReadWrite(Object target) {
        GrailsHibernateUtil.setObjectToReadWrite(target, sessionFactory)
    }

    void setObjectToReadOnly(Object target) {
        GrailsHibernateUtil.setObjectToReadyOnly(target, sessionFactory)
    }
}
