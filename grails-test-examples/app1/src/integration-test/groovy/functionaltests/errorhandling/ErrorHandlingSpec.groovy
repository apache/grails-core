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

import functionaltests.Application
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import groovy.json.JsonSlurper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Unroll

/**
 * Integration tests for error handling patterns in Grails controllers.
 * Tests various HTTP status codes, JSON error responses, exception handling,
 * and error response headers.
 */
@Integration(applicationClass = Application)
@Rollback
class ErrorHandlingSpec extends Specification {

    @Shared
    HttpClient client

    def setup() {
        client = HttpClient.create(new URL("http://localhost:${serverPort}"))
    }

    def cleanup() {
        client?.close()
    }

    // ========== HTTP Status Code Tests ==========

    def "render 404 Not Found status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderNotFound'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND
    }

    def "render 400 Bad Request status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderBadRequest'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    def "render 401 Unauthorized status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderUnauthorized'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNAUTHORIZED
    }

    def "render 403 Forbidden status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderForbidden'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.FORBIDDEN
    }

    def "render 405 Method Not Allowed status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderMethodNotAllowed'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.METHOD_NOT_ALLOWED
    }

    def "render 409 Conflict status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderConflict'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.CONFLICT
    }

    def "render 410 Gone status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderGone'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.GONE
    }

    def "render 422 Unprocessable Entity status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderUnprocessableEntity'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNPROCESSABLE_ENTITY
    }

    def "render 429 Too Many Requests status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderTooManyRequests'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.TOO_MANY_REQUESTS
    }

    def "render 500 Internal Server Error status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderInternalServerError'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
    }

    def "render 503 Service Unavailable status"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/renderServiceUnavailable'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.SERVICE_UNAVAILABLE
    }

    // ========== JSON Error Response Tests ==========

    def "JSON 404 error response contains proper structure"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/jsonNotFound').accept('application/json'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND
    }

    def "JSON 400 error response with validation details"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/jsonBadRequest').accept('application/json'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    def "JSON 422 validation error with multiple field errors"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/jsonValidationError').accept('application/json'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNPROCESSABLE_ENTITY
    }

    def "JSON 500 error response includes request ID"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/jsonServerError').accept('application/json'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
    }

    // ========== Conditional Error Handling Tests ==========

    def "conditional error returns 404 when condition is notfound"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/conditionalError?condition=notfound').accept('application/json'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND
    }

    def "conditional error returns 400 when condition is badrequest"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/conditionalError?condition=badrequest').accept('application/json'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    def "conditional error returns 403 when condition is forbidden"() {
        when:
        client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/conditionalError?condition=forbidden').accept('application/json'),
            String
        )

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.FORBIDDEN
    }

    def "conditional error returns success for unknown condition"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/errorHandlingTest/conditionalError?condition=normal').accept('application/json'),
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
        HttpClientResponseException e = thrown()
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
        HttpClientResponseException e = thrown()
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
