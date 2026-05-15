/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm

import groovy.transform.CompileStatic

import jakarta.persistence.FlushModeType

import org.springframework.context.ApplicationEventPublisher
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.Validator

import grails.gorm.validation.CascadingValidator
import org.grails.datastore.gorm.support.BeforeValidateHelper
import org.grails.datastore.gorm.validation.ValidatorProvider
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.validation.ValidationErrors

/**
 * Methods used for validating GORM instances.
 *
 * @author Graeme Rocher
 * @param <D> the entity/domain class
 * @since 1.0
 */
@CompileStatic
class GormValidationApi<D> extends AbstractGormApi<D> {

    public static final String ARGUMENT_DEEP_VALIDATE = 'deepValidate'

    private Validator internalValidator
    BeforeValidateHelper beforeValidateHelper
    protected final MappingContext mappingContext
    protected final ApplicationEventPublisher eventPublisher
    protected final boolean hasDatastore
    protected final GormRegistry registry

    GormValidationApi(Class<D> persistentClass, Datastore datastore) {
        this(persistentClass, datastore, null)
    }

    GormValidationApi(Class<D> persistentClass, Datastore datastore, GormRegistry registry) {
        super(persistentClass, datastore)
        beforeValidateHelper = new BeforeValidateHelper()
        this.mappingContext = datastore?.mappingContext
        this.eventPublisher = datastore?.applicationEventPublisher
        this.hasDatastore = datastore != null
        this.registry = registry ?: GormEnhancer.getRegistry()
    }

    GormValidationApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver) {
        this(persistentClass, mappingContext, datastoreResolver, null)
    }

    GormValidationApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver, GormRegistry registry) {
        super(persistentClass, mappingContext, datastoreResolver)
        beforeValidateHelper = new BeforeValidateHelper()
        this.mappingContext = mappingContext
        this.eventPublisher = null // Will be resolved if needed
        this.hasDatastore = true
        this.registry = registry ?: GormEnhancer.getRegistry()
    }

    GormValidationApi<D> forQualifier(String qualifier) {
        if (!hasDatastore) return this
        DatastoreResolver resolver = new DatastoreResolver() {
            @Override Datastore resolve() { registry.apiResolver.findDatastore(persistentClass, qualifier) }
        }
        return new GormValidationApi<D>(persistentClass, mappingContext, resolver, registry)
    }

    GormValidationApi(Class<D> persistentClass, MappingContext mappingContext, ApplicationEventPublisher eventPublisher) {
        super(persistentClass, mappingContext, null)
        beforeValidateHelper = new BeforeValidateHelper()
        this.mappingContext = mappingContext
        this.eventPublisher = eventPublisher
        this.hasDatastore = false
        this.registry = GormEnhancer.getRegistry()
    }

    Validator getValidator() {
        if (internalValidator) {
            return internalValidator
        }
        Validator validator = null
        PersistentEntity persistentEntity = getGormPersistentEntity()
        if (persistentEntity instanceof ValidatorProvider) {
            validator = ((ValidatorProvider) persistentEntity).validator
        }
        if (!validator) {
            MappingContext currentMappingContext = getDatastore()?.getMappingContext() ?: this.mappingContext
            if (currentMappingContext) {
                validator = currentMappingContext.getEntityValidator(persistentEntity)
            }
        }
        return validator
    }

    void setValidator(Validator validator) {
        internalValidator = validator
    }

    private boolean doValidate(D instance, Map arguments, List fields) {
        FlushModeType previousFlushMode = null
        Session currentSession = null
        boolean deepValidate = true

        if (arguments?.containsKey(ARGUMENT_DEEP_VALIDATE)) {
            deepValidate = ClassUtils.getBooleanFromMap(ARGUMENT_DEEP_VALIDATE, arguments)
        }

        if (hasDatastore && datastore.hasCurrentSession()) {
            try {
                currentSession = datastore.currentSession
                previousFlushMode = currentSession.flushMode
                currentSession.setFlushMode(FlushModeType.COMMIT)
            } catch (IllegalStateException e) {
                // Ignore, session might be disconnected
                currentSession = null
            }
        }
        try {
            beforeValidateHelper.invokeBeforeValidate(instance, fields)
            fireEvent(instance, fields)

            Validator validator = getValidator()
            if (validator == null) {
                return true
            }

            ValidationErrors localErrors = new ValidationErrors(instance)

            Errors errors = getErrors(instance)

            for (error in errors.allErrors) {
                if (error instanceof FieldError) {
                    if (((FieldError) error).bindingFailure) {
                        localErrors.addError(error)
                    }
                } else {
                    localErrors.addError(error)
                }
            }

            if (validator instanceof CascadingValidator) {
                ((CascadingValidator) validator).validate(instance, localErrors, deepValidate)
            } else if (validator instanceof org.grails.datastore.gorm.validation.CascadingValidator) {
                ((org.grails.datastore.gorm.validation.CascadingValidator) validator).validate(instance, localErrors, deepValidate)
            } else {
                validator.validate(instance, localErrors)
            }

            if (fields) {
                localErrors = filterErrors(localErrors, fields as Set, instance)
            }

            setErrors(instance, localErrors)

            return !getErrors(instance).hasErrors()
        } finally {
            if (previousFlushMode != null) {
                currentSession.setFlushMode(previousFlushMode)
            }
        }
    }

    /**
     * Validates an instance for the given arguments
     *
     * @param instance The instance to validate
     * @param arguments The arguments to use
     * @return True if the instance is valid
     */
    boolean validate(D instance, Map arguments) {
        doValidate(instance, arguments, (List) null)
    }

    /**
     * Validates an instance
     *
     * @param instance The instance to validate
     * @param fields The list of fields to validate
     * @return True if the instance is valid
     */
    boolean validate(D instance, List fields) {
        doValidate(instance, (Map) null, fields)
    }

    private ValidationErrors filterErrors(ValidationErrors errors, Set validatedFields, Object target) {
        if (!validatedFields) return errors

        Errors result = new ValidationErrors(target)

        for (ObjectError error : errors.getAllErrors()) {

            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error
                if (!validatedFields.contains(fieldError.getField())) continue
            }

            result.addError(error)
        }

        return result
    }

    /**
     * Fire the validation event.
     * @param target The target instance.
     * @param fields The list of fields being validated, or null.
     */
    private void fireEvent(target, List fields) {
        ValidationEvent event = createValidationEvent(target)
        event.validatedFields = fields
        ApplicationEventPublisher publisher = eventPublisher
        if (publisher == null) {
            publisher = getDatastore()?.getApplicationEventPublisher()
        }
        publisher?.publishEvent(event)
    }

    protected ValidationEvent createValidationEvent(target) {
        new ValidationEvent(getDatastore(), target)
    }

    /**
     * Validates an instance
     *
     * @param instance The instance to validate
     * @return True if the instance is valid
     */
    boolean validate(D instance) {
        doValidate(instance, (Map) null, (List) null)
    }

    /**
     * Obtains the errors for an instance
     * @param instance The instance to obtain errors for
     * @return The {@link Errors} instance
     */
    Errors getErrors(D instance) {
        if (instance instanceof GormValidateable) {
            GormValidateable gv = (GormValidateable) instance
            def errors = gv.errors
            if (errors == null) {
                errors = resetErrors(instance)
            }
            return errors
        }
        else {
            Datastore ds = getDatastore()
            if (ds != null && ds.hasCurrentSession()) {
                try {
                    Errors errors = (Errors) ds.getCurrentSession().getAttribute(instance, GormProperties.ERRORS)
                    if (errors == null) {
                        errors = resetErrors(instance)
                    }
                    return errors
                } catch (IllegalStateException e) {
                    return new ValidationErrors(instance)
                }
            } else {
                return new ValidationErrors(instance)
            }
        }
    }

    protected Errors resetErrors(D instance) {
        def errors = new ValidationErrors(instance)
        setErrors(instance, errors)
        return errors
    }

    /**
     * Sets the errors for an instance
     * @param instance The instance
     * @param errors The errors
     */
    void setErrors(D instance, Errors errors) {
        if (instance instanceof GormValidateable) {
            GormValidateable gv = (GormValidateable) instance
            gv.errors = errors
        }
        else {
            Datastore ds = getDatastore()
            if (ds != null && ds.hasCurrentSession()) {
                try {
                    ds.getCurrentSession().setAttribute(instance, GormProperties.ERRORS, errors)
                } catch (IllegalStateException e) {
                    // Ignore, session might be disconnected
                }
            }
        }
    }

    /**
     * Clears any errors that exist on an instance
     * @param instance The instance
     */
    void clearErrors(D instance) {
        resetErrors(instance)
    }

    /**
     * Tests whether an instance has any errors
     * @param instance The instance
     * @return True if errors exist
     */
    boolean hasErrors(D instance) {
        if (instance instanceof GormValidateable) {
            GormValidateable gv = (GormValidateable) instance
            return gv.hasErrors()
        }
        else {
            Datastore ds = getDatastore()
            if (ds != null && ds.hasCurrentSession()) {
                try {
                    Errors errors = (Errors) ds.getCurrentSession().getAttribute(instance, GormProperties.ERRORS)
                    return errors?.hasErrors() ?: false
                } catch (IllegalStateException e) {
                    return false
                }
            }
            return false
        }
    }

    @Override
    org.springframework.transaction.PlatformTransactionManager getTransactionManager() {
        Datastore ds = getDatastore()
        if (ds instanceof org.grails.datastore.mapping.transactions.TransactionCapableDatastore) {
            return ((org.grails.datastore.mapping.transactions.TransactionCapableDatastore)ds).getTransactionManager()
        }
        return null
    }
}
