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

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import spock.lang.Shared
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

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
@Integration
class InterceptorOrderingSpec extends Specification {

    @Shared
    HttpClient client

    def setup() {
        client = client ?: HttpClient.create(new URL("http://localhost:$serverPort"))
        // Reset execution order before each test
        client.toBlocking().exchange(
                HttpRequest.GET('/interceptorTest/resetOrder'),
                Map
        )
    }

    def cleanupSpec() {
        client.close()
    }

    // ========== Interceptor Ordering Tests ==========

    def "test interceptors execute in order by 'order' property"() {
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
    }

    def "test before interceptors run before controller action"() {
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
    }

    def "test after interceptors run after controller action"() {
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
    }

    // ========== Blocking Interceptor Tests ==========

    def "test interceptor can block request by returning false"() {
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
    }

    def "test interceptor allows request when returning true"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/blocked?block=false'),
            Map
        )

        then: "controller action should execute"
        response.status.code == 200
        response.body().action == 'blocked'
        response.body().message == 'This should not be seen if blocked'
    }

    // ========== Request Attribute Tests ==========

    def "test interceptor can set request attributes"() {
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
    }

    def "test interceptor can set response headers"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/checkAttributes'),
            Map
        )

        then:
        response.status.code == 200
        response.header('X-Interceptor-Header') == 'set-by-interceptor'
    }

    // ========== Session Tests ==========

    def "test interceptor can set session attributes"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/checkSession'),
            Map
        )

        then:
        response.status.code == 200
        response.body().sessionData != null
        response.body().sessionData.message == 'Session data from interceptor'
    }

    // ========== Conditional Matching Tests ==========

    def "test interceptor conditional matching - matched"() {
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
    }

    def "test interceptor conditional matching - not matched"() {
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
    }

    // ========== Timing Interceptor Tests ==========

    def "test timing interceptor tracks request duration"() {
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
    }

    // ========== Multiple Requests Tests ==========

    def "test interceptors work correctly across multiple requests"() {
        when: "make multiple requests"
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
    }

    // ========== Data Action Tests ==========

    def "test interceptors work with data actions"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/interceptorTest/dataAction?data=testValue'),
            Map
        )

        then:
        response.status.code == 200
        response.body().data == 'testValue'
    }

    // ========== Execution Order Verification ==========

    def "test complete before-controller-after sequence"() {
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
    }
}
