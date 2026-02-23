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

import grails.converters.JSON

/**
 * Controller for testing CORS (Cross-Origin Resource Sharing) functionality.
 * This controller provides endpoints under /api/* which have CORS enabled
 * via application.yml configuration.
 */
class CorsTestController {

    static responseFormats = ['json']

    // ========== Basic CORS Endpoints ==========

    def index() {
        render([message: 'CORS test endpoint', path: '/api/corsTest'] as JSON)
    }

    def getData() {
        render([
            data: [
                [id: 1, name: 'Item 1', description: 'First item'],
                [id: 2, name: 'Item 2', description: 'Second item'],
                [id: 3, name: 'Item 3', description: 'Third item']
            ],
            total: 3
        ] as JSON)
    }

    def getItem() {
        def id = params.id
        render([id: id, name: "Item ${id}", retrieved: true] as JSON)
    }

    // ========== POST/PUT/DELETE endpoints for CORS testing ==========

    def create() {
        def data = request.JSON ?: [:]
        render([created: true, data: data, method: 'POST'] as JSON)
    }

    def update() {
        def id = params.id
        def data = request.JSON ?: [:]
        render([updated: true, id: id, data: data, method: 'PUT'] as JSON)
    }

    def delete() {
        def id = params.id
        render([deleted: true, id: id, method: 'DELETE'] as JSON)
    }

    // ========== Custom Header Endpoints ==========

    def withCustomHeaders() {
        response.setHeader('X-Custom-Response', 'custom-value')
        response.setHeader('X-Request-Timestamp', String.valueOf(System.currentTimeMillis()))
        render([
            message: 'Response with custom headers',
            customHeadersSet: true
        ] as JSON)
    }

    def echoOrigin() {
        def origin = request.getHeader('Origin')
        render([
            receivedOrigin: origin,
            message: 'Origin header received'
        ] as JSON)
    }

    // ========== Authenticated/Credentials Endpoint ==========

    def authenticated() {
        def authHeader = request.getHeader('Authorization')
        def hasCredentials = authHeader != null
        render([
            authenticated: hasCredentials,
            authType: hasCredentials ? authHeader.split(' ')[0] : null,
            message: hasCredentials ? 'Credentials received' : 'No credentials'
        ] as JSON)
    }

    // ========== Long-running request for timing ==========

    def slowRequest() {
        Thread.sleep(100) // Simulate processing
        render([
            completed: true,
            processingTime: 100,
            message: 'Slow request completed'
        ] as JSON)
    }
}
