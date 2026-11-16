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

import groovy.transform.EqualsAndHashCode

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
import org.springframework.validation.BeanPropertyBindingResult
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

    def "a cascade listener is called with each associated object after its beforeValidate callback and before its constraints are checked"() {
        given:
        Validator shelfValidator = shelfValidator()
        Shelf shelf = new Shelf(name: 'Top', volume: new Volume())
        Errors errors = new ValidationErrors(shelf)
        List<String> titles = []
        List<String> associations = []

        when: 'the listener unsets the title that beforeValidate has given the associated object'
        shelfValidator.validate(shelf, errors, true) { Object associatedObject, Association association ->
            titles << associatedObject.title
            associations << association.name
            associatedObject.title = null
            true
        }

        then: 'it is called once, after the beforeValidate callback of that object, with the association that reaches it'
        titles == ['untitled']
        associations == ['volume']

        and: 'before the constraints of that object are checked'
        errors.getFieldError('volume.title').code == 'nullable'
    }

    def "an associated object that a cascade listener has left alone is passed to it again once all of them are validated"() {
        given:
        Validator shelfValidator = shelfValidator()
        Shelf shelf = new Shelf(name: 'Top', volume: new Volume())
        Errors errors = new ValidationErrors(shelf)
        int calls = 0

        when: 'the listener leaves the object alone each time'
        shelfValidator.validate(shelf, errors, true) { Object associatedObject, Association association ->
            calls++
            false
        }

        then: 'it is called a second time, and then no more'
        calls == 2
        !errors.hasErrors()

        when: 'the listener changes the object the second time'
        calls = 0
        shelfValidator.validate(shelf, errors, true) { Object associatedObject, Association association ->
            if (++calls == 1) {
                return false
            }
            associatedObject.title = null
            true
        }

        then: 'the constraints of that object are checked again'
        calls == 2
        errors.getFieldError('volume.title').code == 'nullable'
    }

    def "a cascade listener is asked about the objects it has left alone until it leaves them all alone"() {
        given:
        Validator shelfValidator = shelfValidator()
        Shelf shelf = new Shelf(name: 'Top', volume: new Volume(), spare: new Volume())
        Errors errors = new ValidationErrors(shelf)
        List<Object> passed = []
        List<Object> changed = []

        when: 'the listener changes the object it was passed first, and the other one only once the first is changed'
        shelfValidator.validate(shelf, errors, true) { Object associatedObject, Association association ->
            if (!passed.any { it.is(associatedObject) }) {
                passed << associatedObject
                return false
            }
            boolean change = associatedObject.is(passed[0]) || changed.any { it.is(passed[0]) }
            if (change) {
                changed << associatedObject
            }
            change
        }

        then: 'both are changed, whichever of them the listener is asked about first'
        passed.size() == 2
        changed.size() == 2
    }

    def "the errors of an associated object are replaced when a cascade listener changes it as it is passed again"() {
        given: 'a volume whose code is too long, and a spare one whose code stays too long'
        Validator shelfValidator = shelfValidator()
        Shelf shelf = new Shelf(volume: new Volume(code: 'too-long'), spare: new Volume(code: 'way-too-long'))
        Errors errors = new ValidationErrors(shelf)
        int calls = 0

        when: 'the listener leaves both alone as they are validated, and gives the volume another code as it is passed again'
        Errors result = shelfValidator.validate(shelf, errors, true) { Object associatedObject, Association association ->
            if (++calls > 2 && associatedObject.is(shelf.volume)) {
                associatedObject.code = newCode
                return true
            }
            false
        }

        then: 'the errors returned are a copy of the given errors, which still hold the error of the code it had'
        !result.is(errors)
        errors.getFieldError('volume.code').rejectedValue == 'too-long'

        and: 'in the copy that error is gone, its constraints are checked with the new code, and the other errors are kept'
        result.fieldErrors.collect { [it.field, it.rejectedValue] } == expected

        where:
        newCode         || expected
        'short'         || [['name', null], ['spare.code', 'way-too-long']]
        'also-too-long' || [['name', null], ['spare.code', 'way-too-long'], ['volume.code', 'also-too-long']]
    }

    def "the errors that were recorded before a validation are kept when an associated object is checked again"() {
        given: 'errors that hold an error of the shelf and one of the code of its volume'
        Validator shelfValidator = shelfValidator()
        Shelf shelf = new Shelf(name: 'Top', volume: new Volume(code: 'too-long'))
        Errors errors = new ValidationErrors(shelf)
        errors.rejectValue('name', 'bound')
        errors.rejectValue('volume.code', 'bound')
        int calls = 0

        when: 'the listener leaves the volume alone as it is validated, and changes it as it is passed again'
        Errors result = shelfValidator.validate(shelf, errors, true) { Object associatedObject, Association association ->
            if (++calls == 1) {
                return false
            }
            associatedObject.code = 'short'
            true
        }

        then: 'both are kept, and no error is added to them'
        calls == 2
        result.is(errors)
        errors.fieldErrors.collect { [it.field, it.code] } == [['name', 'bound'], ['volume.code', 'bound']]
    }

    def "a validation with a cascade listener records its errors in the given errors, and returns them"() {
        given:
        Validator shelfValidator = shelfValidator()
        Shelf shelf = new Shelf(volume: new Volume(code: 'too-long'))
        Errors errors = new ValidationErrors(shelf)

        when: 'the listener leaves the volume alone each time'
        Errors result = shelfValidator.validate(shelf, errors, true) { Object associatedObject, Association association -> false }

        then: 'every error is in the given errors, which are returned'
        result.is(errors)
        errors.fieldErrors*.field == ['name', 'volume.code']
    }

    def "the errors that a cascade listener makes a validation return keep the object name and the nested path of the given errors"() {
        given: 'the errors of an object that holds the shelf'
        Validator shelfValidator = shelfValidator()
        Shelf shelf = new Shelf(volume: new Volume(code: 'too-long'), spare: new Volume(code: 'way-too-long'))
        Errors errors = new BeanPropertyBindingResult(new Rack(shelf: shelf), 'holder')
        errors.setNestedPath('shelf')
        int calls = 0

        when: 'the listener leaves both volumes alone as they are validated, and gives the first one another code as it is passed again'
        Errors result = shelfValidator.validate(shelf, errors, true) { Object associatedObject, Association association ->
            if (++calls > 2 && associatedObject.is(shelf.volume)) {
                associatedObject.code = 'also-too-long'
                return true
            }
            false
        }

        then:
        !result.is(errors)
        errors.nestedPath == 'shelf.'
        result.nestedPath == 'shelf.'
        result.fieldErrors.collect { [it.objectName, it.field, it.rejectedValue] } == [
                ['holder', 'shelf.name', null],
                ['holder', 'shelf.spare.code', 'way-too-long'],
                ['holder', 'shelf.volume.code', 'also-too-long']
        ]
    }

    def "an associated object that two associations refer to is validated once, also when the listener changes its hashCode"() {
        given: 'a tome whose hashCode depends on its code, on both ends of a bookend'
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity bookendEntity = mappingContext.addPersistentEntity(Bookend)
        mappingContext.addPersistentEntity(Tome)
        mappingContext.validatorRegistry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        Validator bookendValidator = mappingContext.getEntityValidator(bookendEntity)
        Tome tome = new Tome()
        Bookend bookend = new Bookend(name: 'Oak', left: tome, right: tome)
        int calls = 0

        when: 'the listener gives the tome a code each time it is called'
        Errors result = bookendValidator.validate(bookend, new ValidationErrors(bookend), true) { Object associatedObject, Association association ->
            calls++
            associatedObject.code = "code-${calls}"
            true
        }

        then: 'the beforeValidate callback of the tome runs once, the listener is called once, and its title is reported once'
        tome.validations == 1
        calls == 1
        result.fieldErrors*.field.findAll { it.endsWith('title') }.size() == 1
    }

    def "a cascade listener is not called when validation does not cascade, and validation runs without one"() {
        given:
        Author author = new Author(name: 'Author', books: [new Book()], publisher: new Publisher())
        Errors errors = new ValidationErrors(author)
        List<Object> seen = []

        when:
        authorValidator.validate(author, errors, false) { Object associatedObject, Association association ->
            seen << associatedObject
            true
        }

        then:
        seen.empty
        !errors.hasErrors()

        when:
        authorValidator.validate(author, errors, true, null)

        then:
        !errors.hasErrors()
        author.publisher.validateCalled
    }

    def "a cascade listener is called for its own validation only"() {
        given:
        Book book = new Book()
        Author author = new Author(name: 'Author', publisher: new Publisher(), books: [book])
        Author other = new Author(name: 'Other', publisher: new Publisher())
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>())

        when: 'the listener of one validation runs another validation, which has no listener, each time it is called'
        authorValidator.validate(author, new ValidationErrors(author), true) { Object associatedObject, Association association ->
            seen << associatedObject
            authorValidator.validate(other, new ValidationErrors(other))
            true
        }
        authorValidator.validate(other, new ValidationErrors(other))

        then: 'it is called with the associated objects of its own validation, before and after the other one'
        seen.size() == 2
        seen.contains(book)
        seen.contains(author.publisher)
    }

    private static Validator shelfValidator() {
        // A mapping context whose validators check the constraints of an associated object
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity shelfEntity = mappingContext.addPersistentEntity(Shelf)
        mappingContext.addPersistentEntity(Volume)
        mappingContext.validatorRegistry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        mappingContext.getEntityValidator(shelfEntity)
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

@Entity
class Shelf {
    String name
    Volume volume
    Volume spare

    static constraints = {
        name nullable: false
        spare nullable: true
    }
}

class Rack {
    Shelf shelf
}

@Entity
class Volume {
    String title
    String code

    static belongsTo = Shelf

    static constraints = {
        title nullable: false
        code nullable: true, maxSize: 5
    }

    def beforeValidate() {
        title = 'untitled'
    }
}

@Entity
class Bookend {
    String name
    Tome left
    Tome right
}

@Entity
@EqualsAndHashCode(includes = ['code'])
class Tome {
    String code
    String title

    @Transient
    int validations

    static belongsTo = Bookend

    static constraints = {
        title nullable: false
    }

    def beforeValidate() {
        validations++
    }
}
