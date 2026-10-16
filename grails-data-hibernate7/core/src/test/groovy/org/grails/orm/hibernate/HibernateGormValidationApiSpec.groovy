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
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.FlushMode
import org.springframework.core.env.PropertyResolver
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HibernateGormValidationApiSpec extends Specification {

    @Shared PropertyResolver configuration = DatastoreUtils.createPropertyResolver(
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url': 'jdbc:h2:mem:validationApiSpec;LOCK_TIMEOUT=10000'
    )
    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(configuration, ValidatedBook)
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    void "Test that HibernateGormValidationApi uses the shared template from the datastore"() {
        given:
        def api = (HibernateGormValidationApi) GormRegistry.instance.getValidationApi(ValidatedBook.name)

        expect:
        api instanceof HibernateGormValidationApi
        api.hibernateTemplate.is(hibernateDatastore.getHibernateTemplate())
    }

    @Rollback
    void "validate returns true (not a boxed Boolean) for a valid instance"() {
        given:
        def book = new ValidatedBook(title: 'Clean Code')

        when:
        def result = book.validate()

        then:
        result == true
        result instanceof Boolean
        !book.hasErrors()
    }

    @Rollback
    void "validate returns false for an invalid instance"() {
        given:
        def book = new ValidatedBook(title: null)

        when:
        def result = book.validate()

        then:
        result == false
        book.hasErrors()
        book.errors.getFieldError('title')
    }

    @Rollback
    void "validate with evict:false (default) leaves invalid instance in the session"() {
        given:
        def book = new ValidatedBook(title: 'Valid Title').save(flush: true)
        book.title = null
        def session = hibernateDatastore.sessionFactory.currentSession

        when:
        def result = book.validate(evict: false)

        then:
        result == false
        session.contains(book)
    }

    @Rollback
    void "validate with evict:true removes invalid instance from the session"() {
        given:
        def book = new ValidatedBook(title: 'Valid Title').save(flush: true)
        book.title = null
        def session = hibernateDatastore.sessionFactory.currentSession

        when:
        def result = book.validate(evict: true)

        then:
        result == false
        !session.contains(book)
    }

    void "a failed validate() restores the session flush mode so the transaction still flushes valid saves on commit"() {
        given:
        ValidatedBook.withNewTransaction { ValidatedBook.executeUpdate('delete from ValidatedBook') }
        FlushMode modeBefore = null
        FlushMode modeAfter = null
        boolean valid = true

        when:
        ValidatedBook.withTransaction {
            def session = hibernateDatastore.sessionFactory.currentSession
            new ValidatedBook(title: 'valid one').save()
            def invalid = new ValidatedBook(title: null)
            modeBefore = session.hibernateFlushMode
            valid = invalid.validate()
            modeAfter = session.hibernateFlushMode
            new ValidatedBook(title: 'valid two').save()
        }

        then:
        !valid
        modeAfter == modeBefore
        modeAfter != FlushMode.MANUAL
        ValidatedBook.withNewSession { ValidatedBook.list()*.title.sort() } == ['valid one', 'valid two']
    }

    void "an invalid instance that failed validate() is not persisted by the commit flush when it is never attached"() {
        given:
        ValidatedBook.withNewTransaction { ValidatedBook.executeUpdate('delete from ValidatedBook') }

        when:
        ValidatedBook.withTransaction {
            new ValidatedBook(title: 'ok').save()
            assert !new ValidatedBook(title: null).validate()
            assert new ValidatedBook(title: null).save() == null
        }

        then:
        ValidatedBook.withNewSession { ValidatedBook.list()*.title } == ['ok']
    }

    void "clearErrors then validate again within a transaction still restores the flush mode"() {
        given:
        ValidatedBook.withNewTransaction { ValidatedBook.executeUpdate('delete from ValidatedBook') }
        FlushMode mode = null

        when:
        ValidatedBook.withTransaction {
            def session = hibernateDatastore.sessionFactory.currentSession
            FlushMode before = session.hibernateFlushMode
            def book = new ValidatedBook(title: null)
            assert !book.validate()
            book.clearErrors()
            assert !book.validate()
            mode = session.hibernateFlushMode
            assert mode == before
            new ValidatedBook(title: 'saved').save()
        }

        then:
        mode != FlushMode.MANUAL
        ValidatedBook.withNewSession { ValidatedBook.list()*.title } == ['saved']
    }

    void "validate before the transaction begins in an earlier transaction does not stop a later transaction from flushing"() {
        given:
        ValidatedBook.withNewTransaction { ValidatedBook.executeUpdate('delete from ValidatedBook') }
        def invalid = new ValidatedBook(title: null)

        when:
        boolean valid = ValidatedBook.withNewTransaction { invalid.validate() }
        ValidatedBook.withTransaction {
            new ValidatedBook(title: 'after').save()
        }

        then:
        !valid
        ValidatedBook.withNewSession { ValidatedBook.list()*.title } == ['after']
    }

    void "an invalid dirty instance is not auto flushed by a query after a failed validate()"() {
        given:
        ValidatedBook.withNewTransaction { ValidatedBook.executeUpdate('delete from ValidatedBook') }
        def id = ValidatedBook.withNewTransaction { new ValidatedBook(title: 'original').save(flush: true).id }

        when:
        ValidatedBook.withTransaction {
            def book = ValidatedBook.get(id)
            book.title = null
            assert !book.validate()
            ValidatedBook.findAllByTitle('anything')
            book.discard()
        }

        then:
        notThrown(Exception)
        ValidatedBook.withNewSession { ValidatedBook.get(id).title } == 'original'
    }

    @Rollback
    void "validate with specific fields only validates those fields"() {
        given:
        def book = new ValidatedBook(title: null, author: null)

        when:
        def result = book.validate(['author'])

        then:
        result == true
        !book.hasErrors()
    }
}

@Entity
class ValidatedBook {
    String title
    String author

    static constraints = {
        title nullable: false
        author nullable: true
    }
}
