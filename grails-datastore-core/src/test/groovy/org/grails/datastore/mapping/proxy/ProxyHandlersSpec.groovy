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
package org.grails.datastore.mapping.proxy

import java.lang.reflect.Method

import groovy.lang.GroovyObject
import groovy.lang.MetaClass
import org.codehaus.groovy.runtime.InvokerHelper
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Specification

import org.grails.datastore.mapping.collection.PersistentCollection
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.PersistentEntity

class ProxyHandlersSpec extends Specification {

    KeyValueMappingContext mappingContext = new KeyValueMappingContext('test')
    Session session = Mock(Session) { getMappingContext() >> mappingContext }
    JavassistProxyFactory proxyFactory = new JavassistProxyFactory()

    void setup() {
        mappingContext.addPersistentEntities(PxBook, PxAuthor)
    }

    void "groovy object handler serves the meta class of the proxied class before resolving"() {
        given:
        GroovyObjectMethodHandler handler = new GroovyObjectMethodHandler(PxBook)
        PxBook self = new PxBook(title: 'self')

        expect:
        handler.getProperty(self, 'metaClass').is(InvokerHelper.getMetaClass(PxBook))
        handler.getThisMetaClass().is(InvokerHelper.getMetaClass(PxBook))
        handler.getProperty(self, 'title') == 'self'
        handler.invokeThisMethod(self, 'getMetaClass', [] as Object[]).is(handler.getThisMetaClass())
        handler.invokeThisMethod(self, 'getTitle', [] as Object[]) == 'self'
        handler.wasHandled('x')
        !handler.wasHandled(GroovyObjectMethodHandler.INVOKE_IMPLEMENTATION)

        when:
        MetaClass replacement = Mock(MetaClass)
        handler.setProperty(self, 'metaClass', replacement)

        then:
        handler.getThisMetaClass().is(replacement)

        when:
        handler.setProperty(self, 'title', 'changed')

        then:
        self.title == 'changed'

        when:
        handler.invokeThisMethod(self, 'setMetaClass', [null] as Object[]) == Void
        handler.setThisMetaClass(null)

        then:
        handler.getThisMetaClass().is(InvokerHelper.getMetaClass(PxBook))
    }

    void "groovy object handler routes GroovyObject methods through handleInvocation"() {
        given:
        GroovyObjectMethodHandler handler = new GroovyObjectMethodHandler(PxBook)
        PxBook self = new PxBook(title: 'self')
        Method getMetaClass = GroovyObject.getMethod('getMetaClass')
        Method setMetaClass = GroovyObject.getMethod('setMetaClass', MetaClass)
        Method getProperty = GroovyObject.getMethod('getProperty', String)
        Method setProperty = GroovyObject.getMethod('setProperty', String, Object)
        Method invokeMethod = GroovyObject.getMethod('invokeMethod', String, Object)
        Method getTitle = PxBook.getMethod('getTitle')
        MetaClass replacement = Mock(MetaClass)

        expect:
        handler.handleInvocation(self, getMetaClass, [] as Object[]).is(handler.getThisMetaClass())
        handler.handleInvocation(self, getProperty, ['metaClass'] as Object[]).is(handler.getThisMetaClass())
        handler.handleInvocation(self, getProperty, ['title'] as Object[]) == 'self'
        handler.handleInvocation(self, invokeMethod, ['getTitle', [] as Object[]] as Object[]) == 'self'
        handler.handleInvocation(self, getTitle, [] as Object[]).is(GroovyObjectMethodHandler.INVOKE_IMPLEMENTATION)

        when:
        Object result = handler.handleInvocation(self, setProperty, ['title', 'set'] as Object[])

        then:
        result == Void
        self.title == 'set'

        when:
        result = handler.handleInvocation(self, setProperty, ['metaClass', replacement] as Object[])

        then:
        result == Void
        handler.getThisMetaClass().is(replacement)

        when:
        result = handler.handleInvocation(self, setMetaClass, [null] as Object[])

        then:
        result == Void
        handler.getThisMetaClass().is(InvokerHelper.getMetaClass(PxBook))

        when: 'a method that is not handled falls through to proceed'
        Method length = String.getMethod('length')

        then:
        handler.invoke('abc', length, length, [] as Object[]) == 3
        handler.invoke(self, getTitle, getTitle, [] as Object[]) == 'set'
    }

    void "entity proxy handler answers proxy state without touching the target"() {
        given:
        PxBook target = new PxBook(title: 'target')
        int resolved = 0
        EntityProxyMethodHandler handler = new EntityProxyMethodHandler(PxBook) {
            @Override
            protected Object isProxyInitiated(Object self) { resolved > 0 }
            @Override
            protected Object getProxyKey(Object self) { 7L }
            @Override
            protected Object resolveDelegate(Object self) { resolved++; target }
        }
        Object self = new Object()

        expect:
        handler.getProperty(self, 'proxy') == true
        handler.getProperty(self, 'proxyKey') == 7L
        handler.getProperty(self, 'id') == 7L
        handler.getProperty(self, 'initialized') == false
        handler.invokeThisMethod(self, 'isProxy', [] as Object[]) == true
        handler.invokeThisMethod(self, 'getProxyKey', [] as Object[]) == 7L
        handler.invokeThisMethod(self, 'getId', [] as Object[]) == 7L
        handler.invokeThisMethod(self, 'isInitialized', [] as Object[]) == false
        resolved == 0

        when:
        Object result = handler.invokeThisMethod(self, 'initialize', [] as Object[])

        then:
        result == Void
        resolved == 1
        handler.getProperty(self, 'initialized') == true
        handler.getProperty(self, 'target').is(target)
        handler.invokeThisMethod(self, 'getTarget', [] as Object[]).is(target)
        handler.getProperty(self, 'title') == 'target'
        handler.invokeThisMethod(self, 'getTitle', [] as Object[]) == 'target'
        handler.handleInvocation(self, PxBook.getMethod('getId'), [] as Object[]) == 7L
        handler.handleInvocation(self, PxBook.getMethod('getTitle'), [] as Object[])
                .is(GroovyObjectMethodHandler.INVOKE_IMPLEMENTATION)
    }

