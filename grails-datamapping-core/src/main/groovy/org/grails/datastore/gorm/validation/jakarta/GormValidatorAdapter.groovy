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

import org.springframework.validation.Errors
import org.springframework.validation.beanvalidation.SpringValidatorAdapter

import grails.gorm.validation.ConstrainedEntity
import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.PersistentEntityValidator
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.validation.CascadingValidator

/**
 * A validator adapter that applies translates the constraint errors into the Errors object of a GORM entity.
 *
 * When created for a persistent entity it also validates the GORM constraints of the entity (its {@code constraints}
 * block) and exposes the constraints of the entity, including the ones describing its Bean Validation annotations,
 * as a {@link ConstrainedEntity}.
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class GormValidatorAdapter extends SpringValidatorAdapter implements CascadingValidator, ConstrainedEntity {

    public static final ThreadLocal<Boolean> CASCADE_VALIDATION = new ThreadLocal<>()

    final Validator thisValidator

    /**
     * The validator for the GORM constraints of the entity, null when only Bean Validation applies
     */
    final PersistentEntityValidator entityValidator

    /**
     * The constraints of the entity, including the ones describing its Bean Validation annotations
     */
    final Map<String, ConstrainedProperty> constrainedProperties

    GormValidatorAdapter(Validator targetValidator) {
        this(targetValidator, null, Collections.<String, ConstrainedProperty> emptyMap())
    }

    /**
     * @param targetValidator The Bean Validation validator
     * @param entityValidator The validator for the GORM constraints of the entity, may be null
     * @param constrainedProperties The constraints of the entity to expose
     * @since 8.0.1
     */
    GormValidatorAdapter(Validator targetValidator, PersistentEntityValidator entityValidator, Map<String, ConstrainedProperty> constrainedProperties) {
        super(targetValidator)
        this.thisValidator = targetValidator
        this.entityValidator = entityValidator
        this.constrainedProperties = Collections.unmodifiableMap(constrainedProperties)
    }

    @Override
    void validate(Object target, Errors errors) {
        validate(target, errors, true)
    }

    @Override
    void validate(Object obj, Errors errors, boolean cascade) {
        // the GORM validator cascades to the associations with their own validators, so Bean Validation must not
        // cascade as well or the associated instances would be validated twice
        CASCADE_VALIDATION.set(entityValidator == null ? cascade : Boolean.FALSE)
        try {
            entityValidator?.validate(obj, errors, cascade)
            super.validate(obj, errors)
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

    @Override
    @CompileDynamic
    ExecutableValidator forExecutables() {
        return thisValidator.forExecutables()
    }
}
