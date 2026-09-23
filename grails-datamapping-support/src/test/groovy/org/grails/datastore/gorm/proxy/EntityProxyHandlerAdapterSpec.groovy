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

import grails.core.support.proxy.EntityProxyHandler
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import spock.lang.Specification

class EntityProxyHandlerAdapterSpec extends Specification {

    def proxyHandler = Mock(EntityProxyHandler)
    def adapter = new EntityProxyHandlerAdapter(proxyHandler)

    void "isProxy delegates to the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        def result = adapter.isProxy(target)

        then:
        1 * proxyHandler.isProxy(target) >> true
        result
    }

    void "isInitialized delegates to the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        def result = adapter.isInitialized(target)

        then:
        1 * proxyHandler.isInitialized(target) >> true
        result
    }

    void "isInitialized with an association name delegates to the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        def result = adapter.isInitialized(target, "author")

        then:
        1 * proxyHandler.isInitialized(target, "author") >> false
        !result
    }

    void "unwrap delegates to unwrapIfProxy on the wrapped proxy handler"() {
        given:
        def proxy = new Object()
        def unwrapped = new Object()

        when:
        def result = adapter.unwrap(proxy)

        then:
        1 * proxyHandler.unwrapIfProxy(proxy) >> unwrapped
        result.is(unwrapped)
    }

    void "getIdentifier delegates to getProxyIdentifier on the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        def result = adapter.getIdentifier(target)

        then:
        1 * proxyHandler.getProxyIdentifier(target) >> 42L
        result == 42L
    }

    void "getProxiedClass delegates to the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        def result = adapter.getProxiedClass(target)

        then:
        1 * proxyHandler.getProxiedClass(target) >> String
        result == String
    }

    void "initialize delegates to the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        adapter.initialize(target)

        then:
        1 * proxyHandler.initialize(target)
    }

    void "createProxy with a session, type and key is not supported"() {
        when:
        adapter.createProxy(null, String, "id")

        then:
        thrown(UnsupportedOperationException)
    }

    void "createProxy with a session, association query executor and key is not supported"() {
        when:
        adapter.createProxy(null, (AssociationQueryExecutor) null, "id")

        then:
        thrown(UnsupportedOperationException)
    }
}
