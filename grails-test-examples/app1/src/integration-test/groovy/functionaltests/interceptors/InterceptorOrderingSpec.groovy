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

import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClient

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

    @Autowired HttpClient http

    def setup() {
        // Reset execution order before each test
        http.get('/interceptorTest/resetOrder')
    }

    // ========== Interceptor Ordering Tests ==========

    def "test interceptors execute in order by 'order' property"() {
        when:
        def response = http.get('/interceptorTest/testOrder')

        then: "interceptors should run in order: first(10), second(20), third(30)"
        response.expectStatus(200)
        def order = response.json().executionOrder
        
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
        def response = http.get('/interceptorTest/index')

        then:
        response.expectStatus(200)
        def order = response.json().executionOrder
        
        // All before interceptors should run before controller
        order.findAll { it.contains(':before') }.every { beforeEntry ->
            order.indexOf(beforeEntry) < order.indexOf('controller:index')
        }
    }

    def "test after interceptors run after controller action"() {
        when:
        def response = http.get('/interceptorTest/index')

        then:
        response.expectStatus(200)
        def order = response.json().executionOrder
        
        // All after interceptors should run after controller
        order.findAll { it.contains(':after') }.every { afterEntry ->
            order.indexOf(afterEntry) > order.indexOf('controller:index')
        }
    }

    // ========== Blocking Interceptor Tests ==========

    def "test interceptor can block request by returning false"() {
        when:
        def response = http.get('/interceptorTest/blocked?block=true&reason=testing')

        then: "controller action should not execute"
        response.expectJson(200, [
                blocked: true,
                message: 'Request blocked by interceptor',
                reason: 'testing'
        ])
    }

    def "test interceptor allows request when returning true"() {
        when:
        def response = http.get('/interceptorTest/blocked?block=false')

        then: "controller action should execute"
        response.expectJson(200, [
                action: 'blocked',
                message: 'This should not be seen if blocked'
        ])
    }

    // ========== Request Attribute Tests ==========

    def "test interceptor can set request attributes"() {
        when:
        def response = http.get('/interceptorTest/checkAttributes')

        then:
        response.expectStatus(200)
        with(response.json()) {
            fromBefore == true
            interceptorSet != null
            interceptorSet.source == 'AttributeSettingInterceptor'
        }
    }

    def "test interceptor can set response headers"() {
        when:
        def response = http.get('/interceptorTest/checkAttributes')

        then:
        response.expectHeaders(200, 'X-Interceptor-Header': 'set-by-interceptor')
    }

    // ========== Session Tests ==========

    def "test interceptor can set session attributes"() {
        when:
        def response = http.get('/interceptorTest/checkSession')

        then:
        response.expectStatus(200)
        with(response.json()) {
            sessionData != null
            sessionData.message == 'Session data from interceptor'
        }
    }

    // ========== Conditional Matching Tests ==========

    def "test interceptor conditional matching - matched"() {
        when:
        def response = http.get('/interceptorTest/conditionalAction?match=yes')

        then:
        response.expectStatus(200)

        when:
        def orderResponse = http.get('/interceptorTest/getOrder')

        then:
        orderResponse.json().executionOrder.contains('conditional:before:matched')
    }

    def "test interceptor conditional matching - not matched"() {
        when:
        def response = http.get('/interceptorTest/conditionalAction?match=no')

        then:
        response.expectStatus(200)

        when:
        def orderResponse = http.get('/interceptorTest/getOrder')

        then:
        orderResponse.json().executionOrder.contains('conditional:before:notmatched')
    }

    // ========== Timing Interceptor Tests ==========

    def "test timing interceptor tracks request duration"() {
        when:
        def response = http.get('/interceptorTest/slowAction?delay=50')

        then:
        response.expectJson(200, [
            action: 'slowAction',
            delay: 50
        ])

        // Check that timing interceptor's before phase ran and recorded start time
        // Note: We can only reliably verify before() since after() runs after response is committed
        // and its execution order entry may not be visible due to async timing
        def orderResponse = http.get('/interceptorTest/getOrder')
        orderResponse.json().executionOrder.any { it.startsWith('timing:before') }
    }

    // ========== Multiple Requests Tests ==========

    def "test interceptors work correctly across multiple requests"() {
        when: "make multiple requests"
        def response1 = http.get('/interceptorTest/index')
        
        // Reset again for clean second request
        http.get('/interceptorTest/resetOrder')
        def response2 = http.get('/interceptorTest/index')

        then: "each request should have clean interceptor execution"
        response1.expectStatus(200)
        response2.expectStatus(200)
        
        // Both should have similar execution patterns
        response1.json().executionOrder.contains('first:before')
        response2.json().executionOrder.contains('first:before')
    }

    // ========== Data Action Tests ==========

    def "test interceptors work with data actions"() {
        when:
        def response = http.get('/interceptorTest/dataAction?data=testValue')

        then:
        response.expectJson(200, [
                data: 'testValue',
                interceptorModified: false
        ])
    }

    // ========== Execution Order Verification ==========

    def "test complete before-controller-after sequence"() {
        when:
        def response = http.get('/interceptorTest/index')

        then:
        response.expectStatus(200)
        def order = response.json().executionOrder
        
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
