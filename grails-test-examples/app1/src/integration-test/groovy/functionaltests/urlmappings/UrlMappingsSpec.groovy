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

import spock.lang.Narrative
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

/**
 * Integration tests for Grails URL mappings features.
 * 
 * Tests static paths, path variables, constraints, HTTP method mappings,
 * redirects, and various URL mapping patterns.
 */
@Integration
@Narrative('''
Grails URL mappings provide flexible routing of HTTP requests to controller actions.
This includes path variables, constraints, HTTP method-based routing, and redirects.
''')
class UrlMappingsSpec extends Specification implements HttpClientSupport {

    // ========== Static Path Mappings ==========

    def "static path mapping routes to correct action"() {
        when: "accessing static path"
        def response = http('/api/test')

        then: "routes to index action"
        response.expectJsonContains(200, [
                controller: 'urlMappingsTest',
                action: 'index'
        ])
    }

    // ========== Path Variable Mappings ==========

    def "single path variable is captured"() {
        when: "accessing path with variable"
        def response = http('/api/items/123')

        then: "variable is captured"
        response.expectJsonContains(200, [
                action: 'show',
                id: '123'

        ])
    }

    def "path variable accepts alphanumeric values"() {
        when: "accessing path with alphanumeric id"
        def response = http('/api/items/abc-123')

        then: "variable is captured correctly"
        response.expectJsonContains(200, [id: 'abc-123'])
    }

    def "multiple path variables are captured"() {
        when: "accessing path with multiple variables"
        def response = http('/api/archive/2024/03/15')

        then: "all variables are captured"
        response.expectJsonContains(200, [
                action: 'pathVars',
                year: '2024',
                month: '03',
                day: '15'
        ])
    }

    // ========== Named URL Mappings ==========

    def "named mapping routes correctly"() {
        when: "accessing named mapping"
        def response = http('/api/named/test-name')

        then: "routes to correct action with variable"
        response.expectJsonContains(200, [
                action: 'named',
                name: 'test-name'
        ])
    }

    // ========== Constrained Path Variables ==========

    def "constrained path accepts valid values"() {
        when: "accessing with valid constrained value"
        def response = http('/api/codes/ABC')

        then: "request succeeds"
        response.expectJsonContains(200, [
                action: 'constrained',
                code: 'ABC'
        ])
    }

    def "constrained path rejects invalid values"() {
        when: "accessing with invalid constrained value (lowercase)"
        def response = http('/api/codes/abc')

        then: "request is rejected with 404"
        response.expectStatus(404)
    }

    def "constrained path rejects numeric values"() {
        when: "accessing with numeric value"
        def response = http('/api/codes/123')

        then: "request is rejected with 404"
        response.expectStatus(404)
    }

    // ========== HTTP Method Mappings ==========

    def "GET request routes to list action"() {
        when: "making GET request to resources"
        def response = http('/api/resources')

        then: "routes to list action"
        response.expectJsonContains(200, [action: 'list'])
    }

    def "POST request routes to save action"() {
        when: "making POST request to resources"
        def response = httpPost('/api/resources', '{}')

        then: "routes to save action"
        response.expectJsonContains(200, [
                action: 'save',
                method: 'POST'
        ])
    }

    def "PUT request routes to update action"() {
        when: "making PUT request to resources with id"
        def response = httpPut('/api/resources/42', '{}')

        then: "routes to update action"
        response.expectJsonContains(200, [
                action: 'update',
                id: '42',
                method: 'PUT'
        ])
    }

    def "DELETE request routes to delete action"() {
        when: "making DELETE request to resources with id"
        def response = httpDelete('/api/resources/42')

        then: "routes to delete action"
        response.expectJsonContains(200, [
                action: 'delete',
                id: '42',
                method: 'DELETE'
        ])
    }

    // ========== Optional Path Variables ==========

    def "optional path variable with value"() {
        when: "accessing with optional variable provided"
        def response = http('/api/optional/required-value/optional-value')

        then: "both values captured"
        response.expectJsonContains(200, [
                required: 'required-value',
                optional: 'optional-value'
        ])
    }

    def "optional path variable without value uses default"() {
        when: "accessing without optional variable"
        def response = http('/api/optional/required-value')

        then: "required captured, optional uses default"
        response.expectJsonContains(200, [
                required: 'required-value',
                optional: 'default'
        ])
    }

    // ========== Redirect Mappings ==========

    def "redirect mapping performs redirect"() {
        when: "accessing redirect mapping"
        def response = http('/api/old-endpoint', 'Accept': '*/*')

        then: "redirect is performed and final response received"
        response.expectJsonContains(200, [action: 'index'])
    }

    // ========== Default Controller/Action Mapping ==========

    def "default mapping with controller and action"() {
        when: "using default mapping pattern"
        def response = http('/urlMappingsTest/show/99')

        then: "routes correctly"
        response.expectJson(200, [
                controller: 'urlMappingsTest',
                action: 'show',
                id: '99'
        ])
    }

    def "default mapping with format extension"() {
        when: "using default mapping with format"
        def response = http('/urlMappingsTest/list.json')

        then: "routes correctly with format"
        response.expectJsonContains(200, [action: 'list'])
    }

    // ========== Query Parameter Handling ==========

    def "query parameters are accessible in action"() {
        when: "accessing with query parameters"
        def response = http('/api/test?param1=value1&param2=value2')

        then: "query params are accessible"
        response.expectJsonContains(200, [
                params: [
                        param1: 'value1',
                        param2: 'value2'
                ]
        ])
    }

    // ========== HTTP Method Detection ==========

    def "request method is correctly detected"() {
        when: "making request"
        def response = http('/api/method-test')

        then: "method is correctly detected"
        response.expectJsonContains(200, [method: 'GET'])
    }

    // ========== 404 Not Found ==========

    def "non-existent path returns 404"() {
        when: "accessing non-existent path"
        def response = http('/api/does-not-exist')

        then: "returns 404"
        response.expectStatus(404)
    }
}
