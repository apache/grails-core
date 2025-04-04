package grails.gorm.validation

import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.keyvalue.mapping.config.GormKeyValueMappingFactory
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormMappingConfigurationStrategy
import org.grails.datastore.mapping.validation.ValidationErrors
import org.grails.datastore.mapping.validation.ValidatorRegistry
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
    @Issue('https://github.com/grails/grails-data-mapping/issues/1102')
    def "validation of root object does NOT trigger beforeValidate here"() {
        Author author = new Author()
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then:
        errors.hasErrors()
        errors.getFieldErrors('name')
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/1102')
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

    @Issue('https://github.com/grails/grails-data-mapping/issues/1102')
    def "cascading validation triggers beforeValidate callback on to-one association"() {
        Author author = new Author(name: 'Author', publisher: new Publisher())
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then:
        !errors.hasErrors()
        author.publisher.name == "name"
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/1106')
    def "validation cascades by default if association is owned or has cascade options of PERSIST or MERGE"() {
        Author author = new Author(name: 'Author', publisher: new Publisher())
        Errors errors = new ValidationErrors(author)

        when:
        authorValidator.validate(author, errors)

        then: "validate was called for this by default"
        author.publisher.validateCalled
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/1106')
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

    @Issue('https://github.com/grails/grails-data-mapping/issues/1106')
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

    @Issue('https://github.com/grails/grails-data-mapping/issues/1106')
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
