/* Copyright 2004-2005 the original author or authors.
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
package org.grails.datastore.gorm.validation;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Extends the default Spring Validator interface and provides an additional method that specifies whether
 * validation should cascade into associations.
 */
public interface CascadingValidator extends Validator, grails.gorm.validation.CascadingValidator {

    /**
     * An extended version of the validate(errors,obj) method that takes an additional argument specifying whether
     * the Validator should cascade into associations or not.
     *
     * @param obj The Object to validate
     * @param errors The Spring Errors instance
     * @param cascade True if validation should cascade into associations
     *
     * @see org.springframework.validation.Errors
     * @see org.springframework.validation.Validator
     * @see org.springframework.validation.Validator#validate(Object, org.springframework.validation.Errors)
     */
    void validate(Object obj, Errors errors, boolean cascade);
}
