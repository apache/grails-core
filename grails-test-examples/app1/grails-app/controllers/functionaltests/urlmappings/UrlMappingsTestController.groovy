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

package functionaltests.urlmappings

import grails.converters.JSON

/**
 * Controller for testing URL mapping features.
 */
class UrlMappingsTestController {

    static responseFormats = ['json']

    /**
     * Returns information about the requested parameters.
     */
    def index() {
        render([
            controller: 'urlMappingsTest',
            action: 'index',
            params: params.findAll { k, v -> !['controller', 'action'].contains(k) }
        ] as JSON)
    }

    /**
     * Show action with ID parameter.
     */
    def show() {
        render([
            controller: 'urlMappingsTest',
            action: 'show',
            id: params.id
        ] as JSON)
    }

    /**
     * Action with multiple path variables.
     */
    def pathVars() {
        render([
            controller: 'urlMappingsTest',
            action: 'pathVars',
            year: params.year,
            month: params.month,
            day: params.day
        ] as JSON)
    }

    /**
     * Action for testing named URL mappings.
     */
    def named() {
        render([
            controller: 'urlMappingsTest',
            action: 'named',
            name: params.name
        ] as JSON)
    }

    /**
     * Action for testing constrained parameters.
     */
    def constrained() {
        render([
            controller: 'urlMappingsTest',
            action: 'constrained',
            code: params.code
        ] as JSON)
    }

    /**
     * Action that demonstrates wildcard capture.
     */
    def wildcard() {
        render([
            controller: 'urlMappingsTest',
            action: 'wildcard',
            path: params.path
        ] as JSON)
    }

    /**
     * Action for REST-style resource.
     */
    def list() {
        render([
            controller: 'urlMappingsTest',
            action: 'list',
            format: params.format ?: 'json'
        ] as JSON)
    }

    /**
     * Create action for REST resource.
     */
    def save() {
        render([
            controller: 'urlMappingsTest',
            action: 'save',
            method: 'POST'
        ] as JSON)
    }

    /**
     * Update action for REST resource.
     */
    def update() {
        render([
            controller: 'urlMappingsTest',
            action: 'update',
            id: params.id,
            method: 'PUT'
        ] as JSON)
    }

    /**
     * Delete action for REST resource.
     */
    def delete() {
        render([
            controller: 'urlMappingsTest',
            action: 'delete',
            id: params.id,
            method: 'DELETE'
        ] as JSON)
    }

    /**
     * Action for optional parameters.
     */
    def optional() {
        render([
            controller: 'urlMappingsTest',
            action: 'optional',
            required: params.required,
            optional: params.optional ?: 'default'
        ] as JSON)
    }

    /**
     * Action that returns HTTP method info.
     */
    def httpMethod() {
        render([
            controller: 'urlMappingsTest',
            action: 'httpMethod',
            method: request.method
        ] as JSON)
    }
}
