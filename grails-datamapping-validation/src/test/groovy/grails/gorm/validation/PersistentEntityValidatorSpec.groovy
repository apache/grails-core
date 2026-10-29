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

import org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.keyvalue.mapping.config.GormKeyValueMappingFactory
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormMappingConfigurationStrategy
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.validation.ValidationErrors
import org.grails.datastore.mapping.validation.ValidatorRegistry
import org.springframework.context.MessageSource
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.Entity
import jakarta.persistence.Transient

class PersistentEntityValidatorSpec extends Specification {
    @Shared Validator authorValidator

    void setupSpec() {
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)

        PersistentEntity authorEntity = mappingContext.addPersistentEntity(Author)
        mappingContext.addPersistentEntity(Book)
        mappingContext.addPersistentEntity(Publisher)

        ValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        authorValidator = registry.getValidator(authorEntity)
    }

    // beforeValidate on the initial save is part of the GormValidationApi doValidate() call
    @Issue('https://github.com/apache/grails-data-mapping/issues/1102')
    def "validation of root object does NOT trigger beforeValidate here"() {
        Author author = new Author()
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then:
        errors.hasErrors()
        errors.getFieldErrors('name')
    }

    @Issue('https://github.com/apache/grails-data-mapping/issues/1102')
    def "cascading validation triggers beforeValidate callback on to-many association"() {
        Author author = new Author(name: 'Author', books: [new Book()])
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then:
        !errors.hasErrors()
        author.books.first()
        author.books.first().name == "name"
    }

    @Issue('https://github.com/apache/grails-data-mapping/issues/1102')
    def "cascading validation triggers beforeValidate callback on to-one association"() {
        Author author = new Author(name: 'Author', publisher: new Publisher())
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then:
        !errors.hasErrors()
        author.publisher.name == "name"
    }

    @Issue('https://github.com/apache/grails-data-mapping/issues/1106')
    def "validation cascades by default if association is owned or has cascade options of PERSIST or MERGE"() {
        Author author = new Author(name: 'Author', publisher: new Publisher())
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then: "validate was called for this by default"
        author.publisher.validateCalled
    }

    @Issue('https://github.com/apache/grails-data-mapping/issues/1106')
    def "toMany validation cascades when isOwningSide is true if cascadeValidate option is set to owned or default"() {
        Author author = new Author(name: 'Author', books: [new Book()], defaultBooks: [new Book()], noneBooks: [new Book()])
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then: "validate is called for books"
        !errors.hasErrors()
        author.books.first()
        author.books.first().name == "name"

        and: "validate is called for defaultBooks"
        author.defaultBooks.first()
        author.defaultBooks.first().name == "name"

        and: "validate is not called for noneBooks"
        author.noneBooks.first()
        author.noneBooks.first().name == null
    }

    @Issue('https://github.com/apache/grails-data-mapping/issues/1106')
    def "toMany validation does not cascade when cascadeValidate option is set to none"() {
        Author author = new Author(name: 'Author', noneBooks: [new Book()])
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then: "validate is not called for noneBooks"
        !errors.hasErrors()
        author.noneBooks.first()
        author.noneBooks.first().name == null
    }

    @Issue('https://github.com/apache/grails-data-mapping/issues/1106')
    def "toOne validation does not cascade if cascadeValidate option is none"() {
        Author author = new Author(name: 'Author', nonePublisher: new Publisher())
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then: "validate is not called for publisher"
        !errors.hasErrors()
        !author.nonePublisher.validateCalled
    }

    def "toOne validation cascades if the associated object is dirty and cascadeValidate option set to dirty"() {
        Author author = new Author(name: 'Author', dirtyPublisher: new Publisher())
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then: "validate is called for publisher since it's dirty"
        !errors.hasErrors()
        author.dirtyPublisher.validateCalled
    }

    def "validation does not cascade if the associated object is not dirty and cascadeValidate option set to dirty"() {
        Author author = new Author(name: 'Author', dirtyPublisher: new Publisher())
        Errors errors = new ValidationErrors(author)
        author.dirtyPublisher.trackChanges()

        when:
        authorValidator.validate(author, errors)

        then: "validate is not called for publisher"
        !errors.hasErrors()
        !author.dirtyPublisher.validateCalled
    }

    def "the rules beyond the constraints are validated for the object and for each associated object cascaded to, under its path"() {
        given: "validators that record what they are asked to validate beyond the constraints"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity authorEntity = mappingContext.addPersistentEntity(Author)
        PersistentEntity bookEntity = mappingContext.addPersistentEntity(Book)
        mappingContext.addPersistentEntity(Publisher)
        DefaultValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        RecordingValidator authorValidator = new RecordingValidator(authorEntity, registry.messageSource, registry.constraintsEvaluator)
        RecordingValidator bookValidator = new RecordingValidator(bookEntity, registry.messageSource, registry.constraintsEvaluator)
        mappingContext.addEntityValidator(authorEntity, authorValidator)
        mappingContext.addEntityValidator(bookEntity, bookValidator)
        Book first = new Book()
        Book second = new Book()
        Author author = new Author(name: 'Author', books: [first, second] as List)
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then: "the author is validated after its constraints and associations"
        authorValidator.validated == [author]
        authorValidator.paths == ['']

        and: "each book by its own validator, with the nested path of the association set"
        bookValidator.validated == [first, second]
        bookValidator.paths == ['books[0].', 'books[1].']

        and: "what the validators report lands under that path"
        errors.errorCount == 3
        errors.getFieldError('name').code == 'beyond'
        errors.getFieldError('books[0].name').code == 'beyond'
        errors.getFieldError('books[1].name').code == 'beyond'

        when: "validation does not cascade"
        authorValidator.validated.clear()
        bookValidator.validated.clear()
        errors = new ValidationErrors(author)
        authorValidator.validate(author, errors, false)

        then: "only the author is validated"
        authorValidator.validated == [author]
        bookValidator.validated.empty
        errors.errorCount == 1
    }

    def "the constraints validated are the evaluated ones, whatever a subclass exposes"() {
        given: "a validator exposing a constraint it does not validate"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity authorEntity = mappingContext.addPersistentEntity(Author)
        PersistentEntity bookEntity = mappingContext.addPersistentEntity(Book)
        mappingContext.addPersistentEntity(Publisher)
        DefaultValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        RecordingValidator bookValidator = new RecordingValidator(bookEntity, registry.messageSource, registry.constraintsEvaluator)
        mappingContext.addEntityValidator(bookEntity, bookValidator)
        mappingContext.addEntityValidator(authorEntity, registry.getValidator(authorEntity))
        Author author = new Author(name: 'Author', books: [new Book()])
        Errors errors = new ValidationErrors(author)

        when:
        mappingContext.getEntityValidator(authorEntity).validate(author, errors)

        then: "the exposed constraints say the name of a book is required"
        !bookValidator.constrainedProperties.name.nullable
        bookValidator.constrainedProperties.name.maxSize == 3

        and: "the book is validated with its evaluated constraints, which allow the name beforeValidate sets"
        errors.errorCount == 1
        errors.getFieldError('books[0].name').code == 'beyond'
    }

    def "the validation hints of an associated object are the ones the validator of the owner gives for the association, all the way down"() {
        given: "validators giving the hints of the owner followed by the name of the association"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity libraryEntity = mappingContext.addPersistentEntity(HintedLibrary)
        PersistentEntity sectionEntity = mappingContext.addPersistentEntity(HintedSection)
        PersistentEntity shelfEntity = mappingContext.addPersistentEntity(HintedShelf)
        DefaultValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        HintingValidator libraryValidator = new HintingValidator(libraryEntity, registry.messageSource, registry.constraintsEvaluator)
        HintingValidator sectionValidator = new HintingValidator(sectionEntity, registry.messageSource, registry.constraintsEvaluator)
        HintingValidator shelfValidator = new HintingValidator(shelfEntity, registry.messageSource, registry.constraintsEvaluator)
        mappingContext.addEntityValidator(libraryEntity, libraryValidator)
        mappingContext.addEntityValidator(sectionEntity, sectionValidator)
        mappingContext.addEntityValidator(shelfEntity, shelfValidator)
        HintedSection section = new HintedSection(name: 'section', shelves: [new HintedShelf(name: 'first'), new HintedShelf(name: 'second')] as List)
        HintedLibrary library = new HintedLibrary(name: 'library', sections: [section] as List)

        when:
        libraryValidator.validate(library, new ValidationErrors(library))

        then: "the library is validated without hints"
        libraryValidator.constraintHints == [[]]
        libraryValidator.beyondHints == [[]]

        and: "the section with the hints the library gives, and the shelves with the hints the section gives with them"
        sectionValidator.constraintHints == [['sections']]
        sectionValidator.beyondHints == [['sections']]
        shelfValidator.constraintHints == [['sections', 'shelves'], ['sections', 'shelves']]
        shelfValidator.beyondHints == [['sections', 'shelves'], ['sections', 'shelves']]

        when: "a validation is started while another one cascades"
        [libraryValidator, sectionValidator, shelfValidator]*.clear()
        HintedSection other = new HintedSection(name: 'other', shelves: [new HintedShelf(name: 'third')] as List)
        shelfValidator.whileValidating = { sectionValidator.validate(other, new ValidationErrors(other)) }
        libraryValidator.validate(library, new ValidationErrors(library))

        then: "it starts without hints, and the cascade it interrupts goes on with its own"
        sectionValidator.beyondHints == [[], ['sections']]
        shelfValidator.beyondHints == [['shelves'], ['sections', 'shelves'], ['sections', 'shelves']]
    }
}

