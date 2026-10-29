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

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import jakarta.validation.groups.ConvertGroup
import jakarta.validation.groups.Default

import org.springframework.validation.Errors
import org.springframework.validation.Validator as SpringValidator
import org.springframework.validation.annotation.Validated

import grails.gorm.annotation.Entity
import grails.gorm.validation.CascadingValidator
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
        mappingContext.addPersistentEntities(PlainBook, ValidatedBook, ConstrainedBook, MysteriousBook,
                AnnotatedShelf, AnnotatedShelfBook, PlainShelf, GroupedBook, PublishingShelf, PublishedBook, PublishedChapter,
                PublishedBookNote)
        mappingContext.initialize()
        registry = new JakartaValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        mappingContext.validatorRegistry = registry
    }

    void cleanup() {
        registry.close()
    }

    void "isAvailable reports that jakarta.validation is on the classpath"() {
        expect:
        JakartaValidatorRegistry.isAvailable()
    }

    void "returns a jakarta backed entity validator for entities annotated with @Validated"() {
        given:
        def entity = mappingContext.getPersistentEntity(ValidatedBook.name)

        when:
        SpringValidator validator = registry.getValidator(entity)

        then: "it is the entity validator every consumer of the constraints looks for, backed by Bean Validation"
        validator instanceof JakartaPersistentEntityValidator
        validator instanceof PersistentEntityValidator
        ((JakartaPersistentEntityValidator) validator).beanValidator instanceof GormValidatorAdapter
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

    void "validation cascades to an annotated association, checking its annotations and its constraints block once"() {
        given:
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(AnnotatedShelf.name))
        AnnotatedShelf shelf = new AnnotatedShelf(name: 'shelf')
        shelf.books = [new AnnotatedShelfBook(label: '', code: null, shelf: shelf)] as Set
        Errors errors = new ValidationErrors(shelf)

        when:
        validator.validate(shelf, errors)

        then: "the annotation and the constraints block of the book are each reported once, under the association"
        errors.errorCount == 2
        errors.getFieldError('books[0].label').code == 'NotBlank'
        errors.getFieldError('books[0].code').code == 'nullable'

        when: "validation does not cascade"
        errors = new ValidationErrors(shelf)
        ((CascadingValidator) validator).validate(shelf, errors, false)

        then: "the book is not checked"
        !errors.hasErrors()
    }

    void "a plain entity cascades to an annotated association"() {
        given:
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(PlainShelf.name))
        PlainShelf shelf = new PlainShelf(name: 'shelf')
        shelf.books = [new ConstrainedBook(code: 'toolong', isbn: '1234567890123')] as Set
        Errors errors = new ValidationErrors(shelf)

        when:
        validator.validate(shelf, errors)

        then:
        validator instanceof PersistentEntityValidator
        !(validator instanceof JakartaPersistentEntityValidator)
        errors.errorCount == 2
        errors.getFieldError('books[0].title').code == 'NotNull'
        errors.getFieldError('books[0].code').code == 'Size'
    }

    void "an annotation of another group than the default one leaves the constraints block in force"() {
        given:
        def entity = mappingContext.getPersistentEntity(GroupedBook.name)
        SpringValidator validator = registry.getValidator(entity)
        GroupedBook book = new GroupedBook()
        Errors errors = new ValidationErrors(book)

        when:
        validator.validate(book, errors)

        then: "validate() does not run the group, so the block reports the null"
        errors.errorCount == 1
        errors.getFieldError('title').code == 'nullable'

        and: "the metadata says what is enforced"
        !((ConstrainedEntity) validator).constrainedProperties.title.nullable
    }

    void "an association marked @Valid is validated in the groups of its owner, converted as @ConvertGroup declares"() {
        given: "a book violating the annotations of both groups and its constraints block"
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(PublishingShelf.name))
        PublishingShelf shelf = new PublishingShelf(name: 'shelf')
        PublishedBook book = new PublishedBook(shelf: shelf)
        book.chapter = new PublishedChapter(heading: '', book: book)
        book.note = new PublishedBookNote(summary: '', book: book)
        shelf.books = [book] as Set
        Errors errors = new ValidationErrors(shelf)

        when:
        validator.validate(shelf, errors)

        then: "the book is validated in OnPublish, so its block reports the null its default group annotation would"
        errors.getFieldError('books[0].label').code == 'NotBlank'
        errors.getFieldErrorCount('books[0].label') == 1
        errors.getFieldError('books[0].code').code == 'nullable'

        and: "its chapter, marked @Valid, in OnPublish too"
        errors.getFieldError('books[0].chapter.heading').code == 'NotBlank'
        !errors.hasFieldErrors('books[0].chapter.draft')

        and: "its note, not marked @Valid, in the default group, as on its own"
        errors.getFieldError('books[0].note.text').code == 'NotNull'
        !errors.hasFieldErrors('books[0].note.summary')
        errors.errorCount == 4

        when: "the book is validated on its own"
        errors = new ValidationErrors(book)
        registry.getValidator(mappingContext.getPersistentEntity(PublishedBook.name)).validate(book, errors)

        then: "it is validated in the default group, and so is its chapter"
        errors.getFieldError('label').code == 'nullable'
        errors.getFieldError('code').code == 'NotNull'
        errors.getFieldError('chapter.draft').code == 'NotNull'
        errors.getFieldError('note.text').code == 'NotNull'
        errors.errorCount == 4
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

@Entity
@Validated
class AnnotatedShelf {
    @NotNull
    String name

    static hasMany = [books: AnnotatedShelfBook]
}

@Entity
@Validated
class AnnotatedShelfBook {
    @NotBlank
    String label
    String code

    static belongsTo = [shelf: AnnotatedShelf]

    static constraints = {
        code nullable: false
    }
}

@Entity
class PlainShelf {
    String name

    static hasMany = [books: ConstrainedBook]
}

interface OnPublish {}

@Entity
@Validated
class GroupedBook {
    @NotNull(groups = OnPublish)
    String title

    static constraints = {
        title nullable: false
    }
}

@Entity
@Validated
class PublishingShelf {
    @NotNull
    String name

    @Valid
    @ConvertGroup(from = Default, to = OnPublish)
    Set<PublishedBook> books

    static hasMany = [books: PublishedBook]
}

@Entity
@Validated
class PublishedBook {
    @NotBlank(groups = OnPublish)
    String label

    @NotNull
    String code

    @Valid
    PublishedChapter chapter

    PublishedBookNote note

    static belongsTo = [shelf: PublishingShelf]

    static constraints = {
        label nullable: false
        code nullable: false
    }
}

@Entity
@Validated
class PublishedChapter {
    @NotBlank(groups = OnPublish)
    String heading

    @NotNull
    String draft

    static belongsTo = [book: PublishedBook]
}

@Entity
@Validated
class PublishedBookNote {
    @NotBlank(groups = OnPublish)
    String summary

    @NotNull
    String text

    static belongsTo = [book: PublishedBook]
}
