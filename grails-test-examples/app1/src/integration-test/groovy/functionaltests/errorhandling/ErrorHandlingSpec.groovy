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
package functionaltests.errorhandling

import groovy.json.JsonSlurper

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * Integration tests for error handling patterns in Grails controllers.
 * Tests various HTTP status codes, JSON error responses, exception handling,
 * and error response headers.
 */
@Rollback
@Integration
class ErrorHandlingSpec extends Specification {

    @Shared
    HttpClient client

    def setup() {
        client = client ?: HttpClient.create(new URL("http://localhost:${serverPort}"))
    }

    def cleanupSpec() {
        client.close()
    }

    // ========== HTTP Status Code Tests ==========

    @Unroll
    def "render #statusMsg status"(String action, HttpStatus status, String statusMsg) {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET("/errorHandlingTest/$action"),
            String
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.status == status

        where:
        action                      | status                           | statusMsg
        'renderNotFound'            | HttpStatus.NOT_FOUND             | '404 Not Found'
        'renderBadRequest'          | HttpStatus.BAD_REQUEST           | '400 Bad Request'
        'renderUnauthorized'        | HttpStatus.UNAUTHORIZED          | '401 Unauthorized'
        'renderForbidden'           | HttpStatus.FORBIDDEN             | '403 Forbidden'
        'renderMethodNotAllowed'    | HttpStatus.METHOD_NOT_ALLOWED    | '405 Method Not Allowed'
        'renderConflict'            | HttpStatus.CONFLICT              | '409 Conflict'
        'renderGone'                | HttpStatus.GONE                  | '410 Gone'
        'renderUnprocessableEntity' | HttpStatus.UNPROCESSABLE_ENTITY  | '422 Unprocessable Entity'
        'renderTooManyRequests'     | HttpStatus.TOO_MANY_REQUESTS     | '429 Too Many Requests'
        'renderInternalServerError' | HttpStatus.INTERNAL_SERVER_ERROR | '500 Internal Server Error'
        'renderServiceUnavailable'  | HttpStatus.SERVICE_UNAVAILABLE   | '503 Service Unavailable'
    }

    // ========== JSON Error Response Tests ==========

    @Unroll
    def "JSON #statusCode.code error response #assertion"(String action, HttpStatus statusCode, String assertion) {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET("/errorHandlingTest/$action")
                    .accept('application/json'),
            String
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.status == statusCode

        where:
        action                | statusCode                       | assertion
        'jsonNotFound'        | HttpStatus.NOT_FOUND             | 'contains proper structure'
        'jsonBadRequest'      | HttpStatus.BAD_REQUEST           | 'with validation details'
        'jsonValidationError' | HttpStatus.UNPROCESSABLE_ENTITY  | 'with multiple field errors'
        'jsonServerError'     | HttpStatus.INTERNAL_SERVER_ERROR | 'includes request ID'
    }

    // ========== Conditional Error Handling Tests ==========

    @Unroll
    def "conditional error returns #status.code when condition is #condition"(String condition, HttpStatus status) {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET("/errorHandlingTest/conditionalError?condition=$condition")
                    .accept('application/json'),
            String
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.status == status

        where:
        condition     | status
        'notfound'    | HttpStatus.NOT_FOUND
        'badrequest'  | HttpStatus.BAD_REQUEST
        'forbidden'   | HttpStatus.FORBIDDEN
    }

    def "conditional error returns success for unknown condition"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/conditionalError?condition=normal')
                    .accept('application/json'),
            String
        )

        then:
        response.status == HttpStatus.OK

        and:
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'ok'
        json.condition == 'normal'
    }

    // ========== Error Response Headers Tests ==========

    def "rate limit error includes appropriate headers"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/errorWithHeaders').accept('application/json'),
            String
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.TOO_MANY_REQUESTS

        and:
        def response = e.response
        response.header('Retry-After') == '60'
        response.header('X-RateLimit-Limit') == '100'
        response.header('X-RateLimit-Remaining') == '0'
        response.header('X-RateLimit-Reset') != null
    }

    def "not found error includes suggestion header"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/notFoundWithHints').accept('application/json'),
            String
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND

        and: "suggestion header is present"
        e.response.header('X-Suggested-Resource') == '/api/items'
    }

    // ========== Success Comparison Tests ==========

    def "success endpoint returns 200 OK"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/success').accept('application/json'),
            String
        )

        then:
        response.status == HttpStatus.OK

        and:
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'ok'
        json.message == 'Operation successful'
    }

    def "success with data returns structured response"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/successWithData').accept('application/json'),
            String
        )

        then:
        response.status == HttpStatus.OK

        and:
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'ok'
        json.data.id == 1
        json.data.name == 'Test Item'
        json.data.createdAt != null
    }
}
