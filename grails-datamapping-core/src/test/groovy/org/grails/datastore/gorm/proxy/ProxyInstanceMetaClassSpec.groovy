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
package org.grails.datastore.gorm.proxy

import org.codehaus.groovy.runtime.InvokerHelper
import org.springframework.dao.DataIntegrityViolationException

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Exercises {@link ProxyInstanceMetaClass} - the per-instance metaclass installed by
 * {@link GroovyProxyFactory} on lazily-loaded GORM proxies - through the public
 * {@code proxy()}/property/method GORM API, plus a couple of direct calls covering the
 * field-access ({@code getAttribute}/{@code setAttribute}) hooks that ordinary property
 * syntax does not exercise.
 */
class ProxyInstanceMetaClassSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(ProxyInstanceMetaClassSpecBook)

    void setup() {
        datastore.mappingContext.proxyFactory = new GroovyProxyFactory()
    }

    void "id, initialized and metaClass can be read on a proxy without resolving it"() {
        when:
        def book = ProxyInstanceMetaClassSpecBook.proxy(999L)

        then:
        book.id == 999L
        !book.isInitialized()
        !book.initialized
        book.metaClass instanceof ProxyInstanceMetaClass
    }

    void "accessing a real property on a proxy for a non-existent instance throws DataIntegrityViolationException"() {
        given:
        def book = ProxyInstanceMetaClassSpecBook.proxy(999L)

        when:
        book.title

        then:
        thrown(DataIntegrityViolationException)
    }

    void "accessing a real property resolves and initializes the proxy exactly once"() {
        given:
        def id = new ProxyInstanceMetaClassSpecBook(title: 'Groovy in Action').save(flush: true).id
        def book = ProxyInstanceMetaClassSpecBook.proxy(id)

        expect:
        !book.isInitialized()

        when:
        def title = book.title

        then:
        title == 'Groovy in Action'
        book.isInitialized()
        book.initialized
        book.target != null
        book.getTarget() != null
    }

    void "calling a domain method on a proxy resolves the target before delegating the call"() {
        given:
        def id = new ProxyInstanceMetaClassSpecBook(title: 'Grails in Action').save(flush: true).id
        def book = ProxyInstanceMetaClassSpecBook.proxy(id)

        expect:
        !book.isInitialized()

        when:
        def description = book.describe()

        then:
        description == 'Book: Grails in Action'
        book.isInitialized()
    }

    void "setting the metaClass property to null does not resolve a proxy for a non-existent instance"() {
        given:
        def book = ProxyInstanceMetaClassSpecBook.proxy(999L)

        when:
        book.metaClass = null

        then:
        noExceptionThrown()
        book.metaClass != null
    }

    void "calling setMetaClass(null) directly on a proxy for a non-existent instance does not resolve it"() {
        given:
        def book = ProxyInstanceMetaClassSpecBook.proxy(999L)

        when:
        book.setMetaClass(null)

        then:
        noExceptionThrown()
        book.metaClass != null
    }

    void "getAttribute answers id/initialized/target without needing the shell object, resolving only for target"() {
        given: 'a real persisted instance and a metaclass built the same way GroovyProxyFactory builds one'
        def session = datastore.connect()
        def book = new ProxyInstanceMetaClassSpecBook(title: 'Real Book')
        session.persist(book)
        session.flush()
        def id = book.id
        session.clear()
        def delegate = InvokerHelper.getMetaClass(ProxyInstanceMetaClassSpecBook)
        def proxyMetaClass = new ProxyInstanceMetaClass(delegate, session, id)

        expect:
        proxyMetaClass.getAttribute(null, 'id') == id
        !proxyMetaClass.isProxyInitiated()

        and:
        proxyMetaClass.getAttribute(null, 'initialized') == false
        !proxyMetaClass.isProxyInitiated()

        when:
        def target = proxyMetaClass.getAttribute(null, 'target')

        then:
        target != null
        proxyMetaClass.isProxyInitiated()
    }

    void "setAttribute writes the field on the resolved target"() {
        given:
        def session = datastore.connect()
        def book = new ProxyInstanceMetaClassSpecBook(title: 'Original Title')
        session.persist(book)
        session.flush()
        def id = book.id
        session.clear()
        def delegate = InvokerHelper.getMetaClass(ProxyInstanceMetaClassSpecBook)
        def proxyMetaClass = new ProxyInstanceMetaClass(delegate, session, id)

        when:
        proxyMetaClass.setAttribute(null, 'title', 'Updated Title')

        then:
        proxyMetaClass.getProxyTarget().title == 'Updated Title'
    }
}

@Entity
class ProxyInstanceMetaClassSpecBook {
    String title

    String describe() {
        "Book: ${title}"
    }
}
