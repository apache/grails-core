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
package functionaltests.flow

import java.net.http.HttpClient

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * Integration tests for controller flow features:
 * - Flash scope retention and behavior
 * - chain() for model accumulation across actions
 * - forward() for same-request dispatch
 * - redirect() variations
 *
 * Uses manual redirect handling with session cookie propagation to properly test
 * flash scope and chain model which rely on HTTP session state.
 */
@Integration
@Tag('http-client')
class FlashChainForwardSpec extends Specification implements HttpClientSupport {

    @Shared
    HttpClient noRedirectClient

    void setup() {
        noRedirectClient = noRedirectClient ?: newHttpClientWith {
            followRedirects(HttpClient.Redirect.NEVER)
        }
    }

    /**
     * Helper to follow redirect manually with session cookie.
     */
    private TestHttpResponse followRedirectWithSession(String path) {
        // First request - get redirect and session cookie
        def response1 = http(path, noRedirectClient)

        def sessionCookie = response1.headerValue('Set-Cookie')?.split(';')?.first()
        def location = response1.headerValue('Location')
        if (!location) {
            // Not a redirect, parse body
            return response1
        }

        // Follow redirect with session cookie
        def redirectPath = location.startsWith('http') ?
                new URL(location).path + (new URL(location).query ? "?${new URL(location).query}" : '') :
                location

        def headers = [:] as Map<String, String>
        if (sessionCookie) {
            headers.put('Cookie', sessionCookie)
        }

        http(headers, redirectPath)
    }

    /**
     * Helper to follow chain redirects with session cookie (handles multiple redirects).
     */
    private TestHttpResponse followChainWithSession(String path) {
        String sessionCookie = null
        String currentPath = path
        int maxRedirects = 10
        int redirectCount = 0

        while (redirectCount < maxRedirects) {
            //def request = HttpRequest.GET(currentPath)
            def headers = [:] as Map<String, String>
            if (sessionCookie) {
                headers.put('Cookie', sessionCookie)
            }

            def response = http(headers, currentPath, noRedirectClient)

            // Update session cookie if new one provided
            def newCookie = response.headerValue('Set-Cookie')
            if (newCookie) {
                sessionCookie = newCookie
            }

            def location = response.headerValue('Location')
            if (!location) {
                // No more redirects, return parsed body
                return response
            }

            // Follow to next location
            currentPath = location.startsWith('http') ?
                new URL(location).path + (new URL(location).query ? "?${new URL(location).query}" : '') :
                location
            redirectCount++
        }

        throw new RuntimeException('Too many redirects following chain')
    }

    // ========== Flash Scope Tests ==========

    def "test flash message survives redirect"() {
        when: "setting flash and redirecting with session cookie propagation"
        def response = followRedirectWithSession('/flow/setFlashAndRedirect')

        then: "flash values are available after redirect"
        response.assertJsonContains([
                message: 'This is a flash message',
                type   : 'success'
        ])
    }

    def "test multiple flash values with different types"() {
        when: "setting multiple typed flash values with redirect"
        def response = followRedirectWithSession('/flow/setMultipleFlashValues')

        then: "all types preserved"
        with(response.json()) {
            stringValue == 'Hello'
            intValue == 42
            listValue == ['a', 'b', 'c']
            mapValue.key == 'value'
            mapValue.nested.x == 1
        }
    }

    def "test flash.now for same-request values"() {
        when: "using flash.now"
        def response = http('/flow/flashNow')

        then: "both immediate and persisted values available in same request"
        response.assertJson(200, [
                immediate: 'This is immediate',
                persisted: 'This persists'
        ])
    }

    def "test flash is cleared after being read"() {
        when: "first request sets flash"
        def response1 = http('/flow/setFlashOnly?message=TestMessage', noRedirectClient)
        def sessionCookie = response1.headerValue('Set-Cookie')

        and: "second request reads flash with same session"
        def headers2 = [:] as Map<String, String>
        if (sessionCookie) {
            headers2.put('Cookie', sessionCookie)
        }
        def json2 = http(headers2, '/flow/readFlash').json()

        and: "third request tries to read again with same session"
        def headers3 = [:] as Map<String, String>
        if (sessionCookie) {
            headers3.put('Cookie', sessionCookie)
        }
        def json3 = http(headers3, '/flow/readFlash').json()

        then: "flash available in second request"
        json2.message == 'TestMessage'

        and: "flash cleared in third request"
        json3.message == null
    }

    // ========== Chain Tests ==========
    // Chain uses redirects internally, so we need to handle session cookies

    def "test chain accumulates model across actions"() {
        when: "chaining through three actions with session propagation"
        def response = followChainWithSession('/flow/chainFirst')

        then: "all model values accumulated"
        response.assertJsonContains([
                first     : 'value1',
                second    : 'value2',
                third     : 'value3',
                totalSteps: 3
        ])
    }

    def "test chain preserves params"() {
        when: "chain with params and session propagation"
        def response = followChainWithSession('/flow/chainWithParams?id=123&name=test')

        then: "both chainModel and params available"
        response.assertJsonContains([
                fromChain : true,
                extraParam: 'extra'
        ])
    }

    def "test chain to different controller"() {
        when: "chaining to another controller with session propagation"
        def response = followChainWithSession('/flow/chainToOtherController')

        then: "chain model available in target controller"
        response.assertJsonContains([
                controller: 'flowTarget',
                source    : 'flowController'
        ])
    }

    // ========== Forward Tests ==========

    def "test forward keeps same request"() {
        when: "forwarding to another action"
        def response = http('/flow/forwardToAction')

        then: "request attributes preserved"
        response.assertJsonContains(200, [
                forwardedFrom: 'forwardToAction',
                sameRequest  : true
        ])
    }

    def "test forward with params"() {
        when: "forwarding with additional params"
        def response = http('/flow/forwardWithParams?id=original')

        then: "both original and forwarded params available"
        response.assertJsonContains(200, [
                forwarded: 'yes',
                value    : '123'
        ])
    }

    def "test forward to different controller"() {
        when: "forwarding to another controller"
        def response = http('/flow/forwardToOtherController')

        then: "forward reaches target controller"
        response.assertJsonContains(200, [
                controller      : 'flowTarget',
                sourceController: 'flow'
        ])
    }

    // ========== Redirect Tests ==========

    def "test redirect preserves all params"() {
        when: "redirecting with params"
        def response = http('/flow/redirectWithAllParams?foo=bar&num=42')

        then: "params preserved after redirect"
        response.assertJson(200, [
                params: [
                        foo: 'bar',
                        num: '42'
                ]
        ])
    }

    def "test redirect to uri"() {
        when: "redirecting to specific URI"
        def response = http('/flow/redirectToUri')

        then: "redirected to correct URI"
        response.assertJsonContains(200, [
                fromRedirect: 'true'
        ])
    }

    def "test redirect reaches target"() {
        when: "basic redirect"
        def response = http('/flow/permanentRedirect')

        then: "reaches target action"
        response.assertJson(200, [
                action: 'redirectTarget'
        ])
    }

    // ========== Edge Cases ==========

    def "test chain model is empty when not chained"() {
        when: "calling chain target directly"
        def response = http('/flow/chainThird')

        then: "chainModel is empty/null"
        response.assertStatus(200)
        with(response.json()) {
            first == null
            second == null
            third == 'value3'
        }
    }
}
