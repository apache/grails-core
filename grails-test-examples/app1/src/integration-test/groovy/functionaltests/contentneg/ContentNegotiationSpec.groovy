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

package functionaltests.contentneg

import functionaltests.Application
import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Narrative
import spock.lang.Specification

/**
 * Integration tests for Grails content negotiation features.
 * 
 * Tests Accept header-based content negotiation, URL extension-based
 * format selection, respond method, withFormat, and various
 * content type handling scenarios.
 */
@Integration(applicationClass = Application)
@Narrative('''
Grails provides content negotiation that allows the same controller action
to return different response formats (JSON, XML, HTML) based on the client's
Accept header or URL extension.
''')
class ContentNegotiationSpec extends Specification {

    private HttpClient createClient() {
        HttpClient.create(new URL("http://localhost:$serverPort"))
    }

    // ========== Accept Header-Based Negotiation ==========

    def "JSON response via Accept header application/json"() {
        given:
        def client = createClient()

        when: "requesting with Accept: application/json"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/index')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('application/json')
        
        and: "content is valid JSON"
        def json = new JsonSlurper().parseText(response.body())
        json.message == 'Hello World'
        json.items.size() == 3

        cleanup:
        client.close()
    }

    def "XML response via Accept header application/xml"() {
        given:
        def client = createClient()

        when: "requesting with Accept: application/xml"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/index')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then: "response is XML"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('xml')
        
        and: "content contains expected XML elements (Grails XML converter uses entry key format for maps)"
        response.body().contains('<entry key="message">Hello World</entry>')

        cleanup:
        client.close()
    }

    def "HTML response via Accept header text/html"() {
        given:
        def client = createClient()

        when: "requesting with Accept: text/html"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/index')
                .accept(MediaType.TEXT_HTML),
            String
        )

        then: "response is HTML"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('text/html')
        
        and: "content is HTML page"
        response.body().contains('<h1>Content Negotiation</h1>')
        response.body().contains('Hello World')

