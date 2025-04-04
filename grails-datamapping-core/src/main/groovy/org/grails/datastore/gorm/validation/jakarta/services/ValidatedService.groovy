package org.grails.datastore.gorm.validation.jakarta.services

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.validation.jakarta.ConstraintViolationUtils
import org.grails.datastore.gorm.validation.jakarta.JakartaValidatorRegistry
import org.grails.datastore.mapping.services.Service
import org.grails.datastore.mapping.validation.ValidationException
import org.springframework.validation.Errors

import jakarta.validation.Configuration
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.ParameterNameProvider
import jakarta.validation.Validation
import jakarta.validation.ValidatorFactory
import jakarta.validation.executable.ExecutableValidator
import java.lang.reflect.Method

/**
 * A service that is validated by jakarta.validation
 *
 * @author Graeme Rocher
 */
@CompileStatic
trait ValidatedService<T> extends Service<T> {

    /**
     * The parameter name provided for this service
     */
    ParameterNameProvider parameterNameProvider

    /**
     * The validator factory
     */
    private ValidatorFactory validatorFactoryInstance

    private Map<Method, ExecutableValidator> executableValidatorMap = new LinkedHashMap<Method, ExecutableValidator>().withDefault {
        validatorFactory.getValidator().forExecutables()
    }

    /**
     * @return The validator factory for this service
     */
    ValidatorFactory getValidatorFactory() {
        if(validatorFactoryInstance == null) {

            Configuration configuration
            if(datastore != null) {
                configuration = JakartaValidatorRegistry.buildConfigurationFor(
                        datastore.mappingContext,
                        datastore.mappingContext.validatorRegistry.messageSource
                )
            }
            else {
                configuration = Validation.byDefaultProvider()
                                            .configure()
                configuration = configuration.ignoreXmlConfiguration()
            }
            if(parameterNameProvider != null) {
                configuration = configuration.parameterNameProvider(parameterNameProvider)
            }
            validatorFactoryInstance = configuration.buildValidatorFactory()
        }
        return validatorFactoryInstance
    }

    /**
     * Validate the given method for the given arguments
     *
     * @param instance The instance
     * @param method The method
     * @param args The arguments
     *
     * @throws ConstraintViolationException If a validation error occurs
     */
    void jakartaValidate(Object instance, Method method, Object...args) throws ConstraintViolationException {
        ExecutableValidator validator = executableValidatorMap.get(method)
        Set<ConstraintViolation<Object>> constraintViolations = validator.validateParameters(instance, method, args)
        if(!constraintViolations.isEmpty()) {
            throw new ConstraintViolationException(constraintViolations)
        }
    }

    /**
     * Validate the given method for the given arguments
     *
     * @param instance The instance
     * @param method The method
     * @param args The arguments
     *
     * @throws ValidationException If a validation error occurs
     */
    void validate(Object instance, Method method, Object...args) throws ValidationException {
        ExecutableValidator validator = executableValidatorMap.get(method)
        Set<ConstraintViolation<Object>> constraintViolations = validator.validateParameters(instance, method, args)
        if(!constraintViolations.isEmpty()) {
            throw ValidationException.newInstance("Validation failed for method: $method.name ", asErrors(instance, constraintViolations))
        }
    }

    /**
     * Converts a ConstraintViolationException to errors
     *
     * @param object The validated object
     * @param e The exception
     * @return The errors
     */
    Errors asErrors(Object object, ConstraintViolationException e) {
        ConstraintViolationUtils.asErrors(object, e)
    }

    /**
     * Converts a ConstraintViolationException to errors
     *
     * @param object The validated object
     * @param e The exception
     * @return The errors
     */
    Errors asErrors(Object object, Set<ConstraintViolation> constraintViolations) {
        ConstraintViolationUtils.asErrors(object, constraintViolations)
    }
}