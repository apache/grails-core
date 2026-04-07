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
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.hibernate.proxy.HibernateProxy
import spock.lang.Shared
import spock.lang.Unroll

class GrailsHibernateUtilSpec extends HibernateGormDatastoreSpec {

    @Shared HibernateProxyHandler originalProxyHandler = GrailsHibernateUtil.proxyHandler
    HibernateProxyHandler proxyHandlerMock = Mock(HibernateProxyHandler)

    void setupSpec() {
        manager.addAllDomainClasses([GHUBook, GHUAuthor, GHUAnnotatedEntity])
    }

    def setup() {
        GrailsHibernateUtil.setProxyHandler(proxyHandlerMock)
    }

    def cleanup() {
        GrailsHibernateUtil.setProxyHandler(originalProxyHandler)
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
        def result = GrailsHibernateUtil.unwrapIfProxy(obj)

        then:
        1 * proxyHandlerMock.unwrap(obj) >> unwrapped
        result == unwrapped
    }

    def "test unwrapProxy"() {
        given:
        def proxy = Mock(HibernateProxy)
        def unwrapped = new Object()

        when:
        def result = GrailsHibernateUtil.unwrapProxy(proxy)

        then:
        1 * proxyHandlerMock.unwrap(proxy) >> unwrapped
        result == unwrapped
    }

    def "test getAssociationProxy and isInitialized"() {
        given:
        def book = new GHUBook(title: "Carrie")
        def proxy = Mock(HibernateProxy)

        when:
        def result = GrailsHibernateUtil.getAssociationProxy(book, "title")
        def initialized = GrailsHibernateUtil.isInitialized(book, "title")

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
