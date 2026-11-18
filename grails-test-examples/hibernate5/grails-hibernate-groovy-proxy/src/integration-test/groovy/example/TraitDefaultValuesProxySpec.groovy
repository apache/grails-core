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
package example

import datasources.Application
import grails.testing.mixin.integration.Integration
import groovy.transform.CompileStatic
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import spock.lang.Issue
import spock.lang.Specification

/**
 * Exercises, in the running application, the lazy proxies of a domain class whose trait declares
 * default values: creating them, loading through them, and sending them through Java serialization.
 *
 * Without the Groovy-aware proxy class, Hibernate cannot even construct the proxy, because the
 * trait initializer runs from the proxy constructor before the interceptor exists. Without the
 * Groovy-aware serialized form, a proxy that comes back from serialization is rebuilt as a stock
 * proxy, which fails the same way on Hibernate 5 and is rejected outright on Hibernate 7.
 */
@Issue('https://github.com/apache/grails-core/issues/16600')
@Integration(applicationClass = Application)
class TraitDefaultValuesProxySpec extends Specification {

    void cleanup() {
        Book.withTransaction {
            Loan.executeUpdate('delete from Loan')
            Book.executeUpdate('delete from Book')
        }
    }

    void "load() of a domain class whose trait declares default values returns a usable lazy proxy"() {
        given: 'a persisted book'
        Long id = null
        Book.withTransaction {
            id = new Book(title: 'The Grails Guide').save(flush: true, failOnError: true).id
        }

        when: 'a proxy is requested in a new session and read through'
        Book proxy = null
        boolean uninitializedAfterLoad = false
        Long idWithoutInitializing = null
        boolean uninitializedAfterId = false
        String title = null
        String createdBy = null
        Book.withTransaction {
            proxy = Book.load(id)
            uninitializedAfterLoad = !Hibernate.isInitialized(proxy)
            idWithoutInitializing = proxy.id
            uninitializedAfterId = !Hibernate.isInitialized(proxy)
            title = proxy.title
            createdBy = proxy.createdBy
        }

        then: 'the proxy was created, answered its identifier without loading, and loaded on first use'
        proxy instanceof HibernateProxy
        proxy instanceof Auditable
        uninitializedAfterLoad
        idWithoutInitializing == id
        uninitializedAfterId
        title == 'The Grails Guide'
        createdBy == 'N/A'
        Hibernate.isInitialized(proxy)
    }

    void "a lazy many-to-one to such a domain class loads through its proxy"() {
        given: 'a persisted loan of a book'
        Long loanId = null
        Loan.withTransaction {
            Book book = new Book(title: 'The Grails Guide').save(flush: true, failOnError: true)
            loanId = new Loan(book: book, borrower: 'Ada').save(flush: true, failOnError: true).id
        }

        when: 'the loan is loaded in a new session and its book is read through the association'
        Loan loan = null
        boolean bookUninitializedBefore = false
        String title = null
        String createdBy = null
        Loan.withTransaction {
            loan = Loan.get(loanId)
            bookUninitializedBefore = !Hibernate.isInitialized(loan.book)
            title = loan.book.title
            createdBy = loan.book.createdBy
        }

        then: 'the association was a lazy proxy and reading through it loaded the book'
        bookUninitializedBefore
        loan.book instanceof HibernateProxy
        title == 'The Grails Guide'
        createdBy == 'N/A'
        Hibernate.isInitialized(loan.book)
    }

    void "an uninitialized proxy of such a domain class survives Java serialization"() {
        given: 'a persisted book'
        Long id = null
        Book.withTransaction {
            id = new Book(title: 'The Grails Guide').save(flush: true, failOnError: true).id
        }

        when: 'an uninitialized proxy is serialized while its session is open, and read back'
        Book deserialized = null
        Book.withTransaction {
            deserialized = roundTrip(Book.load(id)) as Book
        }
        boolean uninitializedAfterRead = !Hibernate.isInitialized(deserialized)
        Long idWithoutInitializing = deserialized.id
        boolean uninitializedAfterId = !Hibernate.isInitialized(deserialized)

        and: 'the deserialized proxy is read through, which loads it through the SessionFactory it came from'
        String title = deserialized.title
        String createdBy = deserialized.createdBy

        then: 'it came back as an uninitialized proxy that answers its identifier without loading, and then loads'
        deserialized instanceof HibernateProxy
        deserialized instanceof Auditable
        uninitializedAfterRead
        idWithoutInitializing == id
        uninitializedAfterId
        title == 'The Grails Guide'
        createdBy == 'N/A'
        Hibernate.isInitialized(deserialized)
    }

    void "a loan with its book still lazy survives Java serialization"() {
        given: 'a persisted loan of a book'
        Long loanId = null
        Long bookId = null
        Loan.withTransaction {
            Book book = new Book(title: 'The Grails Guide').save(flush: true, failOnError: true)
            bookId = book.id
            loanId = new Loan(book: book, borrower: 'Ada').save(flush: true, failOnError: true).id
        }

        when: 'the loan is loaded in a new session and serialized with the book still lazy'
        Loan deserialized = null
        boolean bookUninitializedBefore = false
        Loan.withTransaction {
            Loan loan = Loan.get(loanId)
            bookUninitializedBefore = !Hibernate.isInitialized(loan.book)
            deserialized = roundTrip(loan) as Loan
        }
        boolean bookUninitializedAfter = !Hibernate.isInitialized(deserialized.book)
        Long idWithoutInitializing = deserialized.book.id
        boolean bookUninitializedAfterId = !Hibernate.isInitialized(deserialized.book)

        and: 'the book is read through, which loads it through the SessionFactory it came from'
        String title = deserialized.book.title
        String createdBy = deserialized.book.createdBy

        then: 'the book came back as an uninitialized proxy that answers its identifier without loading, and then loads'
        bookUninitializedBefore
        deserialized.borrower == 'Ada'
        deserialized.book instanceof HibernateProxy
        deserialized.book instanceof Auditable
        bookUninitializedAfter
        idWithoutInitializing == bookId
        bookUninitializedAfterId
        title == 'The Grails Guide'
        createdBy == 'N/A'
        Hibernate.isInitialized(deserialized.book)
    }

    @CompileStatic
    private static Object roundTrip(Object object) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { ObjectOutputStream out -> out.writeObject(object) }
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable { ObjectInputStream input ->
            input.readObject()
        }
    }
}