/**
 * Records the objects it is asked to validate beyond the constraints and rejects their name, and exposes a
 * constraint it does not validate.
 */
class RecordingValidator extends PersistentEntityValidator {

    final List<Object> validated = []
    final List<String> paths = []

    RecordingValidator(PersistentEntity entity, MessageSource messageSource, ConstraintsEvaluator constraintsEvaluator) {
        super(entity, messageSource, constraintsEvaluator)
    }

    @Override
    Map<String, ConstrainedProperty> getConstrainedProperties() {
        Map<String, ConstrainedProperty> exposed = new LinkedHashMap<>(super.constrainedProperties)
        DefaultConstrainedProperty name = new DefaultConstrainedProperty(targetClass, 'name', String,
                new DefaultConstraintRegistry(new StaticMessageSource()))
        name.applyConstraint(ConstrainedProperty.NULLABLE_CONSTRAINT, false)
        name.applyConstraint(ConstrainedProperty.MAX_SIZE_CONSTRAINT, 3)
        exposed.name = name
        exposed
    }

    @Override
    protected void validateBeyondConstraints(Object obj, Errors errors, Object[] hints) {
        validated.add(obj)
        paths.add(errors.nestedPath)
        errors.rejectValue('name', 'beyond')
    }
}

@Entity
class Author {
    String name