    void "session proxies lazily retrieve the target from the session"() {
        given:
        PxBook target = new PxBook(title: 'lazy')

        when:
        PxBook proxy = proxyFactory.createProxy(session, PxBook, 3L)

        then:
        proxy instanceof EntityProxy
        proxy instanceof GroovyObject
        proxyFactory.isProxy(proxy)
        !proxyFactory.isInitialized(proxy)
        proxyFactory.getIdentifier(proxy) == 3L
        proxyFactory.getProxiedClass(proxy) == PxBook
        proxyFactory.getProxiedClass(target) == PxBook
        proxy.getId() == 3L
        ((EntityProxy) proxy).proxyKey == 3L
        proxy.metaClass.theClass == proxy.getClass()
        0 * session.retrieve(*_)

        when:
        String title = proxy.getTitle()

        then:
        1 * session.retrieve(PxBook, 3L) >> target
        title == 'lazy'
        proxyFactory.isInitialized(proxy)
        proxyFactory.unwrap(proxy).is(target)
        ((EntityProxy) proxy).target.is(target)

        when: 'the target is only resolved once'
        proxy.setTitle('updated')
        proxyFactory.initialize(proxy)

        then:
        0 * session.retrieve(*_)
        target.title == 'updated'
        proxyFactory.unwrap(target).is(target)
    }

    void "a session proxy whose target cannot be found fails on initialization"() {
        given:
        session.retrieve(PxBook, 99L) >> null
        PxBook proxy = proxyFactory.createProxy(session, PxBook, 99L)

        when:
        proxy.getTitle()

        then:
        DataIntegrityViolationException e = thrown()
        e.message == 'Proxy for [' + PxBook.name + ':99] could not be initialized'
    }

    void "association query proxies resolve their target through the executor"() {
        given:
        PersistentEntity bookEntity = mappingContext.getPersistentEntity(PxBook.name)
        PxBook target = new PxBook(title: 'from executor')
        List results = returnsKeys ? [11L] : [target]
        AssociationQueryExecutor executor = Mock(AssociationQueryExecutor) {
            getIndexedEntity() >> bookEntity
            doesReturnKeys() >> returnsKeys
        }

        when:
        PxBook proxy = proxyFactory.createProxy(session, executor, 5L)

        then:
        proxyFactory.isProxy(proxy)
        !proxyFactory.isInitialized(proxy)
        proxyFactory.getIdentifier(proxy) == 5L
        proxy.getId() == 5L

        when:
        String title = proxy.getTitle()

        then:
        1 * executor.query(5L) >> results
        (returnsKeys ? 1 : 0) * session.retrieve(PxBook, 11L) >> target
        title == 'from executor'
        proxyFactory.isInitialized(proxy)
        proxyFactory.unwrap(proxy).is(target)

        where:
        returnsKeys << [true, false]
    }

    void "an association proxy with no results fails on initialization"() {
        given:
        PersistentEntity bookEntity = mappingContext.getPersistentEntity(PxBook.name)
        AssociationQueryExecutor executor = Stub(AssociationQueryExecutor) {
            getIndexedEntity() >> bookEntity
            doesReturnKeys() >> true
            query(_) >> []
        }
        PxBook proxy = proxyFactory.createProxy(session, executor, 5L)

        when:
        proxy.getTitle()

        then:
        DataIntegrityViolationException e = thrown()
        e.message.startsWith('Proxy for [' + PxBook.name)
        e.message.endsWith('] for association [' + PxBook.name + '] could not be initialized')
    }

    void "the proxy factory understands persistent collections and plain objects"() {
        given:
        PersistentCollection collection = Mock(PersistentCollection)
        PxAuthor author = new PxAuthor(name: 'a')

        expect:
        proxyFactory.isProxy(collection)
        !proxyFactory.isProxy(author)
        proxyFactory.getIdentifier(collection) == null
        proxyFactory.isInitialized(author)
        proxyFactory.isInitialized(author, 'book')
        proxyFactory.unwrap(collection).is(collection)

        when:
        proxyFactory.initialize(collection)
        proxyFactory.initialize(author)

        then:
        1 * collection.initialize()

        when:
        boolean initialized = proxyFactory.isInitialized(collection)

        then:
        1 * collection.isInitialized() >> true
        initialized

        when:
        author.book = proxyFactory.createProxy(session, PxBook, 1L)

        then:
        !proxyFactory.isInitialized(author, 'book')
    }

}

@grails.gorm.annotation.Entity
class PxBook {

    Long id
    String title

}

@grails.gorm.annotation.Entity
class PxAuthor {

    Long id
    String name
    PxBook book

}
