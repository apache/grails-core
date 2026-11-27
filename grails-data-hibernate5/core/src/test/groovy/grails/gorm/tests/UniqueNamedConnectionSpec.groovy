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
package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A save that names a connection, such as {@code book.second.save()}, checks the {@code unique} constraint on that
 * connection and not on the default one.
 */
@Issue('https://github.com/apache/grails-core/issues/16934')
class UniqueNamedConnectionSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
            'dataSource.url'      : 'jdbc:h2:mem:uncDefault;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate' : 'create-drop',
            'dataSource.dialect'  : H2Dialect.name,
            'dataSources.second'  : [url: 'jdbc:h2:mem:uncSecond;LOCK_TIMEOUT=10000'],
            'dataSources.third'   : [url: 'jdbc:h2:mem:uncThird;LOCK_TIMEOUT=10000']
    ]), UncBook, UncAudit, UncPlain)

    void setup() {
        UncBook.withNewTransaction { new UncBook(name: 'onDefault').save(validate: false, flush: true) }
        UncBook.second.withNewTransaction { new UncBook(name: 'onSecond').second.save(validate: false, flush: true) }
    }

    void cleanup() {
        UncBook.withNewTransaction { UncBook.executeUpdate('delete from UncBook') }
        UncBook.second.withNewTransaction { UncBook.second.executeUpdate('delete from UncBook') }
        UncBook.third.withNewTransaction { UncBook.third.executeUpdate('delete from UncBook') }
        UncAudit.withNewTransaction { UncAudit.executeUpdate('delete from UncAudit') }
        UncAudit.third.withNewTransaction { UncAudit.third.executeUpdate('delete from UncAudit') }
        UncBook.innerSaved = null
        UncBook.cascadeErrors = null
        UncBook.innerPlainErrors = null
    }

    private <T> T withSessions(Closure<T> work) {
        UncBook.withNewSession {
            UncBook.second.withNewSession {
                UncBook.third.withNewSession(work)
            }
        }
    }

    void 'a save that names the second connection checks the second connection'() {
        when:
        UncBook onSecond = new UncBook(name: 'onSecond')
        UncBook onDefault = new UncBook(name: 'onDefault')
        List saved = withSessions { [onSecond.second.save(), onDefault.second.save()] }

        then: 'the value of the second connection is refused and the one of the default connection is saved'
        saved[0] == null
        onSecond.hasErrors()
        saved[1] != null
        !onDefault.hasErrors()
    }

    void 'the API of the second connection checks the second connection for save and insert'() {
        when:
        UncBook savedOnSecond = new UncBook(name: 'onSecond')
        UncBook insertedOnSecond = new UncBook(name: 'onSecond')
        UncBook savedOnDefault = new UncBook(name: 'onDefault')
        List results = withSessions {
            [UncBook.second.save(savedOnSecond), UncBook.second.insert(insertedOnSecond), UncBook.second.save(savedOnDefault)]
        }

        then:
        results[0] == null
        results[1] == null
        results[2] != null
    }

    void 'a save that names the third connection inside a block of the second checks the third'() {
        when:
        UncBook book = new UncBook(name: 'onSecond')
        UncBook saved = UncBook.withNewSession {
            UncBook.second.withNewTransaction {
                UncBook.third.withNewTransaction { book.third.save() }
            }
        }

        then: 'the third has no such value'
        book.errors.allErrors.toString() == '[]'
        saved != null
    }

    void 'a save through the default connection made while a save on the third connection runs checks the default connection'() {
        given: 'the value of the inner save is taken on the third connection and free on the default one'
        UncAudit.third.withNewTransaction { new UncAudit(code: 'free').third.save(validate: false, flush: true) }

        when: 'the validation of a book saved on the third connection saves an audit through the default connection'
        UncBook book = new UncBook(name: 'newBook', innerSave: true)
        UncBook saved = withSessions { book.third.save() }

        then: 'the inner save is checked on the default connection, so it is not refused'
        saved != null
        UncBook.innerSaved != null
        !UncBook.innerSaved.hasErrors()
    }

    void 'the connection of the outer save is checked again after an inner save through the default connection'() {
        given:
        UncBook.third.withNewTransaction { new UncBook(name: 'onThird').third.save(validate: false, flush: true) }

        when: 'a book that is taken on the third connection is saved there while its validation saves an audit by default'
        UncBook book = new UncBook(name: 'onThird', innerSave: true)
        UncBook saved = withSessions { book.third.save() }

        then: 'the outer save is still checked on the third connection and refused'
        saved == null
        book.hasErrors()
    }

    void 'a validator that the validation of a book on the third connection calls for an entity that is not mapped to it checks its own connection'() {
        given: 'an entity of the default connection only, as the child of a cascaded validation would be'
        UncBook book = new UncBook(name: 'newBook', cascadeCheck: true)

        when:
        UncBook saved = withSessions { book.third.save() }

        then: 'the check of the other entity does not ask for the third connection, which it is not mapped to, and the save goes on'
        saved != null
        UncBook.cascadeErrors != null
        !UncBook.cascadeErrors.hasErrors()
    }

    @Unroll
    void 'a listener of the validation event that checks the target during #entry sees the connection that is named'() {
        given: 'a listener that runs the validator of the entity on the target of each event, as a listener that checks it would'
        UncEventChecker checker = new UncEventChecker()
        ((ConfigurableApplicationEventPublisher) datastore.applicationEventPublisher).addApplicationListener(checker)

        when: 'a value that exists only on the default connection is saved or validated on the second one'
        UncBook book = new UncBook(name: 'onDefault')
        def result = withSessions { call.call(book) }

        then: 'the check of the listener is made on the second connection, which has no such value'
        result
        checker.errors != null
        !checker.errors.hasErrors()

        cleanup:
        checker.active = false

        where:
        entry                    | call
        'a save of the instance' | { UncBook b -> b.second.save() }
        'a save of the API'      | { UncBook b -> UncBook.second.save(b) }
    }

    @Unroll
    void 'a listener that checks an audit #entry through the default connection while a save on the third connection runs sees the default connection'() {
        given: 'the audit code is taken on the third connection and free on the default one'
        UncAudit.third.withNewTransaction { new UncAudit(code: 'free').third.save(validate: false, flush: true) }
        UncEventChecker checker = new UncEventChecker(target: UncAudit)
        ((ConfigurableApplicationEventPublisher) datastore.applicationEventPublisher).addApplicationListener(checker)

        when:
        UncBook book = new UncBook(name: 'newBook', innerSave: save, innerValidate: !save)
        UncBook saved = withSessions { book.third.save() }

        then: 'the check of the listener is made on the default connection, so the code is free'
        saved != null
        checker.errors != null
        !checker.errors.hasErrors()

        cleanup:
        checker.active = false

        where:
        entry         | save
        'saved'       | true
        'validated'   | false
    }

    void 'an update that is flushed at the commit of a save on the second connection is checked on the second connection'() {
        given: 'a book of the second connection, renamed to a value that exists only on the default connection'
        UncBook.second.withNewTransaction { new UncBook(name: 'toRename').second.save(validate: false, flush: true) }

        when: 'the rename is saved without a flush, so the update is validated when the transaction commits'
        UncBook.second.withNewTransaction {
            UncBook book = UncBook.second.findByName('toRename')
            book.name = 'onDefault'
            book.second.save()
        }

        then: 'the update was not cancelled, and the row of the second connection has the new name'
        UncBook.second.withNewTransaction { UncBook.second.findByName('onDefault') } != null
        UncBook.second.withNewTransaction { UncBook.second.findByName('toRename') } == null
    }

    void 'a validation API built from a mapping context that is called during a save on the second connection checks that connection'() {
        when: 'the validator of the book checks a book that only the default connection has through an API that has no datastore'
        UncBook book = new UncBook(name: 'newBook', innerPlain: true)
        UncBook saved = withSessions { book.second.save() }

        then: 'the check is made on the second connection, which the save named, so the value is free'
        saved != null
        UncBook.innerPlainErrors != null
        !UncBook.innerPlainErrors.hasErrors()
    }

    void 'a save through the default connection still checks the default connection'() {
        when:
        UncBook onDefault = new UncBook(name: 'onDefault')
        UncBook onSecond = new UncBook(name: 'onSecond')
        List saved = withSessions { [onDefault.save(), onSecond.save()] }

        then:
        saved[0] == null
        saved[1] != null
    }
}

