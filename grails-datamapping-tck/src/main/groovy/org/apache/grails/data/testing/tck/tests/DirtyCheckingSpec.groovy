/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.domains.Card
import org.apache.grails.data.testing.tck.domains.CardProfile
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.TestAuthor
import org.apache.grails.data.testing.tck.domains.TestBook
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.proxy.ProxyHandler
import spock.lang.IgnoreIf

/**
 * @author Graeme Rocher
 */
class DirtyCheckingSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.addAll([Person, TestBook, TestAuthor, Card, CardProfile])
    }

    ProxyHandler proxyHandler

    def setup() {
        proxyHandler = manager.session.getMappingContext().proxyHandler
    }

    void "Test that dirty checking methods work when changing entities"() {

        when: "A new instance is created"
        def p = new Person(firstName: "Homer", lastName: "Simpson")
        p.save(flush: true)

        then: "The instance is not dirty"
        !p.isDirty()
        !p.isDirty("firstName")

        when: "The instance is changed"
        p.firstName = "Bart"

        then: "The instance is now dirty"
        p.isDirty()
        p.isDirty("firstName")
        p.dirtyPropertyNames == ['firstName']
        p.getPersistentValue('firstName') == "Homer"

        when: "The instance is loaded from the db"
        p.save(flush: true)
        manager.session.clear()
        p = Person.get(p.id)

        then: "The instance is not dirty"
        !p.isDirty()
        !p.isDirty('firstName')

        when: "The instance is changed"
        p.firstName = "Lisa"

        then: "The instance is dirty"
        p.isDirty()
        p.isDirty("firstName")
    }

    void "test relationships not marked dirty when proxies are used"() {

        given:
        Long bookId = new TestBook(title: 'Martin Fierro', author: new TestAuthor(name: 'Jose Hernandez'))
                .save(flush: true)
                .id
        manager.session.flush()
        manager.session.clear()

        when:
        TestBook book = TestBook.get(bookId)
        book.author = book.author

        then:
        proxyHandler.isProxy(book.author)
        !book.isDirty('author')
        !book.isDirty()

        cleanup:
        TestBook.deleteAll()
        TestAuthor.deleteAll()
    }

    void "test relationships not marked dirty when domain objects are used"() {

        given:
        Long bookId = new TestBook(title: 'Martin Fierro', author: new TestAuthor(name: 'Jose Hernandez'))
                .save(flush: true, failOnError: true)
                .id
        manager.session.flush()
        manager.session.clear()

        when:
        TestBook book = TestBook.get(bookId)
        book.author = TestAuthor.get(book.author.id)

        then:
        !proxyHandler.isProxy(book.author)
        !book.isDirty('author')
        !book.isDirty()

        cleanup:
        TestBook.deleteAll()
        TestAuthor.deleteAll()
    }

    void "test relationships are marked dirty when proxies are used but different"() {
        given:
        Long bookId = new TestBook(title: 'Martin Fierro', author: new TestAuthor(name: 'Jose Hernandez'))
                .save(flush: true, failOnError: true)
                .id
        Long otherAuthorId = new TestAuthor(name: "JD").save(flush: true, failOnError: true).id
        manager.session.flush()
        manager.session.clear()

        when:
        TestBook book = TestBook.get(bookId)
        book.author = TestAuthor.load(otherAuthorId)

        then:
        proxyHandler.isProxy(book.author)
        book.isDirty('author')
        book.isDirty()

        cleanup:
        TestBook.deleteAll()
        TestAuthor.deleteAll()
    }

    void "test relationships marked dirty when domain objects are used and changed"() {

        given:
        Long bookId = new TestBook(title: 'Martin Fierro', author: new TestAuthor(name: 'Jose Hernandez'))
                .save(flush: true, failOnError: true)
                .id
        Long otherAuthorId = new TestAuthor(name: "JD").save(flush: true, failOnError: true).id
        manager.session.flush()
        manager.session.clear()

        when:
        TestBook book = TestBook.get(bookId)
        book.author = TestAuthor.get(otherAuthorId)

        then:
        !proxyHandler.isProxy(book.author)
        book.isDirty('author')
        book.isDirty()

        cleanup:
        TestBook.deleteAll()
        TestAuthor.deleteAll()
    }

    @IgnoreIf({ !Boolean.getBoolean('mongodb.gorm.suite')})
    void "test initialized proxy is not marked as dirty"() {

        given:
        Card card = new Card(cardNumber: "1111-2222-3333-4444")
        card.cardProfile = new CardProfile(fullName: "JD")
        card.save(flush: true, failOnError: true)
        manager.session.flush()
        manager.session.clear()

        when:
        card = Card.get(card.id)

        then:
        proxyHandler.isProxy(card.cardProfile)

        when:
        card.cardProfile.hashCode()

        then:
        proxyHandler.isInitialized(card.cardProfile)
        !card.isDirty()

        cleanup:
        Card.deleteAll()
        CardProfile.deleteAll()
    }

}
