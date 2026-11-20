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
package grails.gorm.validation

import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.GormKeyValueMappingFactory
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormMappingConfigurationStrategy
import org.grails.datastore.mapping.validation.ValidationErrors
import org.grails.datastore.mapping.validation.ValidatorRegistry
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.Entity

/**
 * Demonstrates the Grails 8 default: an unconstrained persistent property is nullable by default,
 * aligning Grails with the rest of the JVM persistence/validation ecosystem. This spec deliberately
 * does NOT opt out (no {@code grails.gorm.default.constraints} override), so it exercises the real
 * framework default applied by {@code DefaultConstraintEvaluator}.
 */
class NullableByDefaultSpec extends Specification {

    @Shared Validator widgetValidator

    void setupSpec() {
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)

        PersistentEntity widgetEntity = mappingContext.addPersistentEntity(Widget)

        ValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        widgetValidator = registry.getValidator(widgetEntity)
    }

    void "an unconstrained property is nullable by default"() {
        given: "a widget whose only populated property is the explicitly-required one"
        Widget widget = new Widget(requiredName: 'gizmo')
        Errors errors = new ValidationErrors(widget)

        when:
        widgetValidator.validate(widget, errors)

        then: "the unconstrained properties are valid while null - they are nullable by default"
        !errors.hasErrors()
        !errors.getFieldError('description')
        !errors.getFieldError('quantity')
    }

    void "a property with an explicit nullable: false constraint is still required"() {
        given: "a widget missing the explicitly-required property"
        Widget widget = new Widget()
        Errors errors = new ValidationErrors(widget)

        when:
        widgetValidator.validate(widget, errors)

        then: "only the explicitly-required property is rejected; the unconstrained ones are not"
        errors.hasErrors()
        errors.getFieldError('requiredName')?.code == 'nullable'
        !errors.getFieldError('description')
        !errors.getFieldError('quantity')
    }

    void "grails.gorm.default.nullable = false restores required-by-default"() {
        given: "a validator built with the YAML-friendly flag disabled"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity widgetEntity = mappingContext.addPersistentEntity(Widget)

        ConnectionSourceSettings settings = new ConnectionSourceSettings()
        settings.default.nullable = false
        Validator validator = new DefaultValidatorRegistry(mappingContext, settings).getValidator(widgetEntity)

        and: "a widget whose unconstrained properties are null"
        Widget widget = new Widget(requiredName: 'gizmo')
        Errors errors = new ValidationErrors(widget)

        when:
        validator.validate(widget, errors)

        then: "the unconstrained properties are required again - the flag restored legacy behaviour"
        errors.hasErrors()
        errors.getFieldError('description')?.code == 'nullable'
        errors.getFieldError('quantity')?.code == 'nullable'
    }
}

@Entity
class Widget {
    String description
    Integer quantity
    String requiredName

    static constraints = {
        requiredName nullable: false
    }
}
