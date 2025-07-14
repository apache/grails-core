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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import jakarta.validation.executable.ExecutableValidator
import org.grails.datastore.gorm.GormValidateable
import org.springframework.validation.beanvalidation.SpringValidatorAdapter

/**
 * A validator adapter that applies translates the constraint errors into the Errors object of a GORM entity
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class GormValidatorAdapter extends SpringValidatorAdapter {

    final Validator thisValidator

    GormValidatorAdapter(Validator targetValidator) {
        super(targetValidator)
        thisValidator = targetValidator
    }

    @Override
    def <T> Set<ConstraintViolation<T>> validate(T object, Class<?>[] groups) {
        def constraintViolations = super.validate(object, groups)
        if (object instanceof GormValidateable) {
            def errors = ((GormValidateable) object).getErrors()
            processConstraintViolations(constraintViolations, errors)
        }
        return constraintViolations
    }

    @Override
    @CompileDynamic
    ExecutableValidator forExecutables() {
        return thisValidator.forExecutables()
    }
}
