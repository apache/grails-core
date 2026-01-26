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

package functionaltests.interceptors

import functionaltests.Application
import grails.testing.mixin.integration.Integration
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import spock.lang.Specification

/**
 * Comprehensive integration tests for Grails interceptor functionality.
 * 
 * Tests cover:
 * - Interceptor execution order (before/after/afterView)
 * - Interceptor ordering using 'order' property
 * - Blocking requests in before()
 * - Request/response attribute manipulation
 * - Session manipulation
 * - Conditional matching
 * - Timing/performance tracking
 */
@Integration(applicationClass = Application)
class InterceptorOrderingSpec extends Specification {

    private HttpClient createClient() {
        HttpClient.create(new URL("http://localhost:$serverPort"))
    }

    def setup() {
        // Reset execution order before each test
        def client = createClient()
        try {
            client.toBlocking().exchange(
                HttpRequest.GET('/interceptorTest/resetOrder'),
                Map
            )
        } finally {
            client.close()
        }
    }

    // ========== Interceptor Ordering Tests ==========

    def "test interceptors execute in order by 'order' property"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/testOrder'),
            Map
        )

        then: "interceptors should run in order: first(10), second(20), third(30)"
        response.status.code == 200
        def order = response.body().executionOrder
        
        // Before interceptors run in ascending order
        def firstBeforeIdx = order.indexOf('first:before')
        def secondBeforeIdx = order.indexOf('second:before')
        def thirdBeforeIdx = order.indexOf('third:before')
        def controllerIdx = order.indexOf('controller:testOrder')
        
        // Verify before interceptors run before controller
        firstBeforeIdx >= 0
        secondBeforeIdx >= 0
        thirdBeforeIdx >= 0
        controllerIdx >= 0
        
        firstBeforeIdx < secondBeforeIdx
        secondBeforeIdx < thirdBeforeIdx
        thirdBeforeIdx < controllerIdx

        cleanup:
        client.close()
    }

    def "test before interceptors run before controller action"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/index'),
            Map
        )

        then:
        response.status.code == 200
        def order = response.body().executionOrder
        
        // All before interceptors should run before controller
        order.findAll { it.contains(':before') }.every { beforeEntry ->
            order.indexOf(beforeEntry) < order.indexOf('controller:index')
        }

        cleanup:
        client.close()
    }

    def "test after interceptors run after controller action"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/index'),
            Map
        )

        then:
        response.status.code == 200
        def order = response.body().executionOrder
        
        // All after interceptors should run after controller
        order.findAll { it.contains(':after') }.every { afterEntry ->
            order.indexOf(afterEntry) > order.indexOf('controller:index')
        }

        cleanup:
        client.close()
    }

    // ========== Blocking Interceptor Tests ==========

    def "test interceptor can block request by returning false"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/blocked?block=true&reason=testing'),
            Map
        )

        then: "controller action should not execute"
        response.status.code == 200
        response.body().blocked == true
        response.body().message == 'Request blocked by interceptor'
        response.body().reason == 'testing'

        cleanup:
        client.close()
    }

    def "test interceptor allows request when returning true"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/blocked?block=false'),
            Map
        )

        then: "controller action should execute"
        response.status.code == 200
        response.body().action == 'blocked'
        response.body().message == 'This should not be seen if blocked'

        cleanup:
        client.close()
    }

    // ========== Request Attribute Tests ==========

    def "test interceptor can set request attributes"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/checkAttributes'),
            Map
        )

        then:
        response.status.code == 200
        response.body().fromBefore == true
        response.body().interceptorSet != null
        response.body().interceptorSet.source == 'AttributeSettingInterceptor'

        cleanup:
        client.close()
    }

    def "test interceptor can set response headers"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/checkAttributes'),
            Map
        )

        then:
        response.status.code == 200
        response.header('X-Interceptor-Header') == 'set-by-interceptor'

        cleanup:
        client.close()
    }

    // ========== Session Tests ==========

    def "test interceptor can set session attributes"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/checkSession'),
            Map
        )

        then:
        response.status.code == 200
        response.body().sessionData != null
        response.body().sessionData.message == 'Session data from interceptor'

        cleanup:
        client.close()
    }

    // ========== Conditional Matching Tests ==========

    def "test interceptor conditional matching - matched"() {
        given:
        def client = createClient()
        // Reset first
        client.toBlocking().exchange(HttpRequest.GET('/interceptorTest/resetOrder'), Map)

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/conditionalAction?match=yes'),
            Map
        )

        then:
        response.status.code == 200
        def orderResponse = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/getOrder'),
            Map
        )
        orderResponse.body().executionOrder.contains('conditional:before:matched')

        cleanup:
        client.close()
    }

    def "test interceptor conditional matching - not matched"() {
        given:
        def client = createClient()
        // Reset first
        client.toBlocking().exchange(HttpRequest.GET('/interceptorTest/resetOrder'), Map)

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/conditionalAction?match=no'),
            Map
        )

        then:
        response.status.code == 200
        def orderResponse = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/getOrder'),
            Map
        )
        orderResponse.body().executionOrder.contains('conditional:before:notmatched')

        cleanup:
        client.close()
    }

    // ========== Timing Interceptor Tests ==========

    def "test timing interceptor tracks request duration"() {
        given:
        def client = createClient()
        // Reset first
        client.toBlocking().exchange(HttpRequest.GET('/interceptorTest/resetOrder'), Map)

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/slowAction?delay=50'),
            Map
        )

        then:
        response.status.code == 200
        response.body().action == 'slowAction'
        response.body().delay == 50
        
        // Check execution order includes timing entries
        def orderResponse = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/getOrder'),
            Map
        )
        orderResponse.body().executionOrder.any { it.startsWith('timing:before') }
        orderResponse.body().executionOrder.any { it.startsWith('timing:after') }

        cleanup:
        client.close()
    }

    // ========== Multiple Requests Tests ==========

    def "test interceptors work correctly across multiple requests"() {
        given:
        def client = createClient()

        when: "make multiple requests"
        client.toBlocking().exchange(HttpRequest.GET('/interceptorTest/resetOrder'), Map)
        def response1 = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/index'),
            Map
        )
        
        // Reset again for clean second request
        client.toBlocking().exchange(HttpRequest.GET('/interceptorTest/resetOrder'), Map)
        def response2 = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/index'),
            Map
        )

        then: "each request should have clean interceptor execution"
        response1.status.code == 200
        response2.status.code == 200
        
        // Both should have similar execution patterns
        response1.body().executionOrder.contains('first:before')
        response2.body().executionOrder.contains('first:before')

        cleanup:
        client.close()
    }

    // ========== Data Action Tests ==========

    def "test interceptors work with data actions"() {
        given:
        def client = createClient()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/dataAction?data=testValue'),
            Map
        )

        then:
        response.status.code == 200
        response.body().data == 'testValue'

        cleanup:
        client.close()
    }

    // ========== Execution Order Verification ==========

    def "test complete before-controller-after sequence"() {
        given:
        def client = createClient()
        client.toBlocking().exchange(HttpRequest.GET('/interceptorTest/resetOrder'), Map)

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/index'),
            Map
        )

        then:
        response.status.code == 200
        def order = response.body().executionOrder
        
        // Verify the complete sequence
        def beforeEntries = order.findAll { it.contains(':before') }
        def afterEntries = order.findAll { it.contains(':after') }
        def controllerEntry = order.find { it.startsWith('controller:') }
        
        // All befores come first
        beforeEntries.every { order.indexOf(it) < order.indexOf(controllerEntry) }
        // All afters come last
        afterEntries.every { order.indexOf(it) > order.indexOf(controllerEntry) }

        cleanup:
        client.close()
    }
}
