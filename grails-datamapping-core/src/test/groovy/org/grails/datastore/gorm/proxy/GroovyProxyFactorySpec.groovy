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

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.EntityReflector
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Specification

class GroovyProxyFactorySpec extends Specification {

    GroovyProxyFactory proxyFactory = new GroovyProxyFactory()

    void "createProxy returns an uninitialized proxy whose identifier is available without loading"() {
        given:
        Session session = Mock(Session)
        session.getPersister(ProxyFactoryTestDomain) >> null
        session.getMappingContext() >> null

        when:
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 42L)

        then:
        proxyFactory.isProxy(proxy)
        proxyFactory.getIdentifier(proxy) == 42L
        !proxyFactory.isInitialized(proxy)
        0 * session.retrieve(_, _)
    }

    void "createProxy uses the session's persister to set the object identifier when available"() {
        given:
        Session session = Mock(Session)
        EntityPersister persister = Mock(EntityPersister)
        session.getPersister(ProxyFactoryTestDomain) >> persister

        when:
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 99L)

        then:
        1 * persister.setObjectIdentifier(_, 99L)
        proxyFactory.isProxy(proxy)
    }

    void "createProxy falls back to the mapping context's entity reflector when there is no persister"() {
        given:
        Session session = Mock(Session)
        MappingContext mappingContext = Mock(MappingContext)
        PersistentEntity entity = Mock(PersistentEntity)
        EntityReflector reflector = Mock(EntityReflector)
        session.getPersister(ProxyFactoryTestDomain) >> null
        session.getMappingContext() >> mappingContext
        mappingContext.getPersistentEntity(ProxyFactoryTestDomain.name) >> entity
        mappingContext.getEntityReflector(entity) >> reflector

        when:
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 5L)

        then:
        1 * reflector.setIdentifier(_ as ProxyFactoryTestDomain, 5L)
        proxyFactory.isProxy(proxy)
        proxyFactory.getIdentifier(proxy) == 5L
    }

    void "createProxy sets the id property directly when the mapping context does not know the type"() {
        given:
        Session session = Mock(Session)
        MappingContext mappingContext = Mock(MappingContext)
        session.getPersister(ProxyFactoryTestDomain) >> null
        session.getMappingContext() >> mappingContext
        mappingContext.getPersistentEntity(ProxyFactoryTestDomain.name) >> null

        when:
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 6L)

        then: 'the underlying instance carries the identifier, bypassing the proxy metaClass to read it'
        ProxyFactoryTestDomain.getMethod('getId').invoke(proxy) == 6L
        proxyFactory.isProxy(proxy)
        0 * session.retrieve(_, _)
    }

    void "createProxy still produces a proxy when identifier assignment fails"() {
        given:
        Session session = Mock(Session)
        session.getPersister(ProxyFactoryTestDomain) >> null
        session.getMappingContext() >> { throw new IllegalStateException('no mapping context') }

        when:
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 8L)

        then:
        proxyFactory.isProxy(proxy)
        proxyFactory.getIdentifier(proxy) == 8L
    }

    void "a proxy answers identity and proxy-state queries through Groovy dispatch without loading"() {
        given:
        Session session = Mock(Session)
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 42L)

        when:
        Object id = proxy.id
        Object idFromGetter = proxy.getId()
        Object isProxy = proxy.isProxy()
        Object initialized = proxy.initialized
        Object initializedFromGetter = proxy.isInitialized()
        Object metaClass = proxy.metaClass
        Object clazz = proxy.class

        then:
        id == 42L
        idFromGetter == 42L
        isProxy == true
        initialized == false
        initializedFromGetter == false
        metaClass instanceof ProxyInstanceMetaClass
        clazz == ProxyFactoryTestDomain
        0 * session.retrieve(_, _)
    }

    void "reading a regular property through Groovy dispatch loads the target once and reads from it"() {
        given:
        Session session = Mock(Session)
        ProxyFactoryTestDomain target = new ProxyFactoryTestDomain(id: 42L, name: 'loaded')
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 42L)

        when:
        String first = proxy.name
        String second = proxy.name

        then:
        1 * session.retrieve(ProxyFactoryTestDomain, 42L) >> target
        first == 'loaded'
        second == 'loaded'
        proxy.initialized
        proxy.target.is(target)
    }

    void "writing a regular property through Groovy dispatch loads the target and writes to it"() {
        given:
        Session session = Mock(Session)
        ProxyFactoryTestDomain target = new ProxyFactoryTestDomain(id: 42L, name: 'loaded')
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 42L)

        when:
        proxy.name = 'changed'

        then:
        1 * session.retrieve(ProxyFactoryTestDomain, 42L) >> target
        target.name == 'changed'
    }

    void "field access through Groovy dispatch reads the identifier from the proxy and other fields from the target"() {
        given:
        Session session = Mock(Session)
        ProxyFactoryTestDomain target = new ProxyFactoryTestDomain(id: 42L, name: 'loaded')
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 42L)

        when:
        Object id = proxy.@id

        then:
        id == 42L
        0 * session.retrieve(_, _)

        when:
        Object name = proxy.@name

        then:
        1 * session.retrieve(ProxyFactoryTestDomain, 42L) >> target
        name == 'loaded'
    }

    void "field assignment through Groovy dispatch loads the target and writes to it"() {
        given:
        Session session = Mock(Session)
        ProxyFactoryTestDomain target = new ProxyFactoryTestDomain(id: 42L, name: 'loaded')
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 42L)

        when:
        proxy.@name = 'changed'

        then:
        1 * session.retrieve(ProxyFactoryTestDomain, 42L) >> target
        target.name == 'changed'
    }

    void "invoking a domain method through Groovy dispatch runs it against the loaded target"() {
        given:
        Session session = Mock(Session)
        ProxyFactoryTestDomain target = new ProxyFactoryTestDomain(id: 42L, name: 'loaded')
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 42L)

        when:
        String description = proxy.describe()

        then:
        1 * session.retrieve(ProxyFactoryTestDomain, 42L) >> target
        description == 'loaded!'
        proxy.isInitialized()
    }

    void "assigning the metaClass of a proxy does not load the target"() {
        given:
        Session session = Mock(Session)
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 42L)

        when:
        proxy.metaClass = null

        then:
        proxy.metaClass != null
        0 * session.retrieve(_, _)
    }

    void "loading a proxy whose target no longer exists throws DataIntegrityViolationException"() {
        given:
        Session session = Mock(Session)
        session.retrieve(ProxyFactoryTestDomain, 42L) >> null
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 42L)

        when:
        proxy.name

        then:
        DataIntegrityViolationException e = thrown()
        e.message.contains('42')
        e.message.contains(ProxyFactoryTestDomain.name)
        !proxyFactory.isInitialized(proxy)
    }

    void "unwrap loads and returns the target for a proxy, caching it as initialized"() {
        given:
        Session session = Mock(Session)
        session.getPersister(ProxyFactoryTestDomain) >> null
        ProxyFactoryTestDomain target = new ProxyFactoryTestDomain(id: 7L, name: 'loaded')
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 7L)

        when:
        Object result = proxyFactory.unwrap(proxy)

        then:
        1 * session.retrieve(ProxyFactoryTestDomain, 7L) >> target
        result.is(target)

        and: 'the proxy is now considered initialized without a second retrieve'
        proxyFactory.isInitialized(proxy)
        0 * session.retrieve(_, _)
    }

    void "unwrap returns the object unchanged when it is not a proxy"() {
        given:
        ProxyFactoryTestDomain plain = new ProxyFactoryTestDomain(id: 1L)

        expect:
        proxyFactory.unwrap(plain).is(plain)
        !proxyFactory.isProxy(plain)
        proxyFactory.isInitialized(plain)
    }

    void "null is never a proxy and is treated as initialized"() {
        expect:
        !proxyFactory.isProxy(null)
        proxyFactory.isInitialized(null)
        proxyFactory.unwrap(null) == null
    }

    void "getIdentifier falls back to invoking getId() on a non-proxied object"() {
        given:
        ProxyFactoryTestDomain plain = new ProxyFactoryTestDomain(id: 5L)

        expect:
        proxyFactory.getIdentifier(plain) == 5L
    }

    void "getIdentifier returns null for a non-proxied object without a getId() method"() {
        expect:
        proxyFactory.getIdentifier(new Object()) == null
    }

    void "getProxiedClass returns the runtime class regardless of proxy state"() {
        given:
        ProxyFactoryTestDomain plain = new ProxyFactoryTestDomain(id: 1L)
        Session session = Mock(Session)
        session.getPersister(ProxyFactoryTestDomain) >> null
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 2L)

        expect:
        proxyFactory.getProxiedClass(plain) == ProxyFactoryTestDomain
        proxyFactory.getProxiedClass(proxy) == ProxyFactoryTestDomain
    }

    void "initialize eagerly resolves the proxy target"() {
        given:
        Session session = Mock(Session)
        session.getPersister(ProxyFactoryTestDomain) >> null
        ProxyFactoryTestDomain target = new ProxyFactoryTestDomain(id: 3L)
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 3L)

        when:
        proxyFactory.initialize(proxy)

        then:
        1 * session.retrieve(ProxyFactoryTestDomain, 3L) >> target
        proxyFactory.isInitialized(proxy)
    }

    void "association proxies are not supported"() {
        given:
        Session session = Mock(Session)
        AssociationQueryExecutor executor = Mock(AssociationQueryExecutor)

        when:
        proxyFactory.createProxy(session, executor, 1L)

        then:
        thrown(UnsupportedOperationException)
    }

    void "isInitialized(object, associationName) treats a null association as initialized"() {
        given:
        ProxyFactoryTestOwner owner = new ProxyFactoryTestOwner(id: 1L, domain: null)

        expect:
        proxyFactory.isInitialized(owner, 'domain')
    }

    void "isInitialized(object, associationName) treats a non-proxy association as initialized"() {
        given:
        ProxyFactoryTestOwner owner = new ProxyFactoryTestOwner(id: 1L, domain: new ProxyFactoryTestDomain(id: 2L))

        expect:
        proxyFactory.isInitialized(owner, 'domain')
    }

    void "isInitialized(object, associationName) reflects the state of a proxied association"() {
        given:
        Session session = Mock(Session)
        ProxyFactoryTestDomain proxy = proxyFactory.createProxy(session, ProxyFactoryTestDomain, 42L)
        ProxyFactoryTestOwner owner = new ProxyFactoryTestOwner(id: 1L, domain: proxy)

        expect:
        !proxyFactory.isInitialized(owner, 'domain')

        when:
        proxyFactory.initialize(proxy)

        then:
        1 * session.retrieve(ProxyFactoryTestDomain, 42L) >> new ProxyFactoryTestDomain(id: 42L)
        proxyFactory.isInitialized(owner, 'domain')
    }
}

class ProxyFactoryTestDomain {
    Long id
    String name

    String describe() {
        "${name}!"
    }
}

class ProxyFactoryTestOwner {
    Long id
    ProxyFactoryTestDomain domain
}
