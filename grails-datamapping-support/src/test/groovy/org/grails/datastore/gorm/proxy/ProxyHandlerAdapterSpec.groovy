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

import org.grails.datastore.mapping.proxy.ProxyHandler
import spock.lang.Specification

class ProxyHandlerAdapterSpec extends Specification {

    def delegate = Mock(ProxyHandler)
    def adapter = new ProxyHandlerAdapter(delegate)

    void "getProxyIdentifier delegates to getIdentifier on the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        def result = adapter.getProxyIdentifier(target)

        then:
        1 * delegate.getIdentifier(target) >> 42L
        result == 42L
    }

    void "getProxiedClass delegates to the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        def result = adapter.getProxiedClass(target)

        then:
        1 * delegate.getProxiedClass(target) >> String
        result == String
    }

    void "isProxy delegates to the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        def result = adapter.isProxy(target)

        then:
        1 * delegate.isProxy(target) >> true
        result
    }

    void "unwrapIfProxy delegates to unwrap on the wrapped proxy handler"() {
        given:
        def proxy = new Object()
        def unwrapped = new Object()

        when:
        def result = adapter.unwrapIfProxy(proxy)

        then:
        1 * delegate.unwrap(proxy) >> unwrapped
        result.is(unwrapped)
    }

    void "isInitialized delegates to the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        def result = adapter.isInitialized(target)

        then:
        1 * delegate.isInitialized(target) >> true
        result
    }

    void "initialize delegates to the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        adapter.initialize(target)

        then:
        1 * delegate.initialize(target)
    }

    void "isInitialized with an association name delegates to the wrapped proxy handler"() {
        given:
        def target = new Object()

        when:
        def result = adapter.isInitialized(target, "author")

        then:
        1 * delegate.isInitialized(target, "author") >> false
        !result
    }
}