    Publisher publisher
    Publisher ownedPublisher
    Publisher defaultPublisher
    Publisher dirtyPublisher
    Publisher nonePublisher

    Set<Book> books
    Set<Book> defaultBooks
    Set<Book> noneBooks

    static hasMany = [
        books: Book, defaultBooks: Book, noneBooks: Book
    ]

    static constraints = {
        name(nullable: false)   // explicit now that properties are nullable by default — this spec
                                // uses the null-name error to detect that root beforeValidate didn't run
        publisher(nullable: true)
        ownedPublisher(nullable: true)
        defaultPublisher(nullable: true)
        dirtyPublisher(nullable: true)
        nonePublisher(nullable: true)
    }

    static mapping = {
        books(cascadeValidate: 'owned') // This should be validated since author isOwningSide of books
        noneBooks(cascadeValidate: 'none') // Don't cascade validation at all

        ownedPublisher(cascadeValidate: 'owned') // Only cascade validation when owned (should not be validated since author doesn't own publisher)
        defaultPublisher(cascadeValidate: 'default') // Explicitly use the default cascade logic
        dirtyPublisher(cascadeValidate: 'dirty') // Only cascade validation if the object is dirty
        nonePublisher(cascadeValidate: 'none') // Don't cascade validation at all
    }
}

/**
 * Records the validation hints it is asked to validate with, and gives the hints of the owner followed by the name of
 * the association as the hints of an associated object.
 */
class HintingValidator extends PersistentEntityValidator {

    final List<List<Object>> constraintHints = []
    final List<List<Object>> beyondHints = []
    Closure whileValidating

    HintingValidator(PersistentEntity entity, MessageSource messageSource, ConstraintsEvaluator constraintsEvaluator) {
        super(entity, messageSource, constraintsEvaluator)
    }

    void clear() {
        constraintHints.clear()
        beyondHints.clear()
    }

    @Override
    protected Map<String, ConstrainedProperty> getValidatedConstrainedProperties(Object[] hints) {
        constraintHints.add(hints as List)
        super.getValidatedConstrainedProperties(hints)
    }

    @Override
    protected Object[] getAssociationValidationHints(Association association, Object[] hints) {
        ((hints as List) + association.name).toArray()
    }

    @Override
    protected void validateBeyondConstraints(Object obj, Errors errors, Object[] hints) {
        Closure action = whileValidating
        whileValidating = null
        action?.call()
        beyondHints.add(hints as List)
    }
}

@Entity
class HintedLibrary {
    String name
    List<HintedSection> sections
    static hasMany = [sections: HintedSection]
}

@Entity
class HintedSection {
    String name
    List<HintedShelf> shelves
    static belongsTo = [library: HintedLibrary]
    static hasMany = [shelves: HintedShelf]
}

@Entity
class HintedShelf {
    String name
    static belongsTo = [section: HintedSection]
}

@Entity
class Book {
    String name
    static belongsTo = [author: Author]

    def beforeValidate() {
        name = "name"
    }
}

@Entity
class Publisher implements DirtyCheckable {
    String name

    @Transient
    boolean validateCalled = false

    def beforeValidate() {
        name = "name"
        validateCalled = true
    }

    // some of the persistent entity validator logic relies on errors being present
    @Transient
    Errors errors
    Errors getErrors() {
        if(errors == null) {
            errors = new ValidationErrors(this)
        }
        errors
    }
}
