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

import spock.lang.Specification

import grails.core.support.proxy.EntityProxyHandler
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.proxy.ProxyHandler

class ProxyAdaptersSpec extends Specification {

    void "the entity proxy handler adapter delegates every proxy query to the grails handler"() {
        given:
        EntityProxyHandler handler = Mock(EntityProxyHandler)
        EntityProxyHandlerAdapter adapter = new EntityProxyHandlerAdapter(handler)
        Object target = new Object()

        when:
        boolean proxy = adapter.isProxy(target)
        boolean initialized = adapter.isInitialized(target)
        boolean associationInitialized = adapter.isInitialized(target, 'owner')
        Object unwrapped = adapter.unwrap(target)
        Serializable identifier = adapter.getIdentifier(target)
        Class proxied = adapter.getProxiedClass(target)
        adapter.initialize(target)

        then:
        1 * handler.isProxy(target) >> true
        1 * handler.isInitialized(target) >> false
        1 * handler.isInitialized(target, 'owner') >> true
        1 * handler.unwrapIfProxy(target) >> 'unwrapped'
        1 * handler.getProxyIdentifier(target) >> 7L
        1 * handler.getProxiedClass(target) >> String
        1 * handler.initialize(target)
        proxy
        !initialized
        associationInitialized
        unwrapped == 'unwrapped'
        identifier == 7L
        proxied == String
    }

    void "the entity proxy handler adapter cannot create proxies"() {
        given:
        EntityProxyHandlerAdapter adapter = new EntityProxyHandlerAdapter(Stub(EntityProxyHandler))

        when:
        adapter.createProxy(Stub(Session), String, 1L)

        then:
        UnsupportedOperationException e = thrown()
        e.message == 'Method createProxy is not supported by this implementation'

        when:
        adapter.createProxy(Stub(Session), Stub(AssociationQueryExecutor), 1L)

        then:
        e = thrown()
        e.message == 'Method createProxy is not supported by this implementation'
    }

    void "the proxy handler adapter exposes a datastore proxy handler as a grails entity proxy handler"() {
        given:
        ProxyHandler delegate = Mock(ProxyHandler)
        ProxyHandlerAdapter adapter = new ProxyHandlerAdapter(delegate)
        Object target = new Object()

        when:
        Object identifier = adapter.getProxyIdentifier(target)
        Class proxied = adapter.getProxiedClass(target)
        boolean proxy = adapter.isProxy(target)
        Object unwrapped = adapter.unwrapIfProxy(target)
        boolean initialized = adapter.isInitialized(target)
        boolean associationInitialized = adapter.isInitialized(target, 'owner')
        adapter.initialize(target)

        then:
        1 * delegate.getIdentifier(target) >> 3L
        1 * delegate.getProxiedClass(target) >> Integer
        1 * delegate.isProxy(target) >> false
        1 * delegate.unwrap(target) >> target
        1 * delegate.isInitialized(target) >> true
        1 * delegate.isInitialized(target, 'owner') >> false
        1 * delegate.initialize(target)
        identifier == 3L
        proxied == Integer
        !proxy
        unwrapped.is(target)
        initialized
        !associationInitialized
        adapter instanceof EntityProxyHandler
    }

}
