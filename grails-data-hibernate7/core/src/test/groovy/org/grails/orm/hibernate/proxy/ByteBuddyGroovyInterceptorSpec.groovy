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
package org.grails.orm.hibernate.proxy

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.proxy.ProxyConfiguration
import spock.lang.Specification
import java.lang.reflect.Method

class ByteBuddyGroovyInterceptorSpec extends Specification {

    def "intercept ignores Groovy internal methods and does not initialize"() {
        given:
        def interceptor = new ByteBuddyGroovyInterceptor(
                "TestEntity",
                Object,
                [] as Class[],
                1L,
                null,
                null,
                null,
                Mock(SharedSessionContractImplementor),
                false
        )
        def proxy = Mock(ProxyConfiguration)
        def getMetaClassMethod = Object.getMethod("getClass") // Placeholder for illustration

        when: "getMetaClass is called (simulated)"
        // In a real scenario, we'd use the actual Groovy method object
        def result = interceptor.intercept(proxy, GroovyObject.getMethod("getMetaClass"), [] as Object[])

        then: "it should not call super.intercept (which would initialize)"
        // We can't easily mock super, but we know it would throw NPE if session/etc are mocks
        // and it tries to initialize.
        noExceptionThrown()
    }

    def "toString returns entity name and id without initialization"() {
        given:
        def interceptor = new ByteBuddyGroovyInterceptor(
                "TestEntity",
                Object,
                [] as Class[],
                1L,
                null,
                null,
                null,
                Mock(SharedSessionContractImplementor),
                false
        )
        def proxy = Mock(ProxyConfiguration)
        def toStringMethod = Object.getMethod("toString")

        when:
        def result = interceptor.intercept(proxy, toStringMethod, [] as Object[])

        then:
        result == "TestEntity:1"
    }
}
