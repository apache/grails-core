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

import functionaltests.Application
import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Narrative
import spock.lang.Specification

/**
 * Integration tests for Grails URL mappings features.
 * 
 * Tests static paths, path variables, constraints, HTTP method mappings,
 * redirects, and various URL mapping patterns.
 */
@Integration(applicationClass = Application)
@Narrative('''
Grails URL mappings provide flexible routing of HTTP requests to controller actions.
This includes path variables, constraints, HTTP method-based routing, and redirects.
''')
class UrlMappingsSpec extends Specification {

    private HttpClient createClient() {
        HttpClient.create(new URL("http://localhost:$serverPort"))
    }

    // ========== Static Path Mappings ==========

    def "static path mapping routes to correct action"() {
        given:
        def client = createClient()

        when: "accessing static path"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/test'),
            String
        )

        then: "routes to index action"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.controller == 'urlMappingsTest'
        json.action == 'index'

        cleanup:
        client.close()
    }

    // ========== Path Variable Mappings ==========

    def "single path variable is captured"() {
        given:
        def client = createClient()

        when: "accessing path with variable"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/items/123'),
            String
        )

        then: "variable is captured"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.action == 'show'
        json.id == '123'

        cleanup:
        client.close()
    }

    def "path variable accepts alphanumeric values"() {
        given:
        def client = createClient()

        when: "accessing path with alphanumeric id"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/items/abc-123'),
            String
        )

        then: "variable is captured correctly"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.id == 'abc-123'

        cleanup:
        client.close()
    }

    def "multiple path variables are captured"() {
        given:
        def client = createClient()

        when: "accessing path with multiple variables"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/archive/2024/03/15'),
            String
        )

        then: "all variables are captured"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.action == 'pathVars'
        json.year == '2024'
        json.month == '03'
        json.day == '15'

        cleanup:
        client.close()
    }

    // ========== Named URL Mappings ==========

    def "named mapping routes correctly"() {
        given:
        def client = createClient()

        when: "accessing named mapping"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/named/test-name'),
            String
        )

        then: "routes to correct action with variable"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.action == 'named'
        json.name == 'test-name'

        cleanup:
        client.close()
    }

    // ========== Constrained Path Variables ==========

    def "constrained path accepts valid values"() {
        given:
        def client = createClient()

        when: "accessing with valid constrained value"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/codes/ABC'),
            String
        )

        then: "request succeeds"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.action == 'constrained'
        json.code == 'ABC'

        cleanup:
        client.close()
    }

    def "constrained path rejects invalid values"() {
        given:
        def client = createClient()

        when: "accessing with invalid constrained value (lowercase)"
        client.toBlocking().exchange(
            HttpRequest.GET('/api/codes/abc'),
            String
        )

        then: "request is rejected with 404"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND

        cleanup:
        client.close()
    }

    def "constrained path rejects numeric values"() {
        given:
        def client = createClient()

        when: "accessing with numeric value"
        client.toBlocking().exchange(
            HttpRequest.GET('/api/codes/123'),
            String
        )

        then: "request is rejected with 404"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND

        cleanup:
        client.close()
    }

    // ========== HTTP Method Mappings ==========

    def "GET request routes to list action"() {
        given:
        def client = createClient()

        when: "making GET request to resources"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/resources'),
            String
        )

        then: "routes to list action"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.action == 'list'

        cleanup:
        client.close()
    }

    def "POST request routes to save action"() {
        given:
        def client = createClient()

        when: "making POST request to resources"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.POST('/api/resources', '{}'),
            String
        )

        then: "routes to save action"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.action == 'save'
        json.method == 'POST'

        cleanup:
        client.close()
    }

    def "PUT request routes to update action"() {
        given:
        def client = createClient()

        when: "making PUT request to resources with id"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.PUT('/api/resources/42', '{}'),
            String
        )

        then: "routes to update action"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.action == 'update'
        json.id == '42'
        json.method == 'PUT'

        cleanup:
        client.close()
    }

    def "DELETE request routes to delete action"() {
        given:
        def client = createClient()

        when: "making DELETE request to resources with id"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.DELETE('/api/resources/42'),
            String
        )

        then: "routes to delete action"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.action == 'delete'
        json.id == '42'
        json.method == 'DELETE'

        cleanup:
        client.close()
    }

    // ========== Optional Path Variables ==========

    def "optional path variable with value"() {
        given:
        def client = createClient()

        when: "accessing with optional variable provided"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/optional/required-value/optional-value'),
            String
        )

        then: "both values captured"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.required == 'required-value'
        json.optional == 'optional-value'

        cleanup:
        client.close()
    }

    def "optional path variable without value uses default"() {
        given:
        def client = createClient()

        when: "accessing without optional variable"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/optional/required-value'),
            String
        )

        then: "required captured, optional uses default"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.required == 'required-value'
        json.optional == 'default'

        cleanup:
        client.close()
    }

    // ========== Redirect Mappings ==========

    def "redirect mapping performs redirect"() {
        given:
        def client = HttpClient.create(new URL("http://localhost:$serverPort"))

        when: "accessing redirect mapping"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/old-endpoint').header('Accept', '*/*'),
            String
        )

        then: "redirect is performed and final response received"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.action == 'index'

        cleanup:
        client.close()
    }

    // ========== Default Controller/Action Mapping ==========

    def "default mapping with controller and action"() {
        given:
        def client = createClient()

        when: "using default mapping pattern"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/urlMappingsTest/show/99'),
            String
        )

        then: "routes correctly"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.controller == 'urlMappingsTest'
        json.action == 'show'
        json.id == '99'

        cleanup:
        client.close()
    }

    def "default mapping with format extension"() {
        given:
        def client = createClient()

        when: "using default mapping with format"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/urlMappingsTest/list.json'),
            String
        )

        then: "routes correctly with format"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.action == 'list'

        cleanup:
        client.close()
    }

    // ========== Query Parameter Handling ==========

    def "query parameters are accessible in action"() {
        given:
        def client = createClient()

        when: "accessing with query parameters"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/test?param1=value1&param2=value2'),
            String
        )

        then: "query params are accessible"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.params.param1 == 'value1'
        json.params.param2 == 'value2'

        cleanup:
        client.close()
    }

    // ========== HTTP Method Detection ==========

    def "request method is correctly detected"() {
        given:
        def client = createClient()

        when: "making request"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/api/method-test'),
            String
        )

        then: "method is correctly detected"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.method == 'GET'

        cleanup:
        client.close()
    }

    // ========== 404 Not Found ==========

    def "non-existent path returns 404"() {
        given:
        def client = createClient()

        when: "accessing non-existent path"
        client.toBlocking().exchange(
            HttpRequest.GET('/api/does-not-exist'),
            String
        )

        then: "returns 404"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND

        cleanup:
        client.close()
    }
}
