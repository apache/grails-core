/*
 * Copyright 2015 the original author or authors.
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
package org.grails.datastore.gorm

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.validation.ValidationErrors
import org.springframework.validation.Errors
import org.springframework.validation.annotation.Validated

import jakarta.persistence.Transient


/**
 * A trait that adds GORM validation behavior to any class
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 * @since 4.0
 */
@CompileStatic
trait GormValidateable {

    @Transient
    private transient boolean skipValidate = false
    /**
     * The validation errors object
     */
    @Transient
    Errors errors

    /**
     * Marks this instance to skip validation
     *
     * @param shouldSkip True if validation should be skipped
     */
    void skipValidation(boolean shouldSkip) {
        this.skipValidate = shouldSkip
    }

    /**
     * @return Whether this instance should skip validation
     */
    boolean shouldSkipValidation() {
        // skip validation if validation set to true or validation handled by jakarta.validation
        return this.skipValidate
    }



    /**
     * Validates an instance for the given arguments
     *
     * @param arguments The arguments to use
     * @return True if the instance is valid
     */
    boolean validate(Map arguments) {
        currentGormValidationApi().validate this, arguments
    }

    /**
     * Validates an instance
     *
     * @param fields The list of fields to validate
     * @return True if the instance is valid
     */
    boolean validate(List fields) {
        currentGormValidationApi().validate this, fields
    }

    /**
     * Validates an instance
     *
     * @return True if the instance is valid
     */
    boolean validate() {
        currentGormValidationApi().validate this
    }

    /**
     * Obtains the errors for an instance
     * @return The {@link Errors} instance
     */
    @Transient
    Errors getErrors() {
        if(errors == null) {
            errors = new ValidationErrors(this)
        }
        errors
    }

    /**
     * Clears any errors that exist on an instance
     */
    void clearErrors() {
        errors = new ValidationErrors(this)
    }

    /**
     * Tests whether an instance has any errors
     * @return True if errors exist
     */
    Boolean hasErrors() {
        getErrors().hasErrors()
    }

    /**
     * Used to obtain the {@link GormValidationApi} instance. This method is used internally by the framework and SHOULD NOT be called by the developer
     */
    private GormValidationApi currentGormValidationApi() {
        GormEnhancer.findValidationApi(getClass())
    }
}