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
import org.grails.orm.hibernate.query.GrailsHibernateQueryUtils
import org.grails.orm.hibernate.support.HibernateRuntimeUtils
import org.hibernate.LockMode
import org.hibernate.Session
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import org.grails.datastore.gorm.validation.CascadingValidator
import org.grails.datastore.gorm.DatastoreResolver

/**
 * Abstract implementation of the Hibernate GORM instance API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class AbstractHibernateGormInstanceApi<D> extends GormInstanceApi<D> {

    private static final Class DEFERRED_BINDING
    static {
        try {
            DEFERRED_BINDING = AbstractHibernateGormInstanceApi.class.classLoader.loadClass("org.grails.datastore.mapping.core.DeferredBindingActions")
        } catch (Throwable e) {
            DEFERRED_BINDING = null
        }
    }

    protected IHibernateTemplate hibernateTemplate
    protected Class<? extends Exception> validationException

    AbstractHibernateGormInstanceApi(Class<D> persistentClass, HibernateDatastore datastore, ClassLoader classLoader) {
        super(persistentClass, datastore)
        this.hibernateTemplate = (IHibernateTemplate) datastore.getHibernateTemplate()
        initializeValidationException(classLoader)
    }

    AbstractHibernateGormInstanceApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver, ClassLoader classLoader) {
        super(persistentClass, mappingContext, datastoreResolver)
        initializeValidationException(classLoader)
    }

    protected void initializeValidationException(ClassLoader classLoader) {
        for (cl in [classLoader, Thread.currentThread().getContextClassLoader(), AbstractHibernateGormInstanceApi.class.classLoader]) {
            if (cl == null) continue
            try {
                this.validationException = (Class<? extends Exception>) cl.loadClass("grails.validation.ValidationException")
                return
            } catch (Throwable e) {
                // ignore
            }
        }
        this.validationException = org.grails.datastore.mapping.validation.ValidationException
    }

    protected HibernateDatastore getHibernateDatastore() {
        return (HibernateDatastore) getDatastore()
    }

    protected IHibernateTemplate getHibernateTemplate() {
        if (hibernateTemplate == null) {
            return (IHibernateTemplate) getHibernateDatastore().getHibernateTemplate()
        }
        return hibernateTemplate
    }

    protected ProxyHandler getProxyHandler() {
        return datastore.mappingContext.proxyHandler
    }

    protected boolean isAutoFlush() {
        return getHibernateDatastore().autoFlush
    }

    @Override
    boolean isFailOnError() {
        return getHibernateDatastore().failOnError
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
        if (arguments?.containsKey('deepValidate')) {
            deepValidate = ClassUtils.getBooleanFromMap('deepValidate', arguments)
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

    protected void autoRetrieveAssociations(Datastore datastore, PersistentEntity domainClass, D target) {
        // no-op, handled by Hibernate
    }

    protected boolean shouldFlush(Map arguments) {
        if (arguments?.containsKey('flush')) {
            return ClassUtils.getBooleanFromMap('flush', arguments)
        }
        return isAutoFlush()
    }

    protected boolean shouldValidate(Map arguments, PersistentEntity domainClass) {
        if (arguments?.containsKey('validate')) {
            return ClassUtils.getBooleanFromMap('validate', arguments)
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
        return save(target, arguments)
    }

    @Override
    void delete(D target, Map arguments) {
        getHibernateTemplate().execute { Object session ->
            ((Session)session).delete target
            if (shouldFlush(arguments)) {
                ((Session)session).flush()
            }
        }
    }

    @Override
    D attach(D target) {
        getHibernateTemplate().lock target, LockMode.NONE
        return target
    }

    @Override
    void discard(D target) {
        getHibernateTemplate().execute { Object session ->
            if (((Session)session).contains(target)) {
                ((Session)session).evict target
            }
        }
    }

    @Override
    boolean isAttached(D target) {
        getHibernateTemplate().execute { Object session ->
            ((Session)session).contains target
        }
    }

    @Override
    D lock(D target) {
        getHibernateTemplate().lock target, LockMode.PESSIMISTIC_WRITE
        return target
    }

    @Override
    D refresh(D target) {
        getHibernateTemplate().refresh target
        return target
    }

    @Override
    @CompileDynamic
    D read(Serializable id) {
        (D) getHibernateTemplate().execute { Object session ->
            ((Session)session).get(persistentClass, id)
        }
    }

    protected abstract D performUpsert(D target, boolean shouldFlush)

    @CompileDynamic
    protected void handleValidationError(PersistentEntity domainClass, D target, Errors errors) {
        org.codehaus.groovy.runtime.InvokerHelper.setProperty(target, GormProperties.ERRORS, errors)
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
        HibernateRuntimeUtils.setObjectToReadWrite(target, getHibernateDatastore().sessionFactory)
    }

    @CompileDynamic
    void setObjectToReadOnly(Object target) {
        HibernateRuntimeUtils.setObjectToReadyOnly(target, getHibernateDatastore().sessionFactory)
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
