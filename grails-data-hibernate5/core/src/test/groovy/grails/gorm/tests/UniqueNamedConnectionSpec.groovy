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
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

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

@Entity
class UncBook {

    String name

    // set by the validation below: the audit that the validator of a book with innerSave saves through the default connection
    static UncAudit innerSaved

    boolean innerSave
    boolean cascadeCheck

    // set by the validation below: the errors of the check of an entity that is mapped to the default connection only
    static Errors cascadeErrors

    static transients = ['innerSave', 'cascadeCheck']

    static constraints = {
        name(unique: true, validator: { String value, UncBook book ->
            if (book.innerSave) {
                UncBook.innerSaved = new UncAudit(code: 'free')
                UncBook.innerSaved.save()
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
