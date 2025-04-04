package grails.gorm.validation.cascade

import jakarta.persistence.Entity
import jakarta.persistence.Transient

import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.GormKeyValueMappingFactory
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.config.GormMappingConfigurationStrategy
import org.grails.datastore.mapping.validation.ValidationErrors
import org.grails.datastore.mapping.validation.ValidatorRegistry
import org.springframework.validation.Errors
import org.springframework.validation.Validator

import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class NoDuplicateCascadeSpec extends Specification {
    @Shared Validator validator

    void setupSpec() {
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)

        def authorEntity = mappingContext.addPersistentEntity(Author)
        def bookEntity = mappingContext.addPersistentEntity(Book)
        def chapterEntity = mappingContext.addPersistentEntity(Chapter)

        ValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        validator = registry.getValidator(chapterEntity)
    }

    @Issue("https://github.com/grails/grails-data-mapping/issues/1064")
    def "cascading validation should not validate objects more than once"() {

        Author a1 = new Author()
        Book b = new Book(author: a1)
        Chapter c = new Chapter(book: b, author: a1)
        Errors errors = new ValidationErrors(b)
        a1.validatedCounter = 0;

        when:
        validator.validate(c, errors)

        then:
        a1.validatedCounter <= 1
    }
}

trait CountValidations {
    @Transient
    int validatedCounter = 0;
    def beforeValidate() {
        validatedCounter = validatedCounter + 1
    }
}

@Entity
class Author implements CountValidations {
    static hasMany = [books: Book, chapters: Chapter]
}

@Entity
class Book {
    static belongsTo = [author: Author];
    static hasMany = [chapters: Chapter]
    Author author;
}

@Entity
class Chapter {
    static belongsTo = [book: Book, author: Author];
    Author author;
    Book book;
}

