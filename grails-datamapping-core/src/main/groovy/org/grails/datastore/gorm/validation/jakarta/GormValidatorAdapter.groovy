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
import jakarta.validation.metadata.ConstraintDescriptor

import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.beanvalidation.SpringValidatorAdapter

import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.validation.CascadingValidator

/**
 * A validator adapter that applies translates the constraint errors into the Errors object of a GORM entity
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class GormValidatorAdapter extends SpringValidatorAdapter implements CascadingValidator {

    /**
     * Whether the validation in progress on the thread cascades to the persistent associations of an entity. When
     * {@code false}, the GORM validator cascades to them, and Bean Validation cascades only through the properties
     * marked {@code @Valid} that are not persistent associations, see {@link MappingContextTraversableResolver}.
     */
    public static final ThreadLocal<Boolean> CASCADE_VALIDATION = new ThreadLocal<>()

    final Validator thisValidator

    GormValidatorAdapter(Validator targetValidator) {
        super(targetValidator)
        thisValidator = targetValidator
    }

    @Override
    void validate(Object obj, Errors errors, boolean cascade) {
        CASCADE_VALIDATION.set(cascade)
        try {
            validate(obj, errors)
        } finally {
            CASCADE_VALIDATION.remove()
        }
    }

    /**
     * Validates the object in the given groups, or in the default group when there are none
     *
     * @param obj The object to validate
     * @param errors The errors to report the violations to
     * @param cascade Whether validation cascades to the persistent associations of the entity through the
     * properties marked {@code @Valid}; it cascades through the other properties marked {@code @Valid} either way
     * @param groups The groups to validate
     * @since 8.0.1
     */
    void validate(Object obj, Errors errors, boolean cascade, Class<?>[] groups) {
        CASCADE_VALIDATION.set(cascade)
        try {
            validate(obj, errors, (Object[]) groups)
        } finally {
            CASCADE_VALIDATION.remove()
        }
    }

    /**
     * Validates the object in the given groups as {@link #validate(Object, Errors, boolean, Class[])} does,
     * reporting only the violations of the constraints declared in one of the reported groups. The provider
     * validates a group together with the groups it extends; when validation cascades from an object validated in
     * a group whose extended groups a {@code @ConvertGroup} converts, the associated object is validated in the
     * group alone, so the violations of the converted groups are left out.
     *
     * @param obj The object to validate
     * @param errors The errors to report the violations to
     * @param cascade Whether validation cascades to the persistent associations of the entity
     * @param groups The groups to validate
     * @param reportedGroups The groups whose violations to report, null to report every violation
     * @since 8.0.1
     */
    void validate(Object obj, Errors errors, boolean cascade, Class<?>[] groups, Set<Class<?>> reportedGroups) {
        if (reportedGroups == null) {
            validate(obj, errors, cascade, groups)
            return
        }
        CASCADE_VALIDATION.set(cascade)
        try {
            Set<ConstraintViolation<Object>> reported = new LinkedHashSet<>()
            for (ConstraintViolation<Object> violation : thisValidator.validate(obj, groups)) {
                if (!Collections.disjoint(violation.constraintDescriptor.groups, reportedGroups)) {
                    reported.add(violation)
                }
            }
            processConstraintViolations(reported, errors)
        } finally {
            CASCADE_VALIDATION.remove()
        }
    }

    @Override
    <T> Set<ConstraintViolation<T>> validate(T object, Class<?>[] groups) {
        def constraintViolations = super.validate(object, groups)
        if (object instanceof GormValidateable) {
            def errors = ((GormValidateable) object).getErrors()
            processConstraintViolations(constraintViolations, errors)
        }
        return constraintViolations
    }

    /**
     * Reports a violation of a class level constraint as an error of the nested path of the errors, such as
     * {@code lines[0]} when GORM cascades validation to an associated object, as Spring reports it when Bean
     * Validation cascades through {@code @Valid}. Without a nested path it remains a global error.
     */
    @Override
    protected void processConstraintViolations(Set<ConstraintViolation<Object>> violations, Errors errors) {
        String nestedPath = errors.getNestedPath()
        if (!nestedPath) {
            super.processConstraintViolations(violations, errors)
            return
        }
        String path = nestedPath.endsWith(Errors.NESTED_PATH_SEPARATOR)
                ? nestedPath.substring(0, nestedPath.length() - Errors.NESTED_PATH_SEPARATOR.length())
                : nestedPath
        for (ConstraintViolation<Object> violation : violations) {
            if (determineField(violation)) {
                super.processConstraintViolations(Collections.singleton(violation), errors)
                continue
            }
            FieldError fieldError = errors.getFieldError('')
            if (fieldError == null || !fieldError.isBindingFailure()) {
                ConstraintDescriptor<?> descriptor = violation.getConstraintDescriptor()
                errors.rejectValue('', determineErrorCode(descriptor),
                        getArgumentsForConstraint(errors.getObjectName(), path, descriptor), violation.getMessage())
            }
        }
    }

    @Override
    @CompileDynamic
    ExecutableValidator forExecutables() {
        return thisValidator.forExecutables()
    }
}
