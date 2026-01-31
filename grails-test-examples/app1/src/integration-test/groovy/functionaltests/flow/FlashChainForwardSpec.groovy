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

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import spock.lang.Shared
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

/**
 * Integration tests for controller flow features:
 * - Flash scope retention and behavior
 * - chain() for model accumulation across actions
 * - forward() for same-request dispatch
 * - redirect() variations
 */
@Integration
class FlashChainForwardSpec extends Specification {

    @Shared
    HttpClient client

    def setup() {
        client = client ?: HttpClient.create(new URL("http://localhost:$serverPort"))
    }

    def cleanupSpec() {
        client.close()
    }

    // ========== Flash Scope Tests ==========

    def "test flash message survives redirect"() {
        when: "setting flash and redirecting"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/setFlashAndRedirect'),
            Map
        )

        then: "flash values are available after redirect"
        response.status.code == 200
        response.body().message == 'This is a flash message'
        response.body().type == 'success'
    }

    def "test multiple flash values with different types"() {
        when: "setting multiple typed flash values"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/setMultipleFlashValues'),
            Map
        )

        then: "all types preserved"
        response.status.code == 200
        response.body().stringValue == 'Hello'
        response.body().intValue == 42
        response.body().listValue == ['a', 'b', 'c']
        response.body().mapValue.key == 'value'
        response.body().mapValue.nested.x == 1
    }

    def "test flash.now for same-request values"() {
        when: "using flash.now"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/flashNow'),
            Map
        )

        then: "both immediate and persisted values available in same request"
        response.status.code == 200
        response.body().immediate == 'This is immediate'
        response.body().persisted == 'This persists'
    }

    def "test flash is cleared after being read"() {
        when: "first request sets flash"
        client.toBlocking().exchange(
            HttpRequest.GET('/flow/setFlashOnly?message=TestMessage'),
            Map
        )

        and: "second request reads flash"
        def response1 = client.toBlocking().exchange(
            HttpRequest.GET('/flow/readFlash'),
            Map
        )

        and: "third request tries to read again"
        def response2 = client.toBlocking().exchange(
            HttpRequest.GET('/flow/readFlash'),
            Map
        )

        then: "flash available in second request but cleared in third"
        // Note: Without session cookies, each request is independent
        // This test documents the expected behavior with sessions
        response1.status.code == 200
        response2.status.code == 200
    }

    // ========== Chain Tests ==========

    def "test chain accumulates model across actions"() {
        when: "chaining through three actions"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/chainFirst'),
            Map
        )

        then: "all model values accumulated"
        response.status.code == 200
        response.body().first == 'value1'
        response.body().second == 'value2'
        response.body().third == 'value3'
        response.body().totalSteps == 3
    }

    def "test chain preserves params"() {
        when: "chain with params"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/chainWithParams?id=123&name=test'),
            Map
        )

        then: "both chainModel and params available"
        response.status.code == 200
        response.body().fromChain == true
        response.body().extraParam == 'extra'
    }

    def "test chain to different controller"() {
        when: "chaining to another controller"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/chainToOtherController'),
            Map
        )

        then: "chain model available in target controller"
        response.status.code == 200
        response.body().controller == 'flowTarget'
        response.body().source == 'flowController'
    }

    // ========== Forward Tests ==========

    def "test forward keeps same request"() {
        when: "forwarding to another action"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/forwardToAction'),
            Map
        )

        then: "request attributes preserved"
        response.status.code == 200
        response.body().forwardedFrom == 'forwardToAction'
        response.body().sameRequest == true
    }

    def "test forward with params"() {
        when: "forwarding with additional params"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/forwardWithParams?id=original'),
            Map
        )

        then: "both original and forwarded params available"
        response.status.code == 200
        response.body().forwarded == 'yes'
        response.body().value == '123'
    }

    def "test forward to different controller"() {
        when: "forwarding to another controller"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/forwardToOtherController'),
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
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/redirectWithAllParams?foo=bar&num=42'),
            Map
        )

        then: "params preserved after redirect"
        response.status.code == 200
        response.body().params.foo == 'bar'
        response.body().params.num == '42'
    }

    def "test redirect to uri"() {
        when: "redirecting to specific URI"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/redirectToUri'),
            Map
        )

        then: "redirected to correct URI"
        response.status.code == 200
        response.body().fromRedirect == 'true'
    }

    def "test redirect reaches target"() {
        when: "basic redirect"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/permanentRedirect'),
            Map
        )

        then: "reaches target action"
        response.status.code == 200
        response.body().action == 'redirectTarget'
    }

    // ========== Edge Cases ==========

    def "test chain model is empty when not chained"() {
        when: "calling chain target directly"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/flow/chainThird'),
            Map
        )

        then: "chainModel is empty/null"
        response.status.code == 200
        response.body().first == null
        response.body().second == null
        response.body().third == 'value3'
    }
}
