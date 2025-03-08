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
import org.grails.datastore.gorm.validation.CascadingValidator
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.validation.ValidationErrors
import org.grails.orm.hibernate.support.HibernateRuntimeUtils
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.hibernate.Session
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.Validator

@CompileStatic
abstract class AbstractHibernateGormValidationApi<D> extends GormValidationApi<D> {

    public static final String ARGUMENT_DEEP_VALIDATE = "deepValidate";
    private static final String ARGUMENT_EVICT = "evict";


    protected ClassLoader classLoader
    protected AbstractHibernateDatastore datastore
    protected IHibernateTemplate hibernateTemplate

    protected AbstractHibernateGormValidationApi(Class<D> persistentClass, AbstractHibernateDatastore datastore, ClassLoader classLoader) {
        super(persistentClass, datastore)
        this.classLoader = classLoader
        this.datastore = datastore
    }


    @Override
    boolean validate(D instance, Map arguments = Collections.emptyMap()) {
        validate(instance, null, arguments)
    }

    boolean validate(D instance, List validatedFieldsList, Map arguments = Collections.emptyMap()) {
        Errors errors = setupErrorsProperty(instance);

        Validator validator = getValidator()
        if(validator == null) return true

        Boolean valid = Boolean.TRUE
        // should evict?
        boolean evict = false
        boolean deepValidate = true
        Set validatedFields = null
        if(validatedFieldsList != null) {
            validatedFields = new HashSet(validatedFieldsList)
        }

        if (arguments?.containsKey(ARGUMENT_DEEP_VALIDATE)) {
            deepValidate = ClassUtils.getBooleanFromMap(ARGUMENT_DEEP_VALIDATE, arguments)
        }

        evict = ClassUtils.getBooleanFromMap(ARGUMENT_EVICT, arguments);

        fireEvent instance, validatedFieldsList


        hibernateTemplate.execute { Session session ->

            def previous = readPreviousFlushMode(session)
            applyManualFlush(session)
            try {
                if (validator instanceof CascadingValidator) {
                    ((CascadingValidator)validator).validate instance, errors, deepValidate
                } else if (validator instanceof grails.gorm.validation.CascadingValidator) {
                    ((grails.gorm.validation.CascadingValidator) validator).validate instance, errors, deepValidate
                } else {
                    validator.validate instance, errors
                }
            } finally {
                if(!errors.hasErrors()) {
                    restoreFlushMode(session, previous)
                }
            }

        }


        int oldErrorCount = errors.errorCount
        errors = filterErrors(errors, validatedFields, instance)

        if (errors.hasErrors()) {
            valid = Boolean.FALSE
            if (evict) {
                // if an boolean argument 'true' is passed to the method
                // and validation fails then the object will be evicted
                // from the session, ensuring it is not saved later when
                // flush is called
                if (hibernateTemplate.contains(instance)) {
                    hibernateTemplate.evict(instance)
                }
            }
        }

        // If the errors have been filtered, update the 'errors' object attached to the target.
        if (errors.errorCount != oldErrorCount) {
            setErrors(instance, errors)
        }

        return valid
    }

    abstract void restoreFlushMode(Session session, Object previousFlushMode)

    abstract Object readPreviousFlushMode(Session session)

    abstract applyManualFlush(Session session)

    private void fireEvent(Object target, List<?> validatedFieldsList) {
        ValidationEvent event = new ValidationEvent(datastore, target);
        event.setValidatedFields(validatedFieldsList);
        datastore.getApplicationEventPublisher().publishEvent(event);
    }

    @SuppressWarnings("rawtypes")
    private Errors filterErrors(Errors errors, Set validatedFields, Object target) {
        if (validatedFields == null) return errors;

        ValidationErrors result = new ValidationErrors(target);

        final List allErrors = errors.getAllErrors();
        for (Object allError : allErrors) {
            ObjectError error = (ObjectError) allError;

            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error;
                if (!validatedFields.contains(fieldError.getField())) continue;
            }

            result.addError(error);
        }

        return result;
    }

    /**
     * Initializes the Errors property on target.  The target will be assigned a new
     * Errors property.  If the target contains any binding errors, those binding
     * errors will be copied in to the new Errors property.
     *
     * @param target object to initialize
     * @return the new Errors object
     */
    protected Errors setupErrorsProperty(Object target) {
        HibernateRuntimeUtils.setupErrorsProperty target
    }
}
