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
package functionaltests.cors

import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Integration tests for CORS (Cross-Origin Resource Sharing) functionality.
 * Tests preflight requests, CORS headers, and cross-origin scenarios.
 *
 * Note: CORS is enabled for /api/** in application.yml
 */
@Integration
@Tag('http-client')
class CorsAdvancedSpec extends Specification implements HttpClientSupport {

    // ========== Basic CORS Header Tests ==========

    def "GET request to CORS-enabled endpoint includes CORS headers"() {
        when:
        def response = http('/api/cors', 'Origin': 'http://example.com')

        then: 'CORS headers should be present'
        response.assertHeaders(200, 'Access-Control-Allow-Origin': '*')
    }

    def "OPTIONS preflight request returns appropriate headers"() {
        when:
        def response = httpOptions(
                '/api/cors/data',
                'Origin': 'http://example.com',
                'Access-Control-Request-Method': 'GET'
        )

        then:
        response.assertHeaders(200,
                'Access-Control-Allow-Methods': 'GET',
                'Access-Control-Allow-Origin': '*'
        )
    }

    def "preflight request for POST method succeeds"() {
        when:
        def response = httpOptions(
                '/api/cors/items',
                'Origin': 'http://example.com',
                'Access-Control-Request-Method': 'POST',
                'Access-Control-Request-Headers': 'Content-Type'
        )

        then:
        response.assertHeaders(200,
                'Access-Control-Allow-Methods': 'POST',
                'Access-Control-Allow-Origin': '*'
        )
    }

    def "preflight request for PUT method succeeds"() {
        when:
        def response = httpOptions(
                '/api/cors/items/1',
                'Origin': 'http://example.com',
                'Access-Control-Request-Method': 'PUT',
                'Access-Control-Request-Headers': 'Content-Type'
        )

        then:
        response.assertStatus(200)
    }

    def "preflight request for DELETE method succeeds"() {
        when:
        def response = httpOptions(
                '/api/cors/items/1',
                'Origin': 'http://example.com',
                'Access-Control-Request-Method': 'DELETE'
        )

        then:
        response.assertStatus(200)
    }

    // ========== Actual Request Tests ==========

    def "GET request to CORS endpoint returns data"() {
        when:
        def response = http(
                '/api/cors/data',
                'Origin': 'http://example.com'
        )

        then:
        response.assertStatus(200).json()
        with(response.json()) {
            data.size() == 3
            total == 3
        }
    }

    def "POST request with CORS headers succeeds"() {
        when:
        def response = httpPostJson(
                '/api/cors/items',
                '{"name":"New Item"}',
                'Origin': 'http://example.com'
        )


        then:
        response.assertJsonContains(200, [
                created: true,
                method : 'POST'
        ])
    }

    def "PUT request with CORS headers succeeds"() {
        when:
        def response = httpPutJson(
                '/api/cors/items/42',
                '{"name":"Updated Item"}',
                'Origin': 'http://example.com'
        )

        then:
        response.assertJsonContains(200, [
                updated: true,
                id     : '42',
                method : 'PUT'
        ])
    }

    def "DELETE request with CORS headers succeeds"() {
        when:
        def response = httpDelete(
                '/api/cors/items/99',
                'Origin': 'http://example.com'
        )

        then:
        response.assertJson(200, [
                deleted: true,
                id     : '99',
                method : 'DELETE'
        ])
    }

    // ========== Custom Headers Tests ==========

    def "response with custom headers includes CORS headers"() {
        when:
        def response = http('/api/cors/custom-headers', 'Origin': 'http://example.com')

        then:
        response.assertJson(200, 'X-Custom-Response': 'custom-value', [
                customHeadersSet: true,
                message         : 'Response with custom headers'
        ])
    }

    def "echo origin endpoint returns received origin"() {
        when:
        def response = http('/api/cors/echo-origin', 'Origin': 'http://my-app.example.com')

        then:
        response.assertJson(200, [
                message       : 'Origin header received',
                receivedOrigin: 'http://my-app.example.com'
        ])
    }

    // ========== Credentials Tests ==========

    def "authenticated endpoint receives authorization header"() {
        when:
        def response = http(
                '/api/cors/authenticated',
                'Origin': 'http://example.com',
                'Authorization': 'Bearer test-token-123'
        )

        then:
        response.assertJson(200, [
                authenticated: true,
                authType     : 'Bearer',
                message      : 'Credentials received'
        ])
    }

    def "authenticated endpoint without credentials returns unauthenticated"() {
        when:
        def response = http('/api/cors/authenticated', 'Origin': 'http://example.com')

        then:
        response.assertJson(200, [
                authType     : null,
                authenticated: false,
                message      : 'No credentials'
        ])
    }

    // ========== Various Origin Tests ==========

    def "request from localhost origin succeeds"() {
        when:
        def response = http('/api/cors', 'Origin': 'http://localhost:3000')

        then:
        response.assertJson(200, [
                message: 'CORS test endpoint',
                path   : '/api/corsTest'
        ])
    }

    def "request from HTTPS origin succeeds"() {
        when:
        def response = http('/api/cors', 'Origin': 'https://secure.example.com')

        then:
        response.assertJson(200, [
                message: 'CORS test endpoint',
                path   : '/api/corsTest'
        ])
    }

    def "request with port in origin succeeds"() {
        when:
        def response = http('/api/cors', 'Origin': 'http://example.com:8080')

        then:
        response.assertJson(200, [
                message: 'CORS test endpoint',
                path   : '/api/corsTest'
        ])
    }
}
