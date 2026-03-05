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

import spock.lang.Narrative
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

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
class ContentNegotiationSpec extends Specification implements HttpClientSupport {

    // ========== Accept Header-Based Negotiation ==========

    def "JSON response via Accept header application/json"() {
        when: "requesting with Accept: application/json"
        def response = http('/contentNegotiation/index', 'Accept': 'application/json')

        then: "response is JSON"
        response.expectStatus(200).contentType.contains('application/json')

        and: "content is valid JSON"
        with(response.json()) {
            message == 'Hello World'
            items.size() == 3
        }
    }

    def "XML response via Accept header application/xml"() {
        when: "requesting with Accept: application/xml"
        def response = http('/contentNegotiation/index', 'Accept': 'application/xml')

        then: "response is XML"
        response.expectStatus(200).contentType.contains('xml')

        and: "content contains expected XML elements (Grails XML converter uses entry key format for maps)"
        response.expectContains('<entry key="message">Hello World</entry>')
    }

    def "HTML response via Accept header text/html"() {
        when: "requesting with Accept: text/html"
        def response = http('/contentNegotiation/index', 'Accept': 'text/html')

        then: "response is HTML"
        response.expectStatus(200).contentType.contains('text/html')
        
        and: "content is HTML page"
        response.expectContains('<h1>Content Negotiation</h1>')
                .expectContains('Hello World')
    }

    // ========== URL Extension-Based Negotiation ==========

    def "JSON response via .json extension"() {
        when: "requesting URL with .json extension"
        def response = http('/contentNegotiation/index.json')

        then: "response is JSON"
        response.expectStatus(200).contentType.contains('json')
        
        and: "content is valid JSON with expected data"
        response.expectJsonContains([message: 'Hello World'])
    }

    def "XML response via .xml extension"() {
        when: "requesting URL with .xml extension"
        def response = http('/contentNegotiation/index.xml')

        then: "response is XML"
        response.expectStatus(200).contentType.contains('xml')
        
        and: "content is XML (Grails converter uses map/entry format)"
        response.expectContains('<entry key="message">')
    }

    // ========== Respond Method Tests ==========

    def "respond method returns JSON for Accept application/json"() {
        when: "calling respond action with Accept: application/json"
        def response = http('/contentNegotiation/respond','Accept': 'application/json')

        then: "response is JSON"
        response.expectStatus(200).contentType.contains('json')
        
        and: "content is valid JSON"
        response.expectJsonContains([
                status: 'success',
                data: [id: 1, name: 'Test Item']
        ])
    }

    def "respond method returns XML for Accept application/xml"() {
        when: "calling respond action with Accept: application/xml"
        def response = http('/contentNegotiation/respond', 'Accept': 'application/xml')

        then: "response is XML"
        response.expectStatus(200).contentType.contains('xml')
    }

    // ========== List/Collection Content Negotiation ==========

    def "list action returns JSON array"() {
        when: "requesting list with Accept: application/json"
        def response = http('/contentNegotiation/list', 'Accept': 'application/json')

        then: "response is JSON array"
        response.expectStatus(200).contentType.contains('json')
        
        and: "content is array with 3 items"
        with(response.jsonList()) {
            size() == 3
            first().id == 1
            first().name == 'Item 1'
        }
    }

    def "list action returns XML for XML accept"() {
        when: "requesting list with Accept: application/xml"
        def response = http('/contentNegotiation/list', 'Accept': 'application/xml')

        then: "response is XML"
        response.expectStatus(200).contentType.contains('xml')
    }

    // ========== Explicit Content Type ==========

    def "explicit content type overrides negotiation"() {
        when: "calling action with explicit content type"
        def response = http('/contentNegotiation/explicitContentType')

        then: "response has explicit content type"
        response.expectStatus(200).contentType.contains('application/json')
        
        and: "content is as specified"
        response.expectJson([explicit: true])
    }

    // ========== Error Response Formatting ==========

    def "error response in JSON format"() {
        when: "requesting error action with Accept: application/json"
        def response = http('/contentNegotiation/error', 'Accept': 'application/json')

        then: "error response is returned"
        response.expectStatus(400)
        
        and: "error body is JSON"
        response.expectJson([
                error: true,
                message: 'Something went wrong',
                code: 'ERR_001'
        ])
    }

    def "error response in XML format"() {
        when: "requesting error action with Accept: application/xml"
        def response = http('/contentNegotiation/error', 'Accept': 'application/xml')

        then: "error response is returned"
        response.expectStatus(400)
        
        and: "error body contains XML elements (Grails converter uses entry key format)"
        response.expectContains('<entry key="error">true</entry>')
    }

    // ========== Format Parameter Override ==========

    def "format parameter can specify JSON"() {
        when: "requesting with format=json parameter"
        def response = http('/contentNegotiation/formatParam?format=json')

        then: "response is JSON"
        response.expectStatus(200)
        
        and: "format is recorded in response"
        response.expectJson([
            format: 'json',
            value: 42
        ])
    }

    def "format parameter can specify XML"() {
        when: "requesting with format=xml parameter"
        def response = http('/contentNegotiation/formatParam?format=xml')

        then: "response is XML"
        response.expectContains(200, '<entry key="format">xml</entry>')
    }

    // ========== Status Code Tests ==========

    def "status by format returns correct status for JSON"() {
        when: "requesting statusByFormat with Accept: application/json"
        def response = http('/contentNegotiation/statusByFormat', 'Accept': 'application/json')

        then: "response status is OK"
        response.expectStatus(200)
        
        and: "body is JSON"
        response.expectJson([status: 'ok'])
    }

    // ========== Accept Header Quality Values ==========

    def "multiAccept action handles Accept header"() {
        when: "requesting with complex Accept header"
        def response = http('/contentNegotiation/multiAccept', 'Accept': 'application/json')

        then: "response is successful"
        response.expectStatus(200)
        
        and: "response contains accept header info"
        response.expectJson([
                acceptHeader: 'application/json',
                negotiated: 'json'
        ])
    }

    // ========== Wildcard/Default Format ==========

    def "formatParam falls back to default for unknown format"() {
        when: "requesting with unknown format"
        def response = http('/contentNegotiation/formatParam?format=unknown')

        then: "response uses default format (JSON)"
        response.expectStatus(200)
        
        and: "response is valid JSON"
        response.expectJson([
                format: 'unknown',
                value: 42
        ])
    }

    // ========== Content-Type Header Variations ==========

    def "JSON with charset in Accept header"() {
        when: "requesting with Accept including charset"
        def response = http(
                '/contentNegotiation/respond',
                'Accept': 'application/json; charset=utf-8'
        )

        then: "response is JSON"
        response.expectStatus(200).contentType.contains('json')
    }

    // ========== Multiple Format Extensions ==========

    def "respond action with .json extension"() {
        when: "requesting respond with .json extension"
        def response = http('/contentNegotiation/respond.json')

        then: "response is JSON"
        response.expectStatus(200).contentType.contains('json')
        
        and: "content is valid"
        response.expectJsonContains([status: 'success'])
    }

    def "list action with .json extension"() {
        when: "requesting list with .json extension"
        def response = http('/contentNegotiation/list.json')

        then: "response is JSON array"
        response.expectStatus(200).contentType.contains('json')
        
        and: "content is array"
        response.jsonList().size() == 3
    }

    // ========== Custom JSON Rendering ==========

    def "custom JSON rendering produces valid output"() {
        when: "requesting custom JSON action"
        def response = http('/contentNegotiation/customJson')

        then: "response is JSON"
        response.expectStatus(200).contentType.contains('application/json')
    }
}
