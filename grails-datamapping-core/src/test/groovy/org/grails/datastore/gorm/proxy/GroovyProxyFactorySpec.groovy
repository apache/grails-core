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
import spock.lang.Specification

class GroovyProxyFactorySpec extends Specification {

    GroovyProxyFactory proxyFactory = new GroovyProxyFactory()

    void "createProxy returns an initialized-looking instance whose identifier is available without loading"() {
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

    void "getIdentifier falls back to invoking getId() on a non-proxied object"() {
        given:
        ProxyFactoryTestDomain plain = new ProxyFactoryTestDomain(id: 5L)

        expect:
        proxyFactory.getIdentifier(plain) == 5L
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
        ProxyFactoryTestDomain owner = new ProxyFactoryTestDomain(id: 1L, name: null)

        expect:
        proxyFactory.isInitialized(owner, 'name')
    }
}

class ProxyFactoryTestDomain {
    Long id
    String name
}
