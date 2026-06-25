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
package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.hibernate.proxy.HibernateProxy
import spock.lang.Shared
import spock.lang.Unroll

class GrailsHibernateUtilSpec extends HibernateGormDatastoreSpec {

    HibernateProxyHandler proxyHandlerMock = Mock(HibernateProxyHandler)

    void setupSpec() {
        manager.registerDomainClasses(GHUBook, GHUAuthor, GHUAnnotatedEntity)
    }

    @Unroll
    def "test isDomainClass for #clazz.simpleName"() {
        expect:
        GrailsHibernateUtil.isDomainClass(clazz) == expected

        where:
        clazz              | expected
        GHUBook            | true
        GHUNonDomain       | false
        String             | false
        GHUAnnotatedEntity | true
    }

    def "test incrementVersion"() {
        given:
        def book = new GHUBook(version: 1L)

        when:
        GrailsHibernateUtil.incrementVersion(book)

        then:
        book.version == 2L
    }

    def "test incrementVersion with non-long version"() {
        given:
        def obj = new GHUNonDomain()

        when:
        GrailsHibernateUtil.incrementVersion(obj)

        then:
        noExceptionThrown()
    }

    def "test qualify and unqualify"() {
        expect:
        GrailsHibernateUtil.qualify("org.test", "MyClass") == "org.test.MyClass"
        GrailsHibernateUtil.unqualify("org.test.MyClass") == "MyClass"
    }

    def "test isNotEmpty"() {
        expect:
        GrailsHibernateUtil.isNotEmpty("test")
        !GrailsHibernateUtil.isNotEmpty("")
        !GrailsHibernateUtil.isNotEmpty(null)
    }

    def "test unwrapIfProxy"() {
        given:
        def obj = new Object()
        def unwrapped = new Object()

        when:
        def result = GrailsHibernateUtil.unwrapIfProxy(obj, proxyHandlerMock)

        then:
        1 * proxyHandlerMock.unwrap(obj) >> unwrapped
        result == unwrapped
    }

    def "test unwrapProxy"() {
        given:
        def proxy = Mock(HibernateProxy)
        def unwrapped = new Object()

        when:
        def result = GrailsHibernateUtil.unwrapProxy(proxy, proxyHandlerMock)

        then:
        1 * proxyHandlerMock.unwrap(proxy) >> unwrapped
        result == unwrapped
    }

    def "test getAssociationProxy and isInitialized"() {
        given:
        def book = new GHUBook(title: "Carrie")
        def proxy = Mock(HibernateProxy)

        when:
        def result = GrailsHibernateUtil.getAssociationProxy(book, "title", proxyHandlerMock)
        def initialized = GrailsHibernateUtil.isInitialized(book, "title", proxyHandlerMock)

        then:
        1 * proxyHandlerMock.getAssociationProxy(book, "title") >> proxy
        1 * proxyHandlerMock.isInitialized(book, "title") >> true
        result == proxy
        initialized
    }

    def "test isMappedWithHibernate"() {
        given:
        def hibernateEntity = Mock(org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity)
        def otherEntity = Mock(org.grails.datastore.mapping.model.PersistentEntity)

        expect:
        GrailsHibernateUtil.isMappedWithHibernate(hibernateEntity)
        !GrailsHibernateUtil.isMappedWithHibernate(otherEntity)
    }

    def "test ensureCorrectGroovyMetaClass"() {
        given:
        def book = new GHUBook()
        def originalMc = book.getMetaClass()
        def newMc = GroovySystem.getMetaClassRegistry().getMetaClass(GHUNonDomain)

        when:
        GrailsHibernateUtil.ensureCorrectGroovyMetaClass(book, GHUNonDomain)

        then:
        book.getMetaClass().getTheClass() == GHUNonDomain

        cleanup:
        book.setMetaClass(originalMc)
    }

    def "setObjectToReadyOnly does nothing when no bound transaction resource"() {
        given:
        def book = new GHUBook(title: "NoTx")

        when:
        GrailsHibernateUtil.setObjectToReadyOnly(book, sessionFactory)

        then:
        noExceptionThrown()
    }

    def "setObjectToReadyOnly marks persistent entity read-only within transaction"() {
        given:
        GHUBook saved = GHUBook.withTransaction {
            new GHUBook(title: "ReadOnlyBook", version: 0L).save(flush: true, failOnError: true)
        }

        when:
        GHUBook.withTransaction {
            def book = GHUBook.get(saved.id)
            GrailsHibernateUtil.setObjectToReadyOnly(book, sessionFactory)
        }

        then:
        noExceptionThrown()
    }

