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

import groovy.transform.CompileStatic

import org.hibernate.FlushMode
import org.hibernate.Session

import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.Validator

import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.gorm.validation.CascadingValidator
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.validation.ValidationErrors
import org.grails.orm.hibernate.support.HibernateRuntimeUtils
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.gorm.DatastoreResolver

@CompileStatic
class HibernateGormValidationApi<D> extends GormValidationApi<D> {

    public static final String ARGUMENT_DEEP_VALIDATE = 'deepValidate'
    private static final String ARGUMENT_EVICT = 'evict'

    protected final ClassLoader classLoader
    protected IHibernateTemplate hibernateTemplate

    HibernateGormValidationApi(Class<D> persistentClass, HibernateDatastore datastore, ClassLoader classLoader) {
        super(persistentClass, datastore)
        this.classLoader = classLoader
        this.hibernateTemplate = (IHibernateTemplate) datastore.getHibernateTemplate()
    }

    HibernateGormValidationApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver, ClassLoader classLoader) {
        super(persistentClass, mappingContext, datastoreResolver)
        this.classLoader = classLoader
    }

    @Override
    GormValidationApi<D> forQualifier(String qualifier) {
        Datastore ds = getDatastore()
        if (ds == null) return this
        
        org.grails.datastore.gorm.DatastoreResolver resolver = new org.grails.datastore.gorm.DatastoreResolver() {
            @Override Datastore resolve() { org.grails.datastore.gorm.GormRegistry.instance.apiResolver.findDatastore(persistentClass, qualifier) }
        }
        return new HibernateGormValidationApi<D>(persistentClass, ds.mappingContext, resolver, classLoader)
    }

    protected HibernateDatastore getHibernateDatastore() {
        (HibernateDatastore) getDatastore()
    }

    protected IHibernateTemplate getHibernateTemplate() {
        if (this.hibernateTemplate == null) {
            return (IHibernateTemplate) getHibernateDatastore().getHibernateTemplate()
        }
        return hibernateTemplate
    }

    @Override
    boolean validate(D instance, Map arguments = Collections.emptyMap()) {
        validate(instance, null, arguments)
    }

    boolean validate(D instance, List validatedFieldsList, Map arguments = Collections.emptyMap()) {
        Errors errors = setupErrorsProperty(instance)

        Validator validator = getValidator()
        if (validator == null) return true

        boolean valid = true
        boolean evict = false
        boolean deepValidate = true
        Set validatedFields = null
        if (validatedFieldsList != null) {
            validatedFields = new HashSet(validatedFieldsList)
        }

        if (arguments?.containsKey(ARGUMENT_DEEP_VALIDATE)) {
            deepValidate = ClassUtils.getBooleanFromMap(ARGUMENT_DEEP_VALIDATE, arguments)
        }

        if (arguments?.containsKey(ARGUMENT_EVICT)) {
            evict = ClassUtils.getBooleanFromMap(ARGUMENT_EVICT, arguments)
        }

        fireEvent(instance, validatedFieldsList)

        getHibernateTemplate().execute { Session session ->
            FlushMode previous = session.getHibernateFlushMode()
            session.setHibernateFlushMode(FlushMode.MANUAL)
            try {
                if (validator instanceof CascadingValidator) {
                    ((CascadingValidator) validator).validate instance, errors, deepValidate
                } else if (validator instanceof org.grails.datastore.gorm.validation.CascadingValidator) {
                    ((org.grails.datastore.gorm.validation.CascadingValidator) validator).validate instance, errors, deepValidate
                } else {
                    validator.validate instance, errors
                }
            } finally {
                if (!errors.hasErrors()) {
                    session.setHibernateFlushMode(previous)
                }
            }
        }

        int oldErrorCount = errors.errorCount
        errors = filterErrors(errors, validatedFields, instance)

        if (errors.hasErrors()) {
            valid = false
            if (evict) {
                if (getHibernateTemplate().contains(instance)) {
                    getHibernateTemplate().evict(instance)
                }
            }
        }

        if (errors.errorCount != oldErrorCount) {
            setErrors(instance, errors)
        }

        return valid
    }

    private void fireEvent(Object target, List<?> validatedFieldsList) {
        ValidationEvent event = new ValidationEvent(getHibernateDatastore(), target)
        event.setValidatedFields(validatedFieldsList)
        getHibernateDatastore().getApplicationEventPublisher().publishEvent(event)
    }

    @SuppressWarnings('rawtypes')
    private static Errors filterErrors(Errors errors, Set validatedFields, Object target) {
        if (validatedFields == null) return errors

        ValidationErrors result = new ValidationErrors(target)

        final List allErrors = errors.getAllErrors()
        for (Object allError : allErrors) {
            ObjectError error = (ObjectError) allError
            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error
                if (!validatedFields.contains(fieldError.getField())) continue
            }
            result.addError(error)
        }

        return result
    }

    protected static Errors setupErrorsProperty(Object target) {
        HibernateRuntimeUtils.setupErrorsProperty target
    }
}