        cleanup:
        client.close()
    }

    // ========== URL Extension-Based Negotiation ==========

    def "JSON response via .json extension"() {
        given:
        def client = createClient()

        when: "requesting URL with .json extension"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/index.json'),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        
        and: "content is valid JSON with expected data"
        def json = new JsonSlurper().parseText(response.body())
        json.message == 'Hello World'

        cleanup:
        client.close()
    }

    def "XML response via .xml extension"() {
        given:
        def client = createClient()

        when: "requesting URL with .xml extension"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/index.xml'),
            String
        )

        then: "response is XML"
        response.status == HttpStatus.OK
        
        and: "content is XML (Grails converter uses map/entry format)"
        response.body().contains('<entry key="message">')

        cleanup:
        client.close()
    }

    // ========== Respond Method Tests ==========

    def "respond method returns JSON for Accept application/json"() {
        given:
        def client = createClient()

        when: "calling respond action with Accept: application/json"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/respond')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        
        and: "content is valid JSON"
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'success'
        json.data.id == 1
        json.data.name == 'Test Item'

        cleanup:
        client.close()
    }

    def "respond method returns XML for Accept application/xml"() {
        given:
        def client = createClient()

        when: "calling respond action with Accept: application/xml"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/respond')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then: "response is XML"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('xml')

        cleanup:
        client.close()
    }

    // ========== List/Collection Content Negotiation ==========

    def "list action returns JSON array"() {
        given:
        def client = createClient()

        when: "requesting list with Accept: application/json"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/list')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "response is JSON array"
        response.status == HttpStatus.OK
        
        and: "content is array with 3 items"
        def json = new JsonSlurper().parseText(response.body())
        json instanceof List
        json.size() == 3
        json[0].id == 1
        json[0].name == 'Item 1'

        cleanup:
        client.close()
    }

    def "list action returns XML for XML accept"() {
        given:
        def client = createClient()

        when: "requesting list with Accept: application/xml"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/list')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then: "response is XML"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('xml')

        cleanup:
        client.close()
    }

    // ========== Explicit Content Type ==========

    def "explicit content type overrides negotiation"() {
        given:
        def client = createClient()

        when: "calling action with explicit content type"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/explicitContentType'),
            String
        )

        then: "response has explicit content type"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('application/json')
        
        and: "content is as specified"
        def json = new JsonSlurper().parseText(response.body())
        json.explicit == true

        cleanup:
        client.close()
    }

    // ========== Error Response Formatting ==========

    def "error response in JSON format"() {
        given:
        def client = createClient()

        when: "requesting error action with Accept: application/json"
        client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/error')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "error response is returned"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
        
        and: "error body is JSON"
        def json = new JsonSlurper().parseText(e.response.body().toString())
        json.error == true
        json.message == 'Something went wrong'
        json.code == 'ERR_001'

        cleanup:
        client.close()
    }

    def "error response in XML format"() {
        given:
        def client = createClient()

        when: "requesting error action with Accept: application/xml"
        client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/error')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then: "error response is returned"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
        
        and: "error body contains XML elements (Grails converter uses entry key format)"
        e.response.body().toString().contains('<entry key="error">true</entry>')

        cleanup:
        client.close()
    }

    // ========== Format Parameter Override ==========

    def "format parameter can specify JSON"() {
        given:
        def client = createClient()

        when: "requesting with format=json parameter"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/formatParam?format=json'),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        
        and: "format is recorded in response"
        def json = new JsonSlurper().parseText(response.body())
        json.format == 'json'
        json.value == 42

        cleanup:
        client.close()
    }

    def "format parameter can specify XML"() {
        given:
        def client = createClient()

        when: "requesting with format=xml parameter"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/formatParam?format=xml'),
            String
        )

        then: "response is XML"
        response.status == HttpStatus.OK
        response.body().contains('<entry key="format">xml</entry>')

        cleanup:
        client.close()
    }

    // ========== Status Code Tests ==========

    def "status by format returns correct status for JSON"() {
        given:
        def client = createClient()

        when: "requesting statusByFormat with Accept: application/json"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/statusByFormat')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "response status is OK"
        response.status == HttpStatus.OK
        
        and: "body is JSON"
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'ok'

        cleanup:
        client.close()
    }

    // ========== Accept Header Quality Values ==========

    def "multiAccept action handles Accept header"() {
        given:
        def client = createClient()

        when: "requesting with complex Accept header"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/multiAccept')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "response is successful"
        response.status == HttpStatus.OK
        
        and: "response contains accept header info"
        def json = new JsonSlurper().parseText(response.body())
        json.acceptHeader != null

        cleanup:
        client.close()
    }

    // ========== Wildcard/Default Format ==========

    def "formatParam falls back to default for unknown format"() {
        given:
        def client = createClient()

        when: "requesting with unknown format"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/formatParam?format=unknown'),
            String
        )

        then: "response uses default format (JSON)"
        response.status == HttpStatus.OK
        
        and: "response is valid JSON"
        def json = new JsonSlurper().parseText(response.body())
        json.format == 'unknown'
        json.value == 42

        cleanup:
        client.close()
    }

    // ========== Content-Type Header Variations ==========

    def "JSON with charset in Accept header"() {
        given:
        def client = createClient()

        when: "requesting with Accept including charset"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/respond')
                .header('Accept', 'application/json; charset=utf-8'),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('json')

        cleanup:
        client.close()
    }

    // ========== Multiple Format Extensions ==========

    def "respond action with .json extension"() {
        given:
        def client = createClient()

        when: "requesting respond with .json extension"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/respond.json'),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        
        and: "content is valid"
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'success'

        cleanup:
        client.close()
    }

    def "list action with .json extension"() {
        given:
        def client = createClient()

        when: "requesting list with .json extension"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/list.json'),
            String
        )

        then: "response is JSON array"
        response.status == HttpStatus.OK
        
        and: "content is array"
        def json = new JsonSlurper().parseText(response.body())
        json instanceof List
        json.size() == 3

        cleanup:
        client.close()
    }

    // ========== Custom JSON Rendering ==========

    def "custom JSON rendering produces valid output"() {
        given:
        def client = createClient()

        when: "requesting custom JSON action"
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/customJson'),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('application/json')

        cleanup:
        client.close()
    }
}
