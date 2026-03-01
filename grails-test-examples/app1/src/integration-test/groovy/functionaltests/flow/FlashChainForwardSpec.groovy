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

import groovy.json.JsonSlurper

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import spock.lang.Shared
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientConfigurationUtils
import org.apache.grails.testing.httpclient.HttpClientSupport

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
class FlashChainForwardSpec extends Specification implements HttpClientSupport {

    @Shared
    HttpClient noRedirectClient

    def setup() {
        if (!noRedirectClient) {
            noRedirectClient = newHttpClient(
                    HttpClientConfigurationUtils.fromSystemProperties().tap {
                        followRedirects = false
                    }
            )
        }
    }
    
    /**
     * Helper to extract session cookie from Set-Cookie header.
     */
    private String extractSessionCookie(String setCookieHeader) {
        if (!setCookieHeader) return null
        setCookieHeader.split(';')[0]
    }

    /**
     * Helper to follow redirect manually with session cookie.
     */
    private Map followRedirectWithSession(String path) {
        // First request - get redirect and session cookie
        def response1 = noRedirectClient.toBlocking().exchange(
            HttpRequest.GET(path),
            String
        )
        
        def sessionCookie = extractSessionCookie(response1.header('Set-Cookie'))
        def location = response1.header('Location')
        
        if (!location) {
            // Not a redirect, parse body
            return new JsonSlurper().parseText(response1.body()) as Map
        }
        
        // Follow redirect with session cookie
        def redirectPath = location.startsWith('http') ? 
            new URL(location).path + (new URL(location).query ? "?${new URL(location).query}" : '') : 
            location
        
        def request2 = HttpRequest.GET(redirectPath)
        if (sessionCookie) {
            request2 = request2.header('Cookie', sessionCookie)
        }
        
        def response2 = httpClient.retrieve(request2)
        new JsonSlurper().parseText(response2) as Map
    }

