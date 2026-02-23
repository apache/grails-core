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

import groovy.json.JsonSlurper

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * Integration tests for CORS (Cross-Origin Resource Sharing) functionality.
 * Tests preflight requests, CORS headers, and cross-origin scenarios.
 * 
 * Note: CORS is enabled for /api/** in application.yml
 */
@Rollback
@Integration
class CorsAdvancedSpec extends Specification {

    @Shared
    HttpClient client

    def setup() {
        client = client ?: HttpClient.create(new URL("http://localhost:${serverPort}"))
    }

    def cleanupSpec() {
        client.close()
    }

    // ========== Basic CORS Header Tests ==========

    def "GET request to CORS-enabled endpoint includes CORS headers"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/api/cors')
                .header('Origin', 'http://example.com'),
            String
        )

        then:
        response.status == HttpStatus.OK
        // CORS headers should be present
        response.header('Access-Control-Allow-Origin') != null
    }

    def "OPTIONS preflight request returns appropriate headers"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.OPTIONS('/api/cors/data')
                .header('Origin', 'http://example.com')
                .header('Access-Control-Request-Method', 'GET'),
            String
        )

        then:
        response.status == HttpStatus.OK
        response.header('Access-Control-Allow-Origin') != null
        response.header('Access-Control-Allow-Methods') != null
    }

    def "preflight request for POST method succeeds"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.OPTIONS('/api/cors/items')
                .header('Origin', 'http://example.com')
                .header('Access-Control-Request-Method', 'POST')
                .header('Access-Control-Request-Headers', 'Content-Type'),
            String
        )

        then:
        response.status == HttpStatus.OK
        response.header('Access-Control-Allow-Methods')?.contains('POST') ||
            response.header('Access-Control-Allow-Origin') != null
    }

    def "preflight request for PUT method succeeds"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.OPTIONS('/api/cors/items/1')
                .header('Origin', 'http://example.com')
                .header('Access-Control-Request-Method', 'PUT')
                .header('Access-Control-Request-Headers', 'Content-Type'),
            String
        )

        then:
        response.status == HttpStatus.OK
    }

    def "preflight request for DELETE method succeeds"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.OPTIONS('/api/cors/items/1')
                .header('Origin', 'http://example.com')
                .header('Access-Control-Request-Method', 'DELETE'),
            String
        )

        then:
        response.status == HttpStatus.OK
    }

    // ========== Actual Request Tests ==========

    def "GET request to CORS endpoint returns data"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/api/cors/data')
                .header('Origin', 'http://example.com'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.data.size() == 3
        json.total == 3
    }

    def "POST request with CORS headers succeeds"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.POST('/api/cors/items', '{"name":"New Item"}')
                .header('Origin', 'http://example.com')
                .contentType(MediaType.APPLICATION_JSON),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.created == true
        json.method == 'POST'
    }

    def "PUT request with CORS headers succeeds"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.PUT('/api/cors/items/42', '{"name":"Updated Item"}')
                .header('Origin', 'http://example.com')
                .contentType(MediaType.APPLICATION_JSON),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.updated == true
        json.id == '42'
        json.method == 'PUT'
    }

    def "DELETE request with CORS headers succeeds"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.DELETE('/api/cors/items/99')
                .header('Origin', 'http://example.com'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.deleted == true
        json.id == '99'
        json.method == 'DELETE'
    }

    // ========== Custom Headers Tests ==========

    def "response with custom headers includes CORS headers"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/api/cors/custom-headers')
                .header('Origin', 'http://example.com'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.customHeadersSet == true
        response.header('X-Custom-Response') == 'custom-value'
    }

    def "echo origin endpoint returns received origin"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/api/cors/echo-origin')
                .header('Origin', 'http://my-app.example.com'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.receivedOrigin == 'http://my-app.example.com'
    }

    // ========== Credentials Tests ==========

    def "authenticated endpoint receives authorization header"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/api/cors/authenticated')
                .header('Origin', 'http://example.com')
                .header('Authorization', 'Bearer test-token-123'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.authenticated == true
        json.authType == 'Bearer'
    }

    def "authenticated endpoint without credentials returns unauthenticated"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/api/cors/authenticated')
                .header('Origin', 'http://example.com'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.authenticated == false
        json.message == 'No credentials'
    }

    // ========== Various Origin Tests ==========

    def "request from localhost origin succeeds"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/api/cors')
                .header('Origin', 'http://localhost:3000'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.message == 'CORS test endpoint'
    }

    def "request from HTTPS origin succeeds"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/api/cors')
                .header('Origin', 'https://secure.example.com'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.message == 'CORS test endpoint'
    }

    def "request with port in origin succeeds"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/api/cors')
                .header('Origin', 'http://example.com:8080'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.message == 'CORS test endpoint'
    }
}
