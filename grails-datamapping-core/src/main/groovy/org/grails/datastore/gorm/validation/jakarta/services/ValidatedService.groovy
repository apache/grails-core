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

package org.grails.datastore.gorm.validation.jakarta.services

import java.lang.reflect.Method

import groovy.transform.CompileStatic

import jakarta.validation.Configuration
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.ParameterNameProvider
import jakarta.validation.Validation
import jakarta.validation.ValidatorFactory
import jakarta.validation.executable.ExecutableValidator

import org.springframework.validation.Errors

import org.grails.datastore.gorm.validation.jakarta.ConstraintViolationUtils
import org.grails.datastore.gorm.validation.jakarta.JakartaValidatorRegistry
import org.grails.datastore.mapping.services.Service
import org.grails.datastore.mapping.validation.ValidationException

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
