/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.proxy

import spock.lang.Specification
import spock.lang.Unroll
import org.grails.orm.hibernate.proxy.GroovyProxyInterceptorLogic.InterceptorState
import org.grails.datastore.gorm.proxy.ProxyInstanceMetaClass

class GroovyProxyInterceptorLogicSpec extends Specification {

    static class TestGroovyObject implements GroovyObject {

        MetaClass metaClass
        Object invokeMethod(String name, Object args) { null }
        Object getProperty(String name) { null }
        void setProperty(String name, Object value) {}
    }

    def 'handleUninitialized handles Groovy metadata methods'() {
        given:
        def state = new InterceptorState('TestEntity', String, 123L)

        when:
        def result = GroovyProxyInterceptorLogic.handleUninitialized(state, methodName, [] as Object[])

        then:
        result != GroovyProxyInterceptorLogic.INVOKE_IMPLEMENTATION
        result != null

        where:
        methodName << ['getMetaClass', 'getStaticMetaClass']
    }

    def 'handleUninitialized handles identifier access'() {
        given:
        def state = new InterceptorState('TestEntity', Object, 123L)

        expect:
        GroovyProxyInterceptorLogic.handleUninitialized(state, methodName, args) == 123L

        where:
        methodName     | args
        'getProperty'  | ['id'] as Object[]
        'ident'        | [] as Object[]
    }

    def 'handleUninitialized handles toString'() {
        given:
        def state = new InterceptorState('Book', Object, 1L)

        expect:
        GroovyProxyInterceptorLogic.handleUninitialized(state, 'toString', [] as Object[]) == 'Book:1'
    }

    def 'handleUninitialized handles dirty checking methods'() {
        given:
        def state = new InterceptorState('TestEntity', Object, 1L)

        expect:
        GroovyProxyInterceptorLogic.handleUninitialized(state, methodName, [] as Object[]) == false

        where:
        methodName << ['isDirty', 'hasChanged']
    }

    @Unroll
    def 'isGroovyMethod identifies #methodName as #expected'() {
        expect:
        GroovyProxyInterceptorLogic.isGroovyMethod(methodName) == expected

        where:
        methodName      | expected
        'getMetaClass'  | true
        'setMetaClass'  | true
        'getProperty'   | true
        'setProperty'   | true
        'invokeMethod'  | true
        'getTitle'      | false
        'save'          | false
    }

    def 'unwrap handles ProxyInstanceMetaClass'() {
        given:
        def target = 'real value'
        def proxyMc = Mock(ProxyInstanceMetaClass) {
            getProxyTarget() >> target
        }
        def proxy = new TestGroovyObject(metaClass: proxyMc)

        expect:
        GroovyProxyInterceptorLogic.unwrap(proxy) == target
        GroovyProxyInterceptorLogic.unwrap(new Object()) == null
    }

    def 'getIdentifier handles ProxyInstanceMetaClass'() {
        given:
        def id = 456L
        def proxyMc = Mock(ProxyInstanceMetaClass) {
            getKey() >> id
        }
        def proxy = new TestGroovyObject(metaClass: proxyMc)

        expect:
        GroovyProxyInterceptorLogic.getIdentifier(proxy) == id
        GroovyProxyInterceptorLogic.getIdentifier(new Object()) == null
    }
}
