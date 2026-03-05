/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.httpclient

import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.regex.Pattern

import javax.net.ssl.SSLSession

import org.opentest4j.AssertionFailedError
import spock.lang.Specification

class HttpResponseExtensionsSpec extends Specification {

    void 'expectStatus and expectNotStatus validate status codes'() {
        given:
        def response = mockResponse(200, 'ok')

        expect:
        response.expectStatus(200).is(response)
        response.expectNotStatus(404).is(response)

        when:
        response.expectStatus(404)

        then:
        thrown(AssertionFailedError)

        when:
        response.expectNotStatus(200)

        then:
        thrown(AssertionFailedError)
    }

    void 'expect and expectNot validate full body match'() {
        given:
        def response = mockResponse(200, 'alpha beta gamma')

        expect:
        response.expect('alpha beta gamma').is(response)
        response.expectNot('other').is(response)

        when:
        response.expect('alpha')

        then:
        thrown(AssertionFailedError)

        when:
        response.expectNot('alpha beta gamma')

        then:
        thrown(AssertionFailedError)
    }

    void 'expectContains and expectNotContains validate substring checks'() {
        given:
        def response = mockResponse(200, 'hello world')

        expect:
        response.expectContains('world').is(response)
        response.expectNotContains('missing').is(response)

        when:
        response.expectContains('mars')

        then:
        thrown(AssertionFailedError)

        when:
        response.expectNotContains('world')

        then:
        thrown(AssertionFailedError)
    }

    void 'expectMatches is full-body and expectContainsMatches is partial-body'() {
        given:
        def response = mockResponse(200, 'prefix 2026-03-05 suffix')

        expect:
        response.expectContainsMatches(~/\d{4}-\d{2}-\d{2}/).is(response)

        when:
        response.expectMatches(~/\d{4}-\d{2}-\d{2}/)

        then:
        thrown(AssertionFailedError)

        and:
        response.expectMatches(~/prefix \d{4}-\d{2}-\d{2} suffix/).is(response)
    }

    void 'expectNotMatches variants invert regex semantics'() {
        given:
        def response = mockResponse(200, 'abc-123')

        expect:
        response.expectNotContainsMatches(~/zzz/).is(response)
        response.expectNotMatches(~/\d+/).is(response)

        when:
        response.expectNotContainsMatches(~/\d+/)

        then:
        thrown(AssertionFailedError)

        when:
        response.expectNotMatches(~/abc-123/)

        then:
        thrown(AssertionFailedError)
    }

    void 'hasHeaderValueIgnoreCase and expectHeadersIgnoreCase support mixed header/value casing'() {
        given:
        def headers = [
                'Content-Type': ['Application/Json; Charset=UTF-8'],
                'X-Request-Id': ['ABC-123']
        ]
        def response = mockResponse(200, '{}', headers)

        expect:
        response.hasHeaderValueIgnoreCase('content-type', 'application/json; charset=utf-8')
        response.hasHeaderValueIgnoreCase('X-REQUEST-ID', 'abc-123')
        response.expectHeadersIgnoreCase(
                'content-type': 'application/json; charset=utf-8',
                'x-request-id': 'abc-123'
        ).is(response)

        when:
        response.expectHeadersIgnoreCase('x-request-id': 'different')

        then:
        thrown(AssertionFailedError)
    }

    void 'json, jsonList and xml parse response body'() {
        given:
        def jsonResponse = mockResponse(200, '{"a":1,"b":"two"}')
        def listResponse = mockResponse(200, '[1,2,3]')
        def xmlResponse = mockResponse(200, '<root><item>value</item></root>')

        expect:
        jsonResponse.json().a == 1
        jsonResponse.json().b == 'two'
        listResponse.jsonList() == [1, 2, 3]
        xmlResponse.xml().item.text() == 'value'
    }

    void 'header helper methods resolve values and presence'() {
        given:
        def headers = [
                'Content-Type': ['application/json'],
                'X-Rate-Limit': ['42'],
                'X-Request-Id': ['req-1']
        ]
        def response = mockResponse(200, '{}', headers)

        expect:
        response.headerValue('Content-Type') == 'application/json'
        response.contentType == 'application/json'
        response.headerValueAsLong('X-Rate-Limit') == 42L
        response.hasHeader('x-request-id')
        response.hasHeaderValue('X-Request-Id', 'req-1')
        !response.hasHeaderValue('X-Request-Id', 'REQ-1')
    }

    void 'expectHeaders and expectStatus with headers validate status and headers together'() {
        given:
        def response = mockResponse(201, 'created', ['X-Request-Id': ['abc-123']])

        expect:
        response.expectHeaders(['X-Request-Id': 'abc-123']).is(response)
        response.expectHeaders(['X-Request-Id': 'abc-123'], 201).is(response)
        response.expectStatus(['X-Request-Id': 'abc-123'], 201).is(response)

        when:
        response.expectHeaders(['X-Request-Id': 'different'])

        then:
        thrown(AssertionFailedError)

        when:
        response.expectStatus(['X-Request-Id': 'abc-123'], 200)

        then:
        thrown(AssertionFailedError)
    }

    void 'expectJson overloads assert JSON with optional status and headers'() {
        given:
        def body = '{"a":1,"nested":{"ok":true}}'
        def response = mockResponse(200, body, ['Content-Type': ['application/json']])

        expect:
        response.expectJson(body).is(response)
        response.expectJson([a: 1, nested: [ok: true]]).is(response)
        response.expectJson(['Content-Type': 'application/json'], 200, body).is(response)
        response.expectJson(['Content-Type': 'application/json'], 200, [a: 1, nested: [ok: true]]).is(response)

        when:
        response.expectJson([a: 2])

        then:
        thrown(AssertionFailedError)
    }

    void 'expectJsonContains overloads validate subset for status and headers'() {
        given:
        def response = mockResponse(200, '{"first":"first","second":null,"n":1}', ['X-Env': ['dev']])

        expect:
        response.expectJsonContains(200, [first: 'first']).is(response)
        response.expectJsonContains(200, [first: 'first', second: null]).is(response)
        response.expectJsonContains(['X-Env': 'dev'], [second: null]).is(response)
        response.expectJsonContains(['X-Env': 'dev'], 200, [n: 1]).is(response)

        when:
        response.expectJsonContains(200, [missing: true])

        then:
        thrown(AssertionError)
    }

    void 'regex helpers reject null patterns'() {
        given:
        def response = mockResponse(200, 'text')

        when:
        response.expectMatches((Pattern) null)

        then:
        thrown(IllegalArgumentException)

        when:
        response.expectContainsMatches((Pattern) null)

        then:
        thrown(IllegalArgumentException)

        when:
        response.expectNotMatches((Pattern) null)

        then:
        thrown(IllegalArgumentException)

        when:
        response.expectNotContainsMatches((Pattern) null)

        then:
        thrown(IllegalArgumentException)
    }

    private HttpResponse<String> mockResponse(
            int status,
            String bodyText,
            Map<String, List<String>> headerValues = Collections.emptyMap()
    ) {
        HttpHeaders httpHeaders = HttpHeaders.of(headerValues, { n, v -> true })
        [
                statusCode: { -> status },
                body: { -> bodyText },
                headers: { -> httpHeaders },
                request: { -> null as HttpRequest },
                previousResponse: { -> Optional.empty() as Optional<HttpResponse<String>> },
                sslSession: { -> Optional.empty() as Optional<SSLSession> },
                uri: { -> URI.create('http://localhost') },
                version: { -> HttpClient.Version.HTTP_1_1 }
        ] as HttpResponse<String>
    }
}
