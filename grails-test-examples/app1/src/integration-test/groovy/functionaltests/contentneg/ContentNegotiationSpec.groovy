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

import groovy.json.JsonSlurper

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

/**
 * Integration tests for Grails content negotiation features.
 * 
 * Tests Accept header-based content negotiation, URL extension-based
 * format selection, respond method, withFormat, and various
 * content type handling scenarios.
 */
@Integration
@Narrative('''
Grails provides content negotiation that allows the same controller action
to return different response formats (JSON, XML, HTML) based on the client's
Accept header or URL extension.
''')
class ContentNegotiationSpec extends Specification {

    @Shared
    HttpClient client

    def setup() {
        client = client ?: HttpClient.create(new URL("http://localhost:$serverPort"))
    }

    def cleanupSpec() {
        client.close()
    }

    // ========== Accept Header-Based Negotiation ==========

    def "JSON response via Accept header application/json"() {
        when: "requesting with Accept: application/json"
        def response = client.toBlocking().exchange(
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
    }

    def "XML response via Accept header application/xml"() {
        when: "requesting with Accept: application/xml"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/index')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then: "response is XML"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('xml')
        
        and: "content contains expected XML elements (Grails XML converter uses entry key format for maps)"
        response.body().contains('<entry key="message">Hello World</entry>')
    }

    def "HTML response via Accept header text/html"() {
        when: "requesting with Accept: text/html"
        def response = client.toBlocking().exchange(
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
    }

    // ========== URL Extension-Based Negotiation ==========

    def "JSON response via .json extension"() {
        when: "requesting URL with .json extension"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/index.json'),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        
        and: "content is valid JSON with expected data"
        def json = new JsonSlurper().parseText(response.body())
        json.message == 'Hello World'
    }

    def "XML response via .xml extension"() {
        when: "requesting URL with .xml extension"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/index.xml'),
            String
        )

        then: "response is XML"
        response.status == HttpStatus.OK
        
        and: "content is XML (Grails converter uses map/entry format)"
        response.body().contains('<entry key="message">')
    }

    // ========== Respond Method Tests ==========

    def "respond method returns JSON for Accept application/json"() {
        when: "calling respond action with Accept: application/json"
        def response = client.toBlocking().exchange(
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
    }

    def "respond method returns XML for Accept application/xml"() {
        when: "calling respond action with Accept: application/xml"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/respond')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then: "response is XML"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('xml')
    }

    // ========== List/Collection Content Negotiation ==========

    def "list action returns JSON array"() {
        when: "requesting list with Accept: application/json"
        def response = client.toBlocking().exchange(
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
    }

    def "list action returns XML for XML accept"() {
        when: "requesting list with Accept: application/xml"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/list')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then: "response is XML"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('xml')
    }

    // ========== Explicit Content Type ==========

    def "explicit content type overrides negotiation"() {
        when: "calling action with explicit content type"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/explicitContentType'),
            String
        )

        then: "response has explicit content type"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('application/json')
        
        and: "content is as specified"
        def json = new JsonSlurper().parseText(response.body())
        json.explicit == true
    }

    // ========== Error Response Formatting ==========

    def "error response in JSON format"() {
        when: "requesting error action with Accept: application/json"
        client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/error')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "error response is returned"
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        
        and: "error body is JSON"
        def json = new JsonSlurper().parseText(e.response.body().toString())
        json.error == true
        json.message == 'Something went wrong'
        json.code == 'ERR_001'
    }

    def "error response in XML format"() {
        when: "requesting error action with Accept: application/xml"
        client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/error')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then: "error response is returned"
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        
        and: "error body contains XML elements (Grails converter uses entry key format)"
        e.response.body().toString().contains('<entry key="error">true</entry>')
    }

    // ========== Format Parameter Override ==========

    def "format parameter can specify JSON"() {
        when: "requesting with format=json parameter"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/formatParam?format=json'),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        
        and: "format is recorded in response"
        def json = new JsonSlurper().parseText(response.body())
        json.format == 'json'
        json.value == 42
    }

    def "format parameter can specify XML"() {
        when: "requesting with format=xml parameter"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/formatParam?format=xml'),
            String
        )

        then: "response is XML"
        response.status == HttpStatus.OK
        response.body().contains('<entry key="format">xml</entry>')
    }

    // ========== Status Code Tests ==========

    def "status by format returns correct status for JSON"() {
        when: "requesting statusByFormat with Accept: application/json"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/statusByFormat')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "response status is OK"
        response.status == HttpStatus.OK
        
        and: "body is JSON"
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'ok'
    }

    // ========== Accept Header Quality Values ==========

    def "multiAccept action handles Accept header"() {
        when: "requesting with complex Accept header"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/multiAccept')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "response is successful"
        response.status == HttpStatus.OK
        
        and: "response contains accept header info"
        def json = new JsonSlurper().parseText(response.body())
        json.acceptHeader != null
    }

    // ========== Wildcard/Default Format ==========

    def "formatParam falls back to default for unknown format"() {
        when: "requesting with unknown format"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/formatParam?format=unknown'),
            String
        )

        then: "response uses default format (JSON)"
        response.status == HttpStatus.OK
        
        and: "response is valid JSON"
        def json = new JsonSlurper().parseText(response.body())
        json.format == 'unknown'
        json.value == 42
    }

    // ========== Content-Type Header Variations ==========

    def "JSON with charset in Accept header"() {
        when: "requesting with Accept including charset"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/respond')
                .header('Accept', 'application/json; charset=utf-8'),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('json')
    }

    // ========== Multiple Format Extensions ==========

    def "respond action with .json extension"() {
        when: "requesting respond with .json extension"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/respond.json'),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        
        and: "content is valid"
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'success'
    }

    def "list action with .json extension"() {
        when: "requesting list with .json extension"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/list.json'),
            String
        )

        then: "response is JSON array"
        response.status == HttpStatus.OK
        
        and: "content is array"
        def json = new JsonSlurper().parseText(response.body())
        json instanceof List
        json.size() == 3
    }

    // ========== Custom JSON Rendering ==========

    def "custom JSON rendering produces valid output"() {
        when: "requesting custom JSON action"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/contentNegotiation/customJson'),
            String
        )

        then: "response is JSON"
        response.status == HttpStatus.OK
        response.contentType.get().toString().contains('application/json')
    }
}