class UncEventChecker implements ApplicationListener<ApplicationEvent> {

    boolean active = true
    Class target = UncBook
    Errors errors

    @Override
    void onApplicationEvent(ApplicationEvent event) {
        if (active && event instanceof ValidationEvent && target.isInstance(((ValidationEvent) event).entityObject)) {
            Object entity = ((ValidationEvent) event).entityObject
            Errors found = new BeanPropertyBindingResult(entity, 'entity')
            PersistentEntity persistent = (PersistentEntity) target.gormPersistentEntity
            persistent.mappingContext.getEntityValidator(persistent).validate(entity, found)
            errors = found
        }
    }
}

@Entity
class UncBook {

    String name

    // set by the validation below: the audit that the validator of a book with innerSave saves through the default connection
    static UncAudit innerSaved

    boolean innerSave
    boolean innerValidate
    boolean innerPlain
    boolean cascadeCheck

    // set by the validation below: the errors of the check of an entity that is mapped to the default connection only
    static Errors cascadeErrors

    // set by the validation below: the errors of a check made through an API that is built from a mapping context
    static Errors innerPlainErrors

    static transients = ['innerSave', 'innerValidate', 'innerPlain', 'cascadeCheck']

    static constraints = {
        name(unique: true, validator: { String value, UncBook book ->
            if (book.innerSave) {
                UncBook.innerSaved = new UncAudit(code: 'free')
                UncBook.innerSaved.save()
            }
            if (book.innerValidate) {
                new UncAudit(code: 'free').validate()
            }
            if (book.innerPlain) {
                UncBook probe = new UncBook(name: 'onDefault')
                UncBook.innerPlainErrors = new BeanPropertyBindingResult(probe, 'probe')
                GormValidationApi<UncBook> noDatastore = new GormValidationApi<UncBook>(UncBook,
                        UncBook.gormPersistentEntity.mappingContext, null)
                noDatastore.validate(probe)
                UncBook.innerPlainErrors = noDatastore.getErrors(probe)
            }
            if (book.cascadeCheck) {
                // the validator of the entity is called directly, as the validation of an association cascades to it
                UncPlain plain = new UncPlain(code: 'plain')
                Errors errors = new BeanPropertyBindingResult(plain, 'plain')
                UncPlain.gormPersistentEntity.mappingContext.getEntityValidator(UncPlain.gormPersistentEntity).validate(plain, errors)
                UncBook.cascadeErrors = errors
            }
            true
        })
    }

    static mapping = {
        version false
        datasource(ConnectionSource.ALL)
    }
}

@Entity
class UncAudit {

    String code

    static constraints = {
        code(unique: true)
    }

    static mapping = {
        version false
        datasource(ConnectionSource.ALL)
    }
}

@Entity
class UncPlain {

    String code

    static constraints = {
        code(unique: true)
    }
}
