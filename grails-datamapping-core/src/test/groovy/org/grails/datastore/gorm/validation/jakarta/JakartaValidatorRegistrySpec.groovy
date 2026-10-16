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

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

import org.springframework.validation.Errors
import org.springframework.validation.Validator as SpringValidator
import org.springframework.validation.annotation.Validated

import grails.gorm.annotation.Entity
import grails.gorm.validation.ConstrainedEntity
import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.PersistentEntityValidator
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.validation.ValidationErrors
import spock.lang.Specification

class JakartaValidatorRegistrySpec extends Specification {

    MappingContext mappingContext = new KeyValueMappingContext("test")
    JakartaValidatorRegistry registry

    void setup() {
        mappingContext.addPersistentEntities(PlainBook, ValidatedBook, ConstrainedBook, MysteriousBook)
        mappingContext.initialize()
        registry = new JakartaValidatorRegistry(mappingContext, new ConnectionSourceSettings())
    }

    void cleanup() {
        registry.close()
    }

    void "isAvailable reports that jakarta.validation is on the classpath"() {
        expect:
        JakartaValidatorRegistry.isAvailable()
    }

    void "returns a jakarta backed validator for entities annotated with @Validated"() {
        given:
        def entity = mappingContext.getPersistentEntity(ValidatedBook.name)

        when:
        SpringValidator validator = registry.getValidator(entity)

        then:
        validator instanceof GormValidatorAdapter
    }

    void "falls back to the default constraint based validator for entities without @Validated"() {
        given:
        def entity = mappingContext.getPersistentEntity(PlainBook.name)

        when:
        SpringValidator validator = registry.getValidator(entity)

        then:
        validator instanceof PersistentEntityValidator
    }

    void "the validator of an annotated entity exposes the constraints block and the annotations as constraints"() {
        given:
        def entity = mappingContext.getPersistentEntity(ConstrainedBook.name)

        when:
        SpringValidator validator = registry.getValidator(entity)

        then:
        validator instanceof ConstrainedEntity
        Map<String, ConstrainedProperty> constraints = ((ConstrainedEntity) validator).constrainedProperties
        !constraints.title.nullable
        constraints.code.maxSize == 5
        constraints.code.nullable
        !constraints.isbn.nullable
        constraints.isbn.maxSize == 13

        and: "the same validator is returned every time"
        registry.getValidator(entity).is(validator)
    }

    void "the annotations and the constraints block are both validated, with a single error per violation"() {
        given:
        def entity = mappingContext.getPersistentEntity(ConstrainedBook.name)
        SpringValidator validator = registry.getValidator(entity)
        ConstrainedBook book = new ConstrainedBook(code: 'toolong')
        Errors errors = new ValidationErrors(book)

        when:
        validator.validate(book, errors)

        then:
        errors.getFieldErrorCount('title') == 1
        errors.getFieldError('title').code == 'NotNull'
        errors.getFieldErrorCount('code') == 1
        errors.getFieldError('code').code == 'Size'
        errors.getFieldErrorCount('isbn') == 1
        errors.getFieldError('isbn').code == 'nullable'
        errors.errorCount == 3

        when: "a null title is also required by the constraints block"
        book = new ConstrainedBook(code: 'ok', isbn: '1234567890123')
        errors = new ValidationErrors(book)
        validator.validate(book, errors)

        then: "Bean Validation reports it once"
        errors.errorCount == 1
        errors.getFieldError('title').code == 'NotNull'
    }

    void "a custom constraint is still validated by Bean Validation"() {
        given:
        def entity = mappingContext.getPersistentEntity(MysteriousBook.name)
        SpringValidator validator = registry.getValidator(entity)
        MysteriousBook book = new MysteriousBook(secret: 'plain')
        Errors errors = new ValidationErrors(book)

        when:
        validator.validate(book, errors)

        then:
        errors.getFieldError('secret').code == 'Mysterious'
        ((ConstrainedEntity) validator).constrainedProperties.secret.appliedConstraints*.name == ['nullable']
    }

    void "exposes the underlying jakarta ValidatorFactory operations"() {
        expect:
        registry.validator != null
        registry.usingContext() != null
        registry.messageInterpolator != null
        registry.traversableResolver != null
        registry.constraintValidatorFactory != null
        registry.parameterNameProvider != null
        registry.clockProvider != null
    }

}

@Entity
class PlainBook {
    String title
}

@Entity
@Validated
class ValidatedBook {
    @NotBlank
    String title
}

@Entity
@Validated
class ConstrainedBook {
    @NotNull
    String title
    @Size(max = 5)
    String code
    String isbn

    static constraints = {
        title nullable: false
        isbn nullable: false, maxSize: 13
    }
}

@Entity
@Validated
class MysteriousBook {
    @Mysterious
    String secret
}
