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
package org.grails.orm.hibernate

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Generated
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import org.grails.datastore.gorm.validation.CascadingValidator
import org.grails.datastore.gorm.DatastoreResolver
import org.hibernate.Session
import org.hibernate.resource.transaction.spi.TransactionStatus
import org.hibernate.engine.spi.SessionImplementor
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.ManyToMany
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.LockMode
import jakarta.persistence.LockModeType
import org.codehaus.groovy.runtime.InvokerHelper
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.HibernateGormValidationApi
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.orm.hibernate.support.HibernateRuntimeUtils

/**
 * Hibernate GORM instance API.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormInstanceApi<D> extends GormInstanceApi<D> {

    protected Class<? extends Exception> validationException

//    HibernateGormInstanceApi(Class<D> persistentClass, HibernateDatastore datastore, ClassLoader classLoader, PlatformTransactionManager transactionManager) {
//        super(persistentClass, datastore)
//        this.hibernateDatastore = datastore
//        this.hibernateTemplate = new GrailsHibernateTemplate(datastore.sessionFactory, datastore)
//        this.proxyHandler = datastore.mappingContext.proxyHandler
//        try {
//            this.validationException = (Class<? extends Exception>)classLoader.loadClass("grails.validation.ValidationException")
//        } catch (ClassNotFoundException e) {
//            this.validationException = org.grails.datastore.mapping.validation.ValidationException
//        }
//    }

    HibernateGormInstanceApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver, ClassLoader classLoader) {
        super(persistentClass, mappingContext, datastoreResolver)
        try {
            this.validationException = (Class<? extends Exception>)classLoader.loadClass("grails.validation.ValidationException")
        } catch (ClassNotFoundException e) {
            this.validationException = org.grails.datastore.mapping.validation.ValidationException
        }
    }

    protected HibernateDatastore getHibernateDatastore() {
        return (HibernateDatastore) getDatastore()
    }

    protected IHibernateTemplate getHibernateTemplate() {
        return (IHibernateTemplate) getHibernateDatastore().getHibernateTemplate()
    }


    protected boolean isAutoFlush() {
        return getHibernateDatastore().isAutoFlush()
    }

    @Override
    boolean isFailOnError() {
        return getHibernateDatastore().isFailOnError()
    }

    @Override
    boolean isMarkDirty() {
        return getHibernateDatastore().markDirty
    }

    @Override
    D save(D target, Map arguments) {

        PersistentEntity domainClass = getGormPersistentEntity()
        runDeferredBinding()
        boolean shouldFlush = shouldFlush(arguments)
        boolean shouldValidate = shouldValidate(arguments, domainClass)

        HibernateRuntimeUtils.autoAssociateBidirectionalOneToOnes(domainClass, target)

        boolean deepValidate = true
        if (arguments?.containsKey(HibernateGormValidationApi.ARGUMENT_DEEP_VALIDATE)) {
            deepValidate = ClassUtils.getBooleanFromMap(HibernateGormValidationApi.ARGUMENT_DEEP_VALIDATE, arguments)
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

    private static final Class DEFERRED_BINDING

    static {
        try {
            DEFERRED_BINDING = HibernateGormInstanceApi.class.classLoader.loadClass("org.grails.datastore.mapping.core.DeferredBindingActions")
        } catch (Throwable e) {
            DEFERRED_BINDING = null
        }
    }

    private static void runDeferredBinding() {
        if (DEFERRED_BINDING != null) {
            DEFERRED_BINDING.getMethod('runActions').invoke(null)
        }
    }

    protected void autoRetrieveAssociations(Datastore datastore, PersistentEntity domainClass, D target) {
        // no-op, handled by Hibernate
    }

    protected boolean shouldFlush(Map arguments) {
        if (arguments?.containsKey(DynamicFinder.ARGUMENT_FLUSH_MODE)) {
            return ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_FLUSH_MODE, arguments)
        }
        return isAutoFlush()
    }

    protected boolean shouldValidate(Map arguments, PersistentEntity domainClass) {
        if (arguments?.containsKey(org.grails.datastore.gorm.GormValidationApi.ARGUMENT_DEEP_VALIDATE)) {
            return ClassUtils.getBooleanFromMap(org.grails.datastore.gorm.GormValidationApi.ARGUMENT_DEEP_VALIDATE, arguments)
        }
        return true
    }

    protected boolean shouldFail(Map arguments) {
        if (arguments?.containsKey("failOnError")) {
            return ClassUtils.getBooleanFromMap("failOnError", arguments)
        }
        return isFailOnError()
    }

    @Override
    D merge(D target, Map arguments) {
        return performMerge(target, shouldFlush(arguments))
    }

    @Override
    void delete(D target, Map arguments) {
        hibernateTemplate.execute { Session session ->
            session.remove target
            if (shouldFlush(arguments)) {
                session.flush()
            }
        }
    }

    @Override
    D attach(D target) {
        hibernateTemplate.execute { Session session ->
            session.lock target, LockModeType.NONE
        }
        return target
    }

    @Override
    void discard(D target) {
        getHibernateTemplate().execute { Session session ->
            if (session.contains(target)) {
                session.detach target
            }
        }
    }

    @Override
    boolean isAttached(D target) {
        getHibernateTemplate().execute { Session session ->
            session.contains target
        }
    }

    @Override
    D lock(D target) {
        getHibernateTemplate().execute { Session session ->
            session.lock target, LockModeType.PESSIMISTIC_WRITE
        }
        return target
    }

    @Override
    D refresh(D target) {
        getHibernateTemplate().execute { Session session ->
            session.refresh target
        }
        return target
    }

    D read(Serializable id) {
        (D) getHibernateTemplate().execute { Session session ->
            session.get(persistentClass, id)
        }
    }

    protected D performUpsert(D target, boolean shouldFlush) {
        getHibernateTemplate().execute { Session session ->
            if (session.contains(target)) {
                if (shouldFlush) {
                    flushSession session
                }
                return target
            } else {
                Serializable id = (Serializable) InvokerHelper.getProperty(target, getGormPersistentEntity().identity.name)
                if (id == null) {
                    return performPersist(target, shouldFlush)
                } else {
                    return performMerge(target, shouldFlush)
                }
            }
        }
    }

    protected void flushSession(Session session) {
        HibernateDatastore datastore = getHibernateDatastore()
        if (datastore.isOsivReadOnly(datastore.sessionFactory)) {
            return
        }
        session.flush()
    }

    protected D performMerge(final D target, final boolean flush) {
        getHibernateTemplate().execute { Session session ->
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
                PersistentEntity entity = getGormPersistentEntity()
                String idProp = entity.identity?.name ?: 'id'
                InvokerHelper.setProperty(target, idProp, InvokerHelper.getProperty(merged, idProp))
            }
            if (flush) {
                flushSession session
            }
            // Sync version after flush so the incremented value is captured
            PersistentEntity entity = getGormPersistentEntity()
            PersistentProperty versionProperty = entity.version
            if (versionProperty != null) {
                InvokerHelper.setProperty(target, versionProperty.name, InvokerHelper.getProperty(merged, versionProperty.name))
            }
            return target
        }
    }

    protected D performPersist(final D target, final boolean shouldFlush) {
        getHibernateTemplate().execute { Session session ->
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
        PersistentEntity entity = getGormPersistentEntity()
        EntityReflector reflector = datastore.mappingContext.getEntityReflector(entity)
        if (reflector == null) return

        SessionImplementor si = (SessionImplementor) session

        for (Association assoc in entity.associations) {
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

    @CompileDynamic
    protected void handleValidationError(PersistentEntity domainClass, D target, Errors errors) {
        InvokerHelper.setProperty(target, GormProperties.ERRORS, errors)
    }

    @CompileDynamic
    protected void markInsertActive() {
        HibernateRuntimeUtils.markInsertActive()
    }

    @CompileDynamic
    protected static void resetInsertActive() {
        HibernateRuntimeUtils.resetInsertActive()
    }

    @CompileDynamic
    void setObjectToReadWrite(Object target) {
        HibernateRuntimeUtils.setObjectToReadWrite(target, hibernateDatastore.sessionFactory)
    }

    @CompileDynamic
    void setObjectToReadOnly(Object target) {
        HibernateRuntimeUtils.setObjectToReadyOnly(target, hibernateDatastore.sessionFactory)
    }

    @CompileDynamic
    protected void incrementVersion(Object target) {
        PersistentEntity persistentEntity = getGormPersistentEntity()
        if (persistentEntity.isVersioned() && target.hasProperty(GormProperties.VERSION)) {
            Object version = target."${GormProperties.VERSION}"
            if (version instanceof Long) {
                target."${GormProperties.VERSION}" = ++((Long) version)
            }
        }
    }
}
