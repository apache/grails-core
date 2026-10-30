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
import org.grails.datastore.mapping.core.Session
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
import org.springframework.validation.ValidationUtils
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

    def "an object is validated with the validation hints it is given, and an associated object once with each hints it is reached with"() {
        given: "validators giving the hints of the owner followed by the name of the association, and a lamp a desk reaches three ways"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity deskEntity = mappingContext.addPersistentEntity(HintedDesk)
        PersistentEntity lampEntity = mappingContext.addPersistentEntity(HintedLamp)
        DefaultValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        HintingValidator deskValidator = new HintingValidator(deskEntity, registry.messageSource, registry.constraintsEvaluator)
        HintingValidator lampValidator = new HintingValidator(lampEntity, registry.messageSource, registry.constraintsEvaluator)
        mappingContext.addEntityValidator(deskEntity, deskValidator)
        mappingContext.addEntityValidator(lampEntity, lampValidator)
        HintedLamp lamp = new HintedLamp(name: 'lamp')
        HintedDesk desk = new HintedDesk(name: 'desk', left: lamp, right: lamp, lamps: [lamp, lamp] as List)

        when: "the desk is validated with hints, as a SmartValidator"
        deskValidator.validate(desk, new ValidationErrors(desk), 'draft')

        then: "the desk is validated with them"
        deskValidator.constraintHints == [['draft']]
        deskValidator.beyondHints == [['draft']]

        and: "the lamp once with the hints of each association, and once for the two entries of the list with the same hints"
        lampValidator.constraintHints.size() == 3
        lampValidator.constraintHints as Set == [['draft', 'left'], ['draft', 'right'], ['draft', 'lamps']] as Set
        lampValidator.beyondHints == lampValidator.constraintHints

        when: "the desk is validated without hints"
        [deskValidator, lampValidator]*.clear()
        deskValidator.validate(desk, new ValidationErrors(desk))

        then:
        deskValidator.beyondHints == [[]]
        lampValidator.beyondHints as Set == [['left'], ['right'], ['lamps']] as Set
    }

    def "two associated instances that compare equal are both validated"() {
        given: "a basket with two lines of the same name, which compare equal, as a Comparable with a hash code of a business key does"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity basketEntity = mappingContext.addPersistentEntity(ComparedBasket)
        mappingContext.addPersistentEntity(ComparedBasketLine)
        ValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        mappingContext.validatorRegistry = registry
        ComparedBasket basket = new ComparedBasket(name: 'basket')
        basket.lines = [new ComparedBasketLine(name: 'same', code: 'A1'), new ComparedBasketLine(name: 'same', code: null)]
        Errors errors = new ValidationErrors(basket)

        expect:
        basket.lines[0] == basket.lines[1]
        basket.lines[0].equals(basket.lines[1])
        basket.lines[0].hashCode() == basket.lines[1].hashCode()
        !basket.lines[0].is(basket.lines[1])

        when:
        registry.getValidator(basketEntity).validate(basket, errors)

        then: "the second line is validated as well as the first"
        errors.errorCount == 1
        errors.getFieldError('lines[1].code').code == 'nullable'
    }

    def "a proxy of the entity is supported and validated as the instance it stands for"() {
        given:
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity authorEntity = mappingContext.addPersistentEntity(ProxiedAuthor)
        Validator validator = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings()).getValidator(authorEntity)
        ProxiedAuthor author = new ProxiedAuthor(id: 1L)
        Session session = Mock(Session) {
            getMappingContext() >> mappingContext
            retrieve(_, _) >> author
        }
        ProxiedAuthor proxy = mappingContext.proxyFactory.createProxy(session, ProxiedAuthor, 1L)
        Errors errors = new ValidationErrors(proxy)

        expect: "the class of the proxy, a subclass of the entity, is supported, as a DataBinder and ValidationUtils require"
        proxy.getClass() != ProxiedAuthor
        validator.supports(proxy.getClass())
        validator.supports(ProxiedAuthor)
        !validator.supports(Author)

        when:
        ValidationUtils.invokeValidator(validator, proxy, errors)

        then: "the instance the proxy stands for is validated"
        errors.errorCount == 1
        errors.getFieldError('name').code == 'nullable'
    }

    def "an initialized proxy of an associated instance is validated as the instance it stands for"() {
        given: "a desk whose lamp is an initialized proxy, and a validator recording the lamps it validates"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity deskEntity = mappingContext.addPersistentEntity(HintedDesk)
        PersistentEntity lampEntity = mappingContext.addPersistentEntity(HintedLamp)
        DefaultValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        RecordingValidator lampValidator = new RecordingValidator(lampEntity, registry.messageSource, registry.constraintsEvaluator)
        mappingContext.addEntityValidator(deskEntity, registry.getValidator(deskEntity))
        mappingContext.addEntityValidator(lampEntity, lampValidator)
        HintedLamp lamp = new HintedLamp(name: 'lamp')
        Session session = Mock(Session) {
            getMappingContext() >> mappingContext
            retrieve(_, _) >> lamp
        }
        HintedLamp proxy = mappingContext.proxyFactory.createProxy(session, HintedLamp, 1L)
        proxy.name
        HintedDesk desk = new HintedDesk(name: 'desk', left: proxy)
        Errors errors = new ValidationErrors(desk)

        expect:
        mappingContext.proxyHandler.isProxy(proxy)
        mappingContext.proxyHandler.isInitialized(proxy)

        when: "the proxy is reached through a to-one association"
        mappingContext.getEntityValidator(deskEntity).validate(desk, errors)

        then: "the lamp behind it is validated, under the path of the association"
        lampValidator.validated.size() == 1
        lampValidator.validated[0].is(lamp)
        lampValidator.paths == ['left.']
        errors.getFieldError('left.name').code == 'beyond'
        errors.errorCount == 1

        when: "the proxy is reached through a collection, next to the lamp itself"
        lampValidator.validated.clear()
        lampValidator.paths.clear()
        desk = new HintedDesk(name: 'desk', lamps: [proxy, lamp] as List)
        errors = new ValidationErrors(desk)
        mappingContext.getEntityValidator(deskEntity).validate(desk, errors)

        then: "the lamp is validated once, the proxy and the lamp being the same instance"
        lampValidator.validated.size() == 1
        lampValidator.validated[0].is(lamp)
        lampValidator.paths == ['lamps[0].']
        errors.errorCount == 1
    }

    def "a cycle of associations ends at the object validation started with, whatever hints it is reached with"() {
        given: "an account whose region's office leads back to it, and validators giving no hints to the associated objects"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity accountEntity = mappingContext.addPersistentEntity(CycledAccount)
        mappingContext.addPersistentEntity(CycledRegion)
        mappingContext.addPersistentEntity(CycledOffice)
        ValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        mappingContext.validatorRegistry = registry
        CycledAccount account = new CycledAccount(owner: null)
        account.region = new CycledRegion(name: 'north', office: new CycledOffice(city: 'Oslo', account: account))
        Errors errors = new ValidationErrors(account)

        when: "the account is validated with a hint, as ValidationUtils and a DataBinder validate"
        ValidationUtils.invokeValidator(registry.getValidator(accountEntity), account, errors, 'draft')

        then: "its owner is reported once, not again under the path leading back to it"
        errors.errorCount == 1
        errors.getFieldError('owner').code == 'nullable'
        !errors.hasFieldErrors('region.office.account.owner')

        when: "it is validated without hints"
        errors = new ValidationErrors(account)
        registry.getValidator(accountEntity).validate(account, errors)

        then:
        errors.errorCount == 1
        errors.getFieldError('owner').code == 'nullable'
    }

    def "a cycle of associations ends at an object on the path to it, whatever hints it is reached with"() {
        given: "an office leading back to the account and to the region it is reached through, and validators giving the hints of the owner followed by the name of the association"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity accountEntity = mappingContext.addPersistentEntity(CycledAccount)
        PersistentEntity regionEntity = mappingContext.addPersistentEntity(CycledRegion)
        PersistentEntity officeEntity = mappingContext.addPersistentEntity(CycledOffice)
        DefaultValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        HintingValidator accountValidator = new HintingValidator(accountEntity, registry.messageSource, registry.constraintsEvaluator)
        HintingValidator regionValidator = new HintingValidator(regionEntity, registry.messageSource, registry.constraintsEvaluator)
        HintingValidator officeValidator = new HintingValidator(officeEntity, registry.messageSource, registry.constraintsEvaluator)
        mappingContext.addEntityValidator(accountEntity, accountValidator)
        mappingContext.addEntityValidator(regionEntity, regionValidator)
        mappingContext.addEntityValidator(officeEntity, officeValidator)
        CycledAccount account = new CycledAccount(owner: 'owner')
        CycledRegion region = new CycledRegion(name: 'north')
        region.office = new CycledOffice(city: 'Oslo', account: account, region: region)
        account.region = region

        when:
        accountValidator.validate(account, new ValidationErrors(account), 'draft')

        then: "the account and the region are validated once each, with the hints they are first reached with"
        accountValidator.constraintHints == [['draft']]
        accountValidator.beyondHints == [['draft']]
        regionValidator.constraintHints == [['draft', 'region']]
        regionValidator.beyondHints == [['draft', 'region']]
        officeValidator.constraintHints == [['draft', 'region', 'office']]
        officeValidator.beyondHints == [['draft', 'region', 'office']]
    }

    def "an instance of a subclass that is an entity of its own is validated by the validator of that entity"() {
        given: "documents and reports, which extend them with a rule and an association of their own, and a validator of reports recording its hints"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        PersistentEntity documentEntity = mappingContext.addPersistentEntity(SubclassedDocument)
        PersistentEntity reportEntity = mappingContext.addPersistentEntity(SubclassedReport)
        mappingContext.addPersistentEntity(SubclassedAppendix)
        DefaultValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        mappingContext.validatorRegistry = registry
        HintingValidator reportValidator = new HintingValidator(reportEntity, registry.messageSource, registry.constraintsEvaluator)
        mappingContext.addEntityValidator(reportEntity, reportValidator)
        Validator documentValidator = registry.getValidator(documentEntity)
        SubclassedReport report = new SubclassedReport(name: 'report', appendix: new SubclassedAppendix(label: null))
        Errors errors = new ValidationErrors(report)

        expect: "the validator of documents supports reports, as it supports the class of a proxy"
        documentValidator.supports(SubclassedReport)

        when: "a report is handed to it with hints, as ValidationUtils does with a validator supporting its class"
        ValidationUtils.invokeValidator(documentValidator, report, errors, 'draft')

        then: "the validator of reports validates it with them, its rules and its associations"
        reportValidator.constraintHints == [['draft']]
        reportValidator.beyondHints == [['draft']]
        errors.errorCount == 2
        errors.getFieldError('pages').code == 'nullable'
        errors.getFieldError('appendix.label').code == 'nullable'

        when: "without cascading"
        reportValidator.clear()
        errors = new ValidationErrors(report)
        ((CascadingValidator) documentValidator).validate(report, errors, false)

        then:
        reportValidator.constraintHints == [[]]
        errors.errorCount == 1
        errors.getFieldError('pages').code == 'nullable'

        when: "a proxy of a report is handed to it"
        reportValidator.clear()
        Session session = Mock(Session) {
            getMappingContext() >> mappingContext
            retrieve(_, _) >> report
        }
        SubclassedReport proxy = mappingContext.proxyFactory.createProxy(session, SubclassedReport, 1L)
        errors = new ValidationErrors(proxy)
        documentValidator.validate(proxy, errors)

        then: "the report it stands for is validated by the validator of reports"
        reportValidator.constraintHints == [[]]
        errors.getFieldError('pages').code == 'nullable'

        when: "a document is handed to it"
        reportValidator.clear()
        SubclassedDocument document = new SubclassedDocument()
        errors = new ValidationErrors(document)
        documentValidator.validate(document, errors)

        then: "it validates the document itself"
        reportValidator.constraintHints.empty
        errors.errorCount == 1
        errors.getFieldError('name').code == 'nullable'
    }

    def "an associated instance of a subclass that is an entity of its own is validated with the rules of that entity"() {
        given: "folders holding a document, and reports, which extend documents with a rule and an association of their own, with a validator of reports recording its hints"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.mappingFactory = new GormKeyValueMappingFactory("test")
        mappingContext.syntaxStrategy = new GormMappingConfigurationStrategy(mappingContext.mappingFactory)
        mappingContext.addPersistentEntity(SubclassedDocument)
        PersistentEntity reportEntity = mappingContext.addPersistentEntity(SubclassedReport)
        mappingContext.addPersistentEntity(SubclassedAppendix)
        PersistentEntity folderEntity = mappingContext.addPersistentEntity(SubclassedFolder)
        DefaultValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())
        mappingContext.validatorRegistry = registry
        HintingValidator reportValidator = new HintingValidator(reportEntity, registry.messageSource, registry.constraintsEvaluator)
        mappingContext.addEntityValidator(reportEntity, reportValidator)
        Validator folderValidator = registry.getValidator(folderEntity)
        SubclassedFolder folder = new SubclassedFolder(name: 'folder',
                document: new SubclassedReport(name: 'report', pages: null, appendix: new SubclassedAppendix(label: null)))
        Errors errors = new ValidationErrors(folder)

        when: "a folder whose document is a report is validated"
        folderValidator.validate(folder, errors)

        then: "the validator of reports validates the report, with the rules and the associations of reports, under the path of the association"
        reportValidator.constraintHints == [[]]
        reportValidator.beyondHints == [[]]
        errors.errorCount == 2
        errors.getFieldError('document.pages').code == 'nullable'
        errors.getFieldError('document.appendix.label').code == 'nullable'

        when: "the document of the folder is a document"
        reportValidator.clear()
        folder.document = new SubclassedDocument(name: null)
        errors = new ValidationErrors(folder)
        folderValidator.validate(folder, errors)

        then: "it is validated with the rules of documents"
        reportValidator.constraintHints.empty
        errors.errorCount == 1
        errors.getFieldError('document.name').code == 'nullable'
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
    protected Map<String, ConstrainedProperty> getValidatedConstrainedProperties(Object obj, Object[] hints) {
        constraintHints.add(hints as List)
        super.getValidatedConstrainedProperties(obj, hints)
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
class HintedDesk {
    String name
    HintedLamp left
    HintedLamp right
    List<HintedLamp> lamps
    static hasMany = [lamps: HintedLamp]
}

@Entity
class HintedLamp {
    Long id
    String name
    static belongsTo = [HintedDesk]
}

@Entity
class ProxiedAuthor {
    Long id
    String name

    static constraints = {
        name(nullable: false)
    }
}

@Entity
class ComparedBasket {
    String name
    List<ComparedBasketLine> lines
    static hasMany = [lines: ComparedBasketLine]
}

/**
 * A line equal to any other line of the same name, as an entity with a business key is, and sorted by it
 */
@Entity
class ComparedBasketLine implements Comparable<ComparedBasketLine> {
    String name
    String code
    static belongsTo = [basket: ComparedBasket]
    static constraints = {
        code nullable: false
    }

    @Override
    int compareTo(ComparedBasketLine other) {
        name <=> other.name
    }

    @Override
    boolean equals(Object other) {
        other instanceof ComparedBasketLine && name == ((ComparedBasketLine) other).name
    }

    @Override
    int hashCode() {
        Objects.hashCode(name)
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
class CycledAccount {
    String owner
    CycledRegion region

    static constraints = {
        owner(nullable: false)
    }
}

@Entity
class CycledRegion {
    String name
    CycledOffice office
}

/**
 * An office leading back to the account and to the region it is reached through
 */
@Entity
class CycledOffice {
    String city
    CycledAccount account
    CycledRegion region
}

@Entity
class SubclassedDocument {
    Long id
    String name

    static constraints = {
        name(nullable: false)
    }
}

/**
 * A report, an entity of its own extending documents with a rule and an association of its own
 */
@Entity
class SubclassedReport extends SubclassedDocument {
    Integer pages
    SubclassedAppendix appendix

    static constraints = {
        pages(nullable: false)
    }
}

@Entity
class SubclassedAppendix {
    String label
    static belongsTo = [report: SubclassedReport]

    static constraints = {
        label(nullable: false)
    }
}

/**
 * A folder holding a document, which may be a report
 */
@Entity
class SubclassedFolder {
    String name
    SubclassedDocument document
}
