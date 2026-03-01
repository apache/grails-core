/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.httpclient

import io.micronaut.http.client.BlockingHttpClient
import spock.lang.Specification

class HttpClientSupportSpec extends Specification {

    void 'getHttpClientURL throws if no port has been assigned'() {
        given:
        def support = new TestSupport(assignedPort: -1)

        when:
        support.httpClient

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('No server port assigned')
    }

    void 'shared async client is reused and then recreated after cleanupClient'() {
        given:
        def support = new TestSupport(assignedPort: 0)

        when: 'first access creates the shared async client'
        def c1 = support.asyncClient
        def c2 = support.asyncClient

        then: 'the shared client is reused'
        c1.is(c2)

        when: 'cleanup runs'
        support.cleanupClients()

        and: 'access again'
        def c3 = support.asyncClient

        then: 'a new shared client is created'
        !c3.is(c1)

        cleanup:
        support.cleanupClients()
    }

    void 'cleanupClient closes custom clients created via newHttpClient'() {
        given:
        def support = new TestSupport(assignedPort: 0)

        and: 'we create custom clients and observe close()'
        def closeCount = 0
        def c1 = support.newHttpClient()
        def c2 = support.newHttpClient()

        [c1, c2].each {
            it.metaClass.close = { -> closeCount++ }
        }

        when:
        support.cleanupClients()

        then:
        closeCount == 2
    }

    void 'httpClient returns a BlockingHttpClient wrapper'() {
        given:
        def support = new TestSupport(assignedPort: 0)

        expect:
        support.httpClient instanceof BlockingHttpClient
    }

    private static class TestSupport implements HttpClientSupport {}
}
