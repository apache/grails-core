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
package org.grails.datastore.gorm.validation.jakarta

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.NotBlank

import org.springframework.validation.Errors

import spock.lang.Specification

class ConstraintViolationUtilsSpec extends Specification {

    Validator validator = Validation.byDefaultProvider().configure().buildValidatorFactory().getValidator()

    void "converts a ConstraintViolationException to Errors using the target's simple class name"() {
        given:
        def target = new Product(name: '')
        Set<ConstraintViolation<Product>> violations = validator.validate(target)
        def exception = new ConstraintViolationException(violations)

        when:
        Errors errors = ConstraintViolationUtils.asErrors(target, exception)

        then:
        errors.objectName == 'Product'
        errors.hasFieldErrors('name')
        errors.getFieldError('name').rejectedValue == ''
    }

    void "converts a set of ConstraintViolation instances to Errors"() {
        given:
        def target = new Product(name: '')
        Set<ConstraintViolation> violations = validator.validate(target) as Set<ConstraintViolation>

        when:
        Errors errors = ConstraintViolationUtils.asErrors(target, violations)

        then:
        errors.objectName == 'Product'
        errors.hasFieldErrors('name')
        errors.getFieldError('name').rejectedValue == ''
    }
}

class Product {
    @NotBlank
    String name
}
