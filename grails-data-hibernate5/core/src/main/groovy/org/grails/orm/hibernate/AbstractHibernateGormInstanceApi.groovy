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
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import org.grails.datastore.mapping.validation.CascadingValidator
import org.grails.datastore.gorm.DatastoreResolver

/**
 * Abstract implementation of the Hibernate GORM instance API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class AbstractHibernateGormInstanceApi<D> extends GormInstanceApi<D> {

    protected HibernateDatastore hibernateDatastore
    protected GrailsHibernateTemplate hibernateTemplate
    protected ProxyHandler proxyHandler
    protected Class<? extends Exception> validationException

    AbstractHibernateGormInstanceApi(Class<D> persistentClass, HibernateDatastore datastore, ClassLoader classLoader, PlatformTransactionManager transactionManager) {
        super(persistentClass, datastore, transactionManager)
        this.hibernateDatastore = datastore
        this.hibernateTemplate = new GrailsHibernateTemplate(datastore.sessionFactory, datastore)
        this.proxyHandler = datastore.mappingContext.proxyHandler
        try {
            this.validationException = (Class<? extends Exception>)classLoader.loadClass("grails.validation.ValidationException")
        } catch (ClassNotFoundException e) {
            this.validationException = org.grails.datastore.mapping.validation.ValidationException
        }
    }

    AbstractHibernateGormInstanceApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver, ClassLoader classLoader) {
        super(persistentClass, mappingContext, datastoreResolver)
        try {
            this.validationException = (Class<? extends Exception>)classLoader.loadClass("grails.validation.ValidationException")
        } catch (ClassNotFoundException e) {
            this.validationException = org.grails.datastore.mapping.validation.ValidationException
        }
    }

    protected HibernateDatastore getHibernateDatastore() {
        if (hibernateDatastore == null) {
            return (HibernateDatastore) getDatastore()
        }
        return hibernateDatastore
    }

    protected IHibernateTemplate getHibernateTemplate() {
        if (hibernateTemplate == null) {
            return (IHibernateTemplate) hibernateDatastore.getHibernateTemplate()
        }
        return hibernateTemplate
    }

    protected ProxyHandler getProxyHandler() {
        if (proxyHandler == null) {
            return datastore.mappingContext.proxyHandler
        }
        return proxyHandler
    }

    protected boolean isAutoFlush() {
        if (hibernateDatastore != null) {
            return hibernateDatastore.autoFlush
        }
        return autoFlush
    }

    @Override
    boolean isFailOnError() {
        if (hibernateDatastore != null) {
            return hibernateDatastore.failOnError
        }
        return failOnError
    }

    @Override
    boolean isMarkDirty() {
        if (hibernateDatastore != null) {
            return hibernateDatastore.markDirty
        }
        return markDirty
    }

    @Override
    D save(D target, Map arguments) {

        PersistentEntity domainClass = getGormPersistentEntity()
        runDeferredBinding()
        boolean shouldFlush = shouldFlush(arguments)
        boolean shouldValidate = shouldValidate(arguments, domainClass)

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

    protected void autoRetrieveAssociations(Datastore datastore, PersistentEntity domainClass, D target) {
        HibernateRuntimeUtils.autoRetrieveAssociations(datastore, domainClass, target)
    }

    protected boolean shouldFlush(Map arguments) {
        if (arguments?.containsKey(ARGUMENT_FLUSH)) {
            return ClassUtils.getBooleanFromMap(ARGUMENT_FLUSH, arguments)
        }
        return isAutoFlush()
    }

    protected boolean shouldValidate(Map arguments, PersistentEntity domainClass) {
        if (arguments?.containsKey(ARGUMENT_VALIDATE)) {
            return ClassUtils.getBooleanFromMap(ARGUMENT_VALIDATE, arguments)
        }
        return true
    }

    protected boolean shouldFail(Map arguments) {
        if (arguments?.containsKey(ARGUMENT_FAIL_ON_ERROR)) {
            return ClassUtils.getBooleanFromMap(ARGUMENT_FAIL_ON_ERROR, arguments)
        }
        return isFailOnError()
    }

    @Override
    D merge(D target, Map arguments) {
        return save(target, arguments)
    }

    @Override
    void delete(D target, Map arguments) {
        hibernateTemplate.execute { Session session ->
            session.delete target
            if (shouldFlush(arguments)) {
                session.flush()
            }
        }
    }

    @Override
    D attach(D target) {
        hibernateTemplate.lock target, LockMode.NONE
        return target
    }

    @Override
    void discard(D target) {
        hibernateTemplate.execute { Session session ->
            if (session.contains(target)) {
                session.evict target
            }
        }
    }

    @Override
    boolean isAttached(D target) {
        hibernateTemplate.execute { Session session ->
            session.contains target
        }
    }

    @Override
    D lock(D target) {
        hibernateTemplate.lock target, LockMode.PESSIMISTIC_WRITE
        return target
    }

    @Override
    D refresh(D target) {
        hibernateTemplate.refresh target
        return target
    }

    @Override
    @CompileDynamic
    D read(Serializable id) {
        (D) hibernateTemplate.execute { Session session ->
            session.get(persistentClass, id)
        }
    }

    abstract D performUpsert(D target, boolean shouldFlush)

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