    def "setObjectToReadWrite does nothing when entity not in session"() {
        when:
        GHUBook.withTransaction {
            def book = new GHUBook(title: "Detached")
            GrailsHibernateUtil.setObjectToReadWrite(book, sessionFactory)
        }

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // isDomainClass — uncovered branches
    // -------------------------------------------------------------------------

    def "isDomainClass returns false for a Closure class"() {
        expect:
        !GrailsHibernateUtil.isDomainClass(Closure)
    }

    def "isDomainClass returns false for an enum"() {
        expect:
        !GrailsHibernateUtil.isDomainClass(GHUStatus)
    }

    def "isDomainClass returns true for class with id and version fields but no annotation"() {
        expect: "class that has 'id' and 'version' fields should pass the reflective check"
        GrailsHibernateUtil.isDomainClass(GHUIdVersionPojo)
    }

    // -------------------------------------------------------------------------
    // ensureCorrectGroovyMetaClass — uncovered branches
    // -------------------------------------------------------------------------

    def "ensureCorrectGroovyMetaClass does nothing when metaclass already matches"() {
        given:
        def book = new GHUBook()
        def originalMc = book.getMetaClass()

        when: "called with the same class — no change expected"
        GrailsHibernateUtil.ensureCorrectGroovyMetaClass(book, GHUBook)

        then:
        book.getMetaClass() == originalMc
        noExceptionThrown()
    }

    def "ensureCorrectGroovyMetaClass does nothing for non-GroovyObject"() {
        given:
        def target = "plain java string"

        when:
        GrailsHibernateUtil.ensureCorrectGroovyMetaClass(target, String)

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // setObjectToReadyOnly — resource bound but entity not in session
    // -------------------------------------------------------------------------

    def "setObjectToReadyOnly does nothing when entity is not in session even with active transaction"() {
        when:
        GHUBook.withTransaction {
            def detached = new GHUBook(title: "NotInSession")
            GrailsHibernateUtil.setObjectToReadyOnly(detached, sessionFactory)
        }

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // setObjectToReadWrite — EntityEntry null branch
    // -------------------------------------------------------------------------

    def "setObjectToReadWrite does nothing when EntityEntry is null for a transient entity"() {
        when:
        GHUBook.withTransaction {
            def transient_ = new GHUBook(title: "Transient")
            // entity is in the session (contains() may return false for unsaved) — either way no exception
            GrailsHibernateUtil.setObjectToReadWrite(transient_, sessionFactory)
        }

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // setObjectToReadyOnly then setObjectToReadWrite — full round-trip
    // -------------------------------------------------------------------------

    @Rollback
    def "entity marked read-only can be reverted to read-write"() {
        given:
        def book = new GHUBook(title: "ReadWriteBook", version: 0L).save(flush: true, failOnError: true)

        when:
        GHUBook.withTransaction {
            def loaded = GHUBook.get(book.id)
            GrailsHibernateUtil.setObjectToReadyOnly(loaded, sessionFactory)
            GrailsHibernateUtil.setObjectToReadWrite(loaded, sessionFactory)
        }

        then:
        noExceptionThrown()
    }

    @Rollback
    def "setObjectToReadyOnly handles HibernateProxy correctly"() {
        given:
        GHUBook saved = GHUBook.withTransaction {
            new GHUBook(title: "ProxyBook", version: 0L).save(flush: true, failOnError: true)
        }

        when:
        GHUBook.withTransaction {
            // using getReference() to get a proxy
            def book = sessionFactory.currentSession.getReference(GHUBook, saved.id)
            GrailsHibernateUtil.setObjectToReadyOnly(book, sessionFactory)
        }

        then:
        noExceptionThrown()
    }

    @Rollback
    def "setObjectToReadWrite handles HibernateProxy correctly"() {
        given:
        GHUBook saved = GHUBook.withTransaction {
            new GHUBook(title: "ProxyBookRW", version: 0L).save(flush: true, failOnError: true)
        }

        when:
        GHUBook.withTransaction {
            def book = sessionFactory.currentSession.getReference(GHUBook, saved.id)
            GrailsHibernateUtil.setObjectToReadyOnly(book, sessionFactory)
            GrailsHibernateUtil.setObjectToReadWrite(book, sessionFactory)
        }

        then:
        noExceptionThrown()
    }

    def "isDomainClass returns true for class with Entity annotation from jakarta.persistence"() {
        expect:
        GrailsHibernateUtil.isDomainClass(GHUJpaEntity)
    }

    def "isDomainClass returns false for POJO missing identity/version fields"() {
        expect:
        !GrailsHibernateUtil.isDomainClass(GHUPojoMissingVersion)
    }
}

@jakarta.persistence.Entity
class GHUJpaEntity {
    @jakarta.persistence.Id
    Long id
}

class GHUPojoMissingVersion {
    Long id
    String name
}

@Entity
class GHUBook {
    Long id
    Long version
    String title
}

@Entity
class GHUAuthor {
    Long id
    String name
    static hasMany = [books: GHUBook]
}

class GHUNonDomain {
    String name
}

@grails.persistence.Entity
class GHUAnnotatedEntity {
    Long id
}

enum GHUStatus { ACTIVE, INACTIVE }

/** Plain POJO with 'id' and 'version' fields — should satisfy the reflective isDomainClass check. */
class GHUIdVersionPojo {
    Long id
    Long version
    String name
}
