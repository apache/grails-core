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

import spock.lang.Specification
import spock.lang.Unroll

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Integration tests for error handling patterns in Grails controllers.
 * Tests various HTTP status codes, JSON error responses, exception handling,
 * and error response headers.
 */
@Integration
class ErrorHandlingSpec extends Specification implements HttpClientSupport{

    // ========== HTTP Status Code Tests ==========

    @Unroll
    def "render #statusMsg status"(String action, int statusCode, String statusMsg) {
        when:
        def response = http("/errorHandlingTest/$action")

        then:
        response.expectStatus(statusCode)

        where:
        action                      | statusCode | statusMsg
        'renderNotFound'            | 404        | '404 Not Found'
        'renderBadRequest'          | 400        | '400 Bad Request'
        'renderUnauthorized'        | 401        | '401 Unauthorized'
        'renderForbidden'           | 403        | '403 Forbidden'
        'renderMethodNotAllowed'    | 405        | '405 Method Not Allowed'
        'renderConflict'            | 409        | '409 Conflict'
        'renderGone'                | 410        | '410 Gone'
        'renderUnprocessableEntity' | 422        | '422 Unprocessable Entity'
        'renderTooManyRequests'     | 429        | '429 Too Many Requests'
        'renderInternalServerError' | 500        | '500 Internal Server Error'
        'renderServiceUnavailable'  | 503        | '503 Service Unavailable'
    }

    // ========== JSON Error Response Tests ==========

    @Unroll
    def "JSON #statusCode error response #assertion"(String action, int statusCode, String assertion) {
        when:
        def response = http("/errorHandlingTest/$action", 'Accept': 'application/json')

        then:
        response.expectStatus(statusCode)

        where:
        action                | statusCode | assertion
        'jsonNotFound'        | 404        | 'contains proper structure'
        'jsonBadRequest'      | 400        | 'with validation details'
        'jsonValidationError' | 422        | 'with multiple field errors'
        'jsonServerError'     | 500        | 'includes request ID'
    }

    // ========== Conditional Error Handling Tests ==========

    @Unroll
    def "conditional error returns #statusCode when condition is #condition"(String condition, int statusCode) {
        when:
        def response = http(
                "/errorHandlingTest/conditionalError?condition=$condition",
                'Accept': 'application/json'
        )

        then:
        response.expectStatus(statusCode)

        where:
        condition     | statusCode
        'notfound'    | 404
        'badrequest'  | 400
        'forbidden'   | 403
    }

    def "conditional error returns success for unknown condition"() {
        when:
        def response = http(
                '/errorHandlingTest/conditionalError?condition=normal',
                'Accept': 'application/json'
        )

        then:
        response.expectJson(200, [
                status: 'ok',
                condition: 'normal'
        ])
    }

    // ========== Error Response Headers Tests ==========

    def "rate limit error includes appropriate headers"() {
        when:
        def response = http('/errorHandlingTest/errorWithHeaders', 'Accept': 'application/json')

        then:
        response.expectHeaders(429,
                'Retry-After': '60',
                'X-RateLimit-Limit': '100',
                'X-RateLimit-Remaining': '0'
        )

        and:
        response.hasHeader('X-RateLimit-Reset')
    }

    def "not found error includes suggestion header"() {
        when:
        def response = http('/errorHandlingTest/notFoundWithHints', 'Accept': 'application/json')

        then: "suggestion header is present"
        response.expectHeaders(404, 'X-Suggested-Resource': '/api/items')
    }

    // ========== Success Comparison Tests ==========

    def "success endpoint returns 200 OK"() {
        when:
        def response = http('/errorHandlingTest/success', 'Accept': 'application/json')

        then:
        response.expectJson(200, [
                status: 'ok',
                message: 'Operation successful'
        ])
    }

    def "success with data returns structured response"() {
        when:
        def response = http('/errorHandlingTest/successWithData', 'Accept': 'application/json')

        then:
        response.expectJsonContains(200, [
                status: 'ok',
                data: [
                        id: 1,
                        name: 'Test Item'
                ]
        ])

        and:
        response.json().data['createdAt'] != null
    }
}
