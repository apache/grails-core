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

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.GroupSequence
import jakarta.validation.Payload
import jakarta.validation.Valid
import jakarta.validation.Validation
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size
import jakarta.validation.groups.ConvertGroup
import jakarta.validation.groups.Default

import org.springframework.validation.DataBinder
import org.springframework.validation.Errors
import org.springframework.validation.SmartValidator
import org.springframework.validation.ValidationUtils
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
                PublishedBookNote, SharedChapterBook, SharedChapter, SequencedBook, SequencedPart, CascadingShelf, ReviewedBook,
                ValidatedDocument, ValidatedReport, TitledBook, DetailedBook, ExtendedOwner, ExtendedChild, SequencedOwner, SequencedRoot,
                SequencedChild, SequencedGrandchild)
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

    void "the validator validates in the groups given as validation hints, cascading with them"() {
        given: "a book violating the annotations of both groups, with a chapter marked @Valid and a note"
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(PublishedBook.name))
        PublishedBook book = new PublishedBook(label: '', code: null)
        book.chapter = new PublishedChapter(heading: '', book: book)
        book.note = new PublishedBookNote(summary: '', book: book)
        Errors errors = new ValidationErrors(book)

        when: "it is validated with the OnPublish group as a validation hint, as ValidationUtils and a DataBinder validate"
        ValidationUtils.invokeValidator(validator, book, errors, OnPublish)

        then: "the book is validated in OnPublish, so its block reports the null its default group annotation would"
        validator instanceof SmartValidator
        errors.getFieldError('label').code == 'NotBlank'
        errors.getFieldError('code').code == 'nullable'

        and: "its chapter, marked @Valid, in OnPublish too, and its note in the default group"
        errors.getFieldError('chapter.heading').code == 'NotBlank'
        !errors.hasFieldErrors('chapter.draft')
        errors.getFieldError('note.text').code == 'NotNull'
        !errors.hasFieldErrors('note.summary')
        errors.errorCount == 4

        when: "a DataBinder validates it with the group"
        DataBinder binder = new DataBinder(book)
        binder.validator = validator
        binder.validate(OnPublish)

        then:
        binder.bindingResult.getFieldError('label').code == 'NotBlank'
        binder.bindingResult.getFieldError('chapter.heading').code == 'NotBlank'
        binder.bindingResult.errorCount == 4

        when: "it is validated without hints"
        errors = new ValidationErrors(book)
        ValidationUtils.invokeValidator(validator, book, errors)

        then: "it is validated in the default group, where the block admits the empty label and the chapter is checked in"
        !errors.hasFieldErrors('label')
        errors.getFieldError('code').code == 'NotNull'
        errors.getFieldError('chapter.draft').code == 'NotNull'
        errors.getFieldError('note.text').code == 'NotNull'
        errors.errorCount == 3
    }

    void "an instance associated twice, each association converting the default group to another group, is validated in both"() {
        given: "a chapter valid for publishing but not for archiving, as the opening and the closing of a book"
        SharedChapter chapter = new SharedChapter(heading: 'Shared', archiveNote: '')
        SharedChapterBook book = new SharedChapterBook(title: 'Groups', opening: chapter, closing: chapter)
        Errors errors = new ValidationErrors(book)

        expect: "the provider reports the violation of the closing"
        beanValidationPaths(book) == ['closing.archiveNote']

        when:
        registry.getValidator(mappingContext.getPersistentEntity(SharedChapterBook.name)).validate(book, errors)

        then: "so does GORM, having validated the chapter in OnPublish as the opening and in OnArchive as the closing"
        errors.getFieldError('closing.archiveNote').code == 'NotBlank'
        errors.errorCount == 1
    }

    void "a group sequence an association converts to is expanded before the conversions of the associated instance apply"() {
        given: "a part validated in a sequence of OnPublish, whose chapter converts OnPublish to OnArchive"
        SharedChapter chapter = new SharedChapter(heading: 'Shared', archiveNote: '')
        SequencedBook book = new SequencedBook(title: 'Sequence', part: new SequencedPart(heading: 'Part', chapter: chapter))
        Errors errors = new ValidationErrors(book)

        expect: "the provider validates the chapter in OnArchive"
        beanValidationPaths(book) == ['part.chapter.archiveNote']

        when:
        registry.getValidator(mappingContext.getPersistentEntity(SequencedBook.name)).validate(book, errors)

        then: "and so does GORM"
        errors.getFieldError('part.chapter.archiveNote').code == 'NotBlank'
        errors.errorCount == 1
    }

    void "a group sequence none of whose groups is converted stops at the first group with a violation"() {
        given: "an epilogue violating both groups of the sequence it is validated in"
        SequencedBook book = new SequencedBook(title: 'Sequence', epilogue: new SharedChapter(heading: '', archiveNote: ''))
        Errors errors = new ValidationErrors(book)

        expect: "the provider stops after OnPublish"
        beanValidationPaths(book) == ['epilogue.heading']

        when:
        registry.getValidator(mappingContext.getPersistentEntity(SequencedBook.name)).validate(book, errors)

        then: "and so does GORM"
        errors.getFieldError('epilogue.heading').code == 'NotBlank'
        !errors.hasFieldErrors('epilogue.archiveNote')
        errors.errorCount == 1
    }

    void "an association marked @Valid of an entity without constraint annotations is validated in the group its @ConvertGroup converts to"() {
        given: "a shelf without an annotated property, whose book converts the default group to OnPublish"
        CascadingShelf shelf = new CascadingShelf(name: 'shelf', book: new ReviewedBook(title: '', isbn: null))
        Errors errors = new ValidationErrors(shelf)

        expect: "the provider validates the book in OnPublish"
        beanValidationPaths(shelf) == ['book.title']

        when:
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(CascadingShelf.name))
        validator.validate(shelf, errors)

        then: "the shelf gets the validator honouring the conversion, so GORM does the same"
        validator instanceof JakartaPersistentEntityValidator
        errors.getFieldError('book.title').code == 'NotBlank'
        !errors.hasFieldErrors('book.isbn')
        errors.errorCount == 1
    }

    void "a subclass declaring no annotation of its own is validated with the annotations it inherits"() {
        given:
        ValidatedReport report = new ValidatedReport(title: '')
        Errors errors = new ValidationErrors(report)

        expect:
        beanValidationPaths(report) == ['title']

        when:
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(ValidatedReport.name))
        validator.validate(report, errors)

        then:
        validator instanceof JakartaPersistentEntityValidator
        errors.getFieldError('title').code == 'NotBlank'
        errors.errorCount == 1
        !((ConstrainedEntity) validator).constrainedProperties.title.nullable
    }

    void "an entity whose only constraint is a class level one is validated with it"() {
        given:
        TitledBook book = new TitledBook(title: 'Same', subtitle: 'Same')
        Errors errors = new ValidationErrors(book)

        expect:
        beanValidationPaths(book) == ['']

        when:
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(TitledBook.name))
        validator.validate(book, errors)

        then:
        validator instanceof JakartaPersistentEntityValidator
        errors.errorCount == 1
        errors.globalErrors*.code == ['DistinctTitles']
    }

    void "a property marked @Valid that is not a persistent association is cascaded to by Bean Validation"() {
        given: "a book whose transient details violate an annotation"
        DetailedBook book = new DetailedBook(title: 'Details', details: new BookDetails(summary: ''))
        Errors errors = new ValidationErrors(book)
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(DetailedBook.name))

        expect:
        beanValidationPaths(book) == ['details.summary']

        when:
        validator.validate(book, errors)

        then: "the details are validated under their path"
        errors.getFieldError('details.summary').code == 'NotBlank'
        errors.errorCount == 1

        when: "validation does not cascade to the associations"
        errors = new ValidationErrors(book)
        ((CascadingValidator) validator).validate(book, errors, false)

        then: "the details, which are no association, are validated all the same"
        errors.getFieldError('details.summary').code == 'NotBlank'
        errors.errorCount == 1
    }

    void "a group of the owner no conversion converts is validated alone on the associated instance, without the converted groups it extends"() {
        given: "a child whose association converts Base, which the Extended group of its owner extends"
        ExtendedOwner owner = new ExtendedOwner(name: 'owner', child: new ExtendedChild(retired: 'yes', label: '', note: ''))
        Errors errors = new ValidationErrors(owner)
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(ExtendedOwner.name))

        expect: "the provider validates the child in Extended alone and in Converted, not in Base"
        beanValidationPaths(owner, Extended) == ['child.label', 'child.note']

        when:
        ValidationUtils.invokeValidator(validator, owner, errors, Extended)

        then: "and so does GORM"
        errors.getFieldError('child.label').code == 'NotBlank'
        errors.getFieldError('child.note').code == 'NotBlank'
        !errors.hasFieldErrors('child.retired')
        errors.errorCount == 2

        when: "the owner is validated in Base"
        errors = new ValidationErrors(owner)
        ValidationUtils.invokeValidator(validator, owner, errors, Base)

        then: "the child is validated in Converted alone"
        beanValidationPaths(owner, Base) == ['child.label']
        errors.getFieldError('child.label').code == 'NotBlank'
        errors.errorCount == 1
    }

    void "a group sequence an association converts to stops at the first group with a violation of the associated instance or of its own associations"() {
        given: "a child validated in StepA then StepB, whose grandchild converts StepA to StepX and violates it"
        SequencedOwner owner = new SequencedOwner(name: 'owner',
                child: new SequencedChild(a: 'ok', b: '', grandchild: new SequencedGrandchild(x: '')))
        Errors errors = new ValidationErrors(owner)
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(SequencedOwner.name))

        expect: "the provider finds the violation of the grandchild in StepA and stops before StepB"
        beanValidationPaths(owner) == ['child.grandchild.x']

        when:
        validator.validate(owner, errors)

        then: "and so does GORM"
        errors.getFieldError('child.grandchild.x').code == 'NotBlank'
        !errors.hasFieldErrors('child.b')
        errors.errorCount == 1

        when: "the grandchild is valid"
        owner.child.grandchild.x = 'ok'
        errors = new ValidationErrors(owner)
        validator.validate(owner, errors)

        then: "the sequence goes on to StepB"
        beanValidationPaths(owner) == ['child.b']
        errors.getFieldError('child.b').code == 'NotBlank'
        errors.errorCount == 1
    }

    void "a group sequence given as a validation hint stops at the first group with a violation anywhere in the graph"() {
        given: "a root violating StepA, whose child violates StepB"
        SequencedRoot root = new SequencedRoot(a: '', child: new SequencedChild(a: 'ok', b: '', grandchild: new SequencedGrandchild(x: 'ok')))
        Errors errors = new ValidationErrors(root)
        SpringValidator validator = registry.getValidator(mappingContext.getPersistentEntity(SequencedRoot.name))

        expect: "the provider stops after StepA"
        beanValidationPaths(root, StepAThenB) == ['a']

        when:
        ValidationUtils.invokeValidator(validator, root, errors, StepAThenB)

        then: "and so does GORM: the child is not validated in StepB"
        errors.getFieldError('a').code == 'NotBlank'
        !errors.hasFieldErrors('child.b')
        errors.errorCount == 1

        when: "the root is valid in StepA"
        root.a = 'ok'
        errors = new ValidationErrors(root)
        ValidationUtils.invokeValidator(validator, root, errors, StepAThenB)

        then:
        beanValidationPaths(root, StepAThenB) == ['child.b']
        errors.getFieldError('child.b').code == 'NotBlank'
        errors.errorCount == 1
    }

    private static List<String> beanValidationPaths(Object instance, Class<?>... groups) {
        def factory = Validation.buildDefaultValidatorFactory()
        try {
            return factory.validator.validate(instance, groups)*.propertyPath*.toString().sort()
        }
        finally {
            factory.close()
        }
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

interface OnArchive {}

@GroupSequence([OnPublish])
interface PublishingSequence {}

@GroupSequence([OnPublish, OnArchive])
interface PublishingThenArchiving {}

@Entity
@Validated
class SharedChapterBook {
    @NotNull
    String title

    @Valid
    @ConvertGroup(from = Default, to = OnPublish)
    SharedChapter opening

    @Valid
    @ConvertGroup(from = Default, to = OnArchive)
    SharedChapter closing
}

@Entity
@Validated
class SharedChapter {
    @NotBlank(groups = OnPublish)
    String heading

    @NotBlank(groups = OnArchive)
    String archiveNote

    static belongsTo = [SharedChapterBook, SequencedBook, SequencedPart]
}

@Entity
@Validated
class SequencedBook {
    @NotNull
    String title

    @Valid
    @ConvertGroup(from = Default, to = PublishingSequence)
    SequencedPart part

    @Valid
    @ConvertGroup(from = Default, to = PublishingThenArchiving)
    SharedChapter epilogue
}

@Entity
@Validated
class SequencedPart {
    @NotNull
    String heading

    @Valid
    @ConvertGroup(from = OnPublish, to = OnArchive)
    SharedChapter chapter

    static belongsTo = [SequencedBook]
}

@Entity
class CascadingShelf {
    String name

    @Valid
    @ConvertGroup(from = Default, to = OnPublish)
    ReviewedBook book
}

@Entity
class ReviewedBook {
    @NotBlank(groups = OnPublish)
    String title

    @NotNull
    String isbn

    static belongsTo = [CascadingShelf]
}

@Entity
class ValidatedDocument {
    @NotBlank
    String title
}

@Entity
class ValidatedReport extends ValidatedDocument {
    String summary
}

@Constraint(validatedBy = [DistinctTitlesValidator])
@Target([ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@interface DistinctTitles {

    String message() default 'the subtitle must differ from the title'

    Class<?>[] groups() default []

    Class<? extends Payload>[] payload() default []
}

class DistinctTitlesValidator implements ConstraintValidator<DistinctTitles, TitledBook> {

    @Override
    boolean isValid(TitledBook book, ConstraintValidatorContext context) {
        book == null || book.title == null || book.title != book.subtitle
    }
}

@Entity
@DistinctTitles
class TitledBook {
    String title
    String subtitle
}

@Entity
class DetailedBook {
    @NotNull
    String title

    @Valid
    BookDetails details

    static transients = ['details']
}

class BookDetails {
    @NotBlank
    String summary
}

interface Base {}

interface Extended extends Base {}

interface Converted {}

@Entity
class ExtendedOwner {
    @NotNull
    String name

    @Valid
    @ConvertGroup(from = Base, to = Converted)
    ExtendedChild child
}

@Entity
class ExtendedChild {
    @Null(groups = Base)
    String retired

    @NotBlank(groups = Converted)
    String label

    @NotBlank(groups = Extended)
    String note

    static belongsTo = [ExtendedOwner]
}

interface StepA {}

interface StepB {}

interface StepX {}

@GroupSequence([StepA, StepB])
interface StepAThenB {}

@Entity
class SequencedOwner {
    @NotNull
    String name

    @Valid
    @ConvertGroup(from = Default, to = StepAThenB)
    SequencedChild child
}

@Entity
class SequencedRoot {
    @NotBlank(groups = StepA)
    String a

    @Valid
    SequencedChild child
}

@Entity
class SequencedChild {
    @NotBlank(groups = StepA)
    String a

    @NotBlank(groups = StepB)
    String b

    @Valid
    @ConvertGroup(from = StepA, to = StepX)
    SequencedGrandchild grandchild

    static belongsTo = [SequencedOwner, SequencedRoot]
}

@Entity
class SequencedGrandchild {
    @NotBlank(groups = StepX)
    String x

    static belongsTo = [SequencedChild]
}