    /**
     * Helper to follow chain redirects with session cookie (handles multiple redirects).
     */
    private Map followChainWithSession(String path) {
        String sessionCookie = null
        String currentPath = path
        int maxRedirects = 10
        int redirectCount = 0
        
        while (redirectCount < maxRedirects) {
            def request = HttpRequest.GET(currentPath)
            if (sessionCookie) {
                request = request.header('Cookie', sessionCookie)
            }
            
            def response = noRedirectClient.toBlocking().exchange(request, String)
            
            // Update session cookie if new one provided
            def newCookie = extractSessionCookie(response.header('Set-Cookie'))
            if (newCookie) {
                sessionCookie = newCookie
            }
            
            def location = response.header('Location')
            if (!location) {
                // No more redirects, return parsed body
                return new JsonSlurper().parseText(response.body() as String) as Map
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
        def result = followRedirectWithSession('/flow/setFlashAndRedirect')

        then: "flash values are available after redirect"
        result.message == 'This is a flash message'
        result.type == 'success'
    }

    def "test multiple flash values with different types"() {
        when: "setting multiple typed flash values with redirect"
        def result = followRedirectWithSession('/flow/setMultipleFlashValues')

        then: "all types preserved"
        result.stringValue == 'Hello'
        result.intValue == 42
        result.listValue == ['a', 'b', 'c']
        result.mapValue.key == 'value'
        result.mapValue.nested.x == 1
    }

    def "test flash.now for same-request values"() {
        when: "using flash.now"
        def response = httpClient.exchange(
            '/flow/flashNow',
            Map
        )

        then: "both immediate and persisted values available in same request"
        response.status.code == 200
        response.body().immediate == 'This is immediate'
        response.body().persisted == 'This persists'
    }

    def "test flash is cleared after being read"() {
        when: "first request sets flash"
        def response1 = noRedirectClient.toBlocking().exchange(
            '/flow/setFlashOnly?message=TestMessage',
            String
        )
        def sessionCookie = extractSessionCookie(response1.header('Set-Cookie'))

        and: "second request reads flash with same session"
        def request2 = HttpRequest.GET('/flow/readFlash')
        if (sessionCookie) {
            request2 = request2.header('Cookie', sessionCookie)
        }
        def response2 = httpClient.retrieve(request2)
        def json2 = new JsonSlurper().parseText(response2)

        and: "third request tries to read again with same session"
        def request3 = HttpRequest.GET('/flow/readFlash')
        if (sessionCookie) {
            request3 = request3.header('Cookie', sessionCookie)
        }
        def response3 = httpClient.retrieve(request3)
        def json3 = new JsonSlurper().parseText(response3)

        then: "flash available in second request"
        json2.message == 'TestMessage'

        and: "flash cleared in third request"
        json3.message == null
    }

    // ========== Chain Tests ==========
    // Chain uses redirects internally, so we need to handle session cookies

    def "test chain accumulates model across actions"() {
        when: "chaining through three actions with session propagation"
        def result = followChainWithSession('/flow/chainFirst')

        then: "all model values accumulated"
        result.first == 'value1'
        result.second == 'value2'
        result.third == 'value3'
        result.totalSteps == 3
    }

    def "test chain preserves params"() {
        when: "chain with params and session propagation"
        def result = followChainWithSession('/flow/chainWithParams?id=123&name=test')

        then: "both chainModel and params available"
        result.fromChain == true
        result.extraParam == 'extra'
    }

    def "test chain to different controller"() {
        when: "chaining to another controller with session propagation"
        def result = followChainWithSession('/flow/chainToOtherController')

        then: "chain model available in target controller"
        result.controller == 'flowTarget'
        result.source == 'flowController'
    }

    // ========== Forward Tests ==========

    def "test forward keeps same request"() {
        when: "forwarding to another action"
        def response = httpClient.exchange(
            '/flow/forwardToAction',
            Map
        )

        then: "request attributes preserved"
        response.status.code == 200
        response.body().forwardedFrom == 'forwardToAction'
        response.body().sameRequest == true
    }

    def "test forward with params"() {
        when: "forwarding with additional params"
        def response = httpClient.exchange(
            '/flow/forwardWithParams?id=original',
            Map
        )

        then: "both original and forwarded params available"
        response.status.code == 200
        response.body().forwarded == 'yes'
        response.body().value == '123'
    }

    def "test forward to different controller"() {
        when: "forwarding to another controller"
        def response = httpClient.exchange(
            '/flow/forwardToOtherController',
            Map
        )

        then: "forward reaches target controller"
        response.status.code == 200
        response.body().controller == 'flowTarget'
        response.body().sourceController == 'flow'
    }

    // ========== Redirect Tests ==========

    def "test redirect preserves all params"() {
        when: "redirecting with params"
        def response = httpClient.exchange(
            '/flow/redirectWithAllParams?foo=bar&num=42',
            Map
        )

        then: "params preserved after redirect"
        response.status.code == 200
        response.body().params.foo == 'bar'
        response.body().params.num == '42'
    }

    def "test redirect to uri"() {
        when: "redirecting to specific URI"
        def response = httpClient.exchange(
            '/flow/redirectToUri',
            Map
        )

        then: "redirected to correct URI"
        response.status.code == 200
        response.body().fromRedirect == 'true'
    }

    def "test redirect reaches target"() {
        when: "basic redirect"
        def response = httpClient.exchange(
            '/flow/permanentRedirect',
            Map
        )

        then: "reaches target action"
        response.status.code == 200
        response.body().action == 'redirectTarget'
    }

    // ========== Edge Cases ==========

    def "test chain model is empty when not chained"() {
        when: "calling chain target directly"
        def response = httpClient.exchange(
            '/flow/chainThird',
            Map
        )

        then: "chainModel is empty/null"
        response.status.code == 200
        response.body().first == null
        response.body().second == null
        response.body().third == 'value3'
    }
}
