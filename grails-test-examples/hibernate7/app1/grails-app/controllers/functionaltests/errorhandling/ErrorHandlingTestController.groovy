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

import grails.converters.JSON
import grails.web.http.HttpHeaders

/**
 * Controller demonstrating various error handling patterns in Grails.
 */
class ErrorHandlingTestController {

    static responseFormats = ['json', 'html']

    // ========== HTTP Status Code Tests ==========

    def renderNotFound() {
        render status: 404, text: 'Resource not found'
    }

    def renderBadRequest() {
        render status: 400, text: 'Bad request'
    }

    def renderUnauthorized() {
        render status: 401, text: 'Unauthorized'
    }

    def renderForbidden() {
        render status: 403, text: 'Forbidden'
    }

    def renderMethodNotAllowed() {
        render status: 405, text: 'Method not allowed'
    }

    def renderConflict() {
        render status: 409, text: 'Conflict'
    }

    def renderGone() {
        render status: 410, text: 'Gone'
    }

    def renderUnprocessableEntity() {
        render status: 422, text: 'Unprocessable entity'
    }

    def renderTooManyRequests() {
        render status: 429, text: 'Too many requests'
    }

    def renderInternalServerError() {
        render status: 500, text: 'Internal server error'
    }

    def renderServiceUnavailable() {
        render status: 503, text: 'Service unavailable'
    }

    // ========== JSON Error Response Tests ==========

    def jsonNotFound() {
        response.status = 404
        render([error: 'not_found', message: 'The requested resource was not found'] as JSON)
    }

    def jsonBadRequest() {
        response.status = 400
        render([error: 'bad_request', message: 'Invalid request parameters', details: [field: 'name', issue: 'required']] as JSON)
    }

    def jsonValidationError() {
        response.status = 422
        render([
            error: 'validation_error',
            message: 'Validation failed',
            errors: [
                [field: 'email', message: 'Invalid email format'],
                [field: 'age', message: 'Must be at least 18']
            ]
        ] as JSON)
    }

    def jsonServerError() {
        response.status = 500
        render([error: 'internal_error', message: 'An unexpected error occurred', requestId: UUID.randomUUID().toString()] as JSON)
    }

    // ========== Exception Throwing Tests ==========

    def throwRuntimeException() {
        throw new RuntimeException('A runtime error occurred')
    }

    def throwIllegalArgumentException() {
        throw new IllegalArgumentException('Invalid argument provided')
    }

    def throwIllegalStateException() {
        throw new IllegalStateException('Invalid state')
    }

    def throwNullPointerException() {
        String s = null
        s.length() // This will throw NPE
    }

    def throwIndexOutOfBounds() {
        def list = []
        list[10] // This will throw IndexOutOfBoundsException
    }

    def throwArithmeticException() {
        def result = 1 / 0 // This will throw ArithmeticException
        render([result: result] as JSON)
    }

    def throwNumberFormatException() {
        Integer.parseInt('not-a-number')
    }

    def throwCustomBusinessException() {
        throw new BusinessException('INVALID_ORDER', 'Order cannot be processed')
    }

    def throwNestedExceptions() {
        try {
            throw new IllegalArgumentException('Root cause')
        } catch (Exception e) {
            throw new RuntimeException('Wrapper exception', e)
        }
    }

    // ========== Conditional Error Handling ==========

    def conditionalError() {
        def condition = params.condition
        switch (condition) {
            case 'notfound':
                response.status = 404
                render([error: 'not_found'] as JSON)
                break
            case 'badrequest':
                response.status = 400
                render([error: 'bad_request'] as JSON)
                break
            case 'forbidden':
                response.status = 403
                render([error: 'forbidden'] as JSON)
                break
            case 'error':
                throw new RuntimeException('Conditional error triggered')
            default:
                render([status: 'ok', condition: condition] as JSON)
        }
    }

    // ========== Response with Headers ==========

    def errorWithHeaders() {
        response.status = 429
        response.setHeader('Retry-After', '60')
        response.setHeader('X-RateLimit-Limit', '100')
        response.setHeader('X-RateLimit-Remaining', '0')
        response.setHeader('X-RateLimit-Reset', String.valueOf(System.currentTimeMillis() + 60000))
        render([error: 'rate_limited', retryAfter: 60] as JSON)
    }

    def notFoundWithHints() {
        response.status = 404
        response.setHeader('X-Suggested-Resource', '/api/items')
        render([error: 'not_found', suggestions: ['/api/items', '/api/products']] as JSON)
    }

    // ========== Successful Operations for Comparison ==========

    def success() {
        render([status: 'ok', message: 'Operation successful'] as JSON)
    }

    def successWithData() {
        render([status: 'ok', data: [id: 1, name: 'Test Item', createdAt: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")]] as JSON)
    }
}

/**
 * Custom business exception for testing exception handling.
 */
class BusinessException extends RuntimeException {
    String code
    String description

    BusinessException(String code, String description) {
        super("$code: $description")
        this.code = code
        this.description = description
    }
}
