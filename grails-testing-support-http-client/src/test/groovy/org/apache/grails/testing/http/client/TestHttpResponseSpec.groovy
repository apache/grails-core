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
package org.apache.grails.testing.http.client

import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.util.regex.Pattern

import javax.net.ssl.SSLSession

import groovy.json.JsonException
import groovy.json.JsonParserType
import groovy.xml.XmlSlurper

import org.opentest4j.AssertionFailedError
import org.xml.sax.SAXParseException
import spock.lang.Specification
import spock.lang.Unroll

class TestHttpResponseSpec extends Specification {

    void 'expectStatus and expectNotStatus validate status codes'() {
        given:
        def response = mockResponse(200, 'ok')

        expect:
        response.assertStatus(200).is(response)
        response.assertNotStatus(404).is(response)

        when:
        response.assertStatus(404)

        then:
        thrown(AssertionFailedError)

        when:
        response.assertNotStatus(200)

        then:
        thrown(AssertionFailedError)
    }

    void 'expect and expectNot validate full body match'() {
        given:
        def response = mockResponse(200, 'alpha beta gamma')

        expect:
        response.assertEquals('alpha beta gamma').is(response)
        response.assertNotEquals('other').is(response)

        when:
        response.assertEquals('alpha')

        then:
        thrown(AssertionFailedError)

        when:
        response.assertNotEquals('alpha beta gamma')

        then:
        thrown(AssertionFailedError)
    }

    void 'expectContains and expectNotContains validate substring checks'() {
        given:
        def response = mockResponse(200, 'hello world')

        expect:
        response.assertContains('world').is(response)
        response.assertNotContains('missing').is(response)

        when:
        response.assertContains('mars')

        then:
        thrown(AssertionFailedError)

        when:
        response.assertNotContains('world')

        then:
        thrown(AssertionFailedError)
    }

    void 'expectMatches is full-body and expectContainsMatches is partial-body'() {
        given:
        def response = mockResponse(200, 'prefix 2026-03-05 suffix')

        expect:
        response.assertContainsMatches(~/\d{4}-\d{2}-\d{2}/).is(response)

        when:
        response.assertMatches(~/\d{4}-\d{2}-\d{2}/)

        then:
        thrown(AssertionFailedError)

        and:
        response.assertMatches(~/prefix \d{4}-\d{2}-\d{2} suffix/).is(response)
    }

    void 'expectNotMatches variants invert regex semantics'() {
        given:
        def response = mockResponse(200, 'abc-123')

        expect:
        response.assertNotContainsMatches(~/zzz/).is(response)
        response.assertNotMatches(~/\d+/).is(response)

        when:
        response.assertNotContainsMatches(~/\d+/)

        then:
        thrown(AssertionFailedError)

        when:
        response.assertNotMatches(~/abc-123/)

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
        response.assertHeadersIgnoreCase(
                'content-type': 'application/json; charset=utf-8',
                'x-request-id': 'abc-123'
        ).is(response)

        when:
        response.assertHeadersIgnoreCase('x-request-id': 'different')

        then:
        thrown(AssertionFailedError)
    }

    void 'negative and case insensitive header overloads validate status and mismatches'() {
        given:
        def response = mockResponse(202, 'accepted', [
                'X-Request-Id': ['ABC-123'],
                'X-Mode'      : ['strict']
        ])

        expect:
        response.assertHeadersIgnoreCase(['x-request-id': 'abc-123'], 202).is(response)
        response.assertNotHeaders('X-Request-Id': 'different').is(response)

        when:
        response.assertHeadersIgnoreCase(['x-request-id': 'abc-123'], 200)

        then:
        thrown(AssertionFailedError)

        when:
        response.assertNotHeaders('X-Mode': 'strict')

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

    void 'xml uses a secure default slurper that does not resolve external entities'() {
        given:
        def secretFile = Files.createTempFile('test-http-response-xml', '.txt')
        Files.writeString(secretFile, 'top-secret-token')
        def uri = secretFile.toUri().toASCIIString()
        def response = mockResponse(200, """<!DOCTYPE root [
<!ENTITY ext SYSTEM '${uri}'>
]>
<root>&ext;</root>""")

        when:
        response.xml()

        then:
        def e = thrown(SAXParseException)
        e.message.contains('External Entity')

        cleanup:
        Files.deleteIfExists(secretFile)
    }

    void 'xml secure default still allows inline doctype declarations with internal entities'() {
        given:
        def response = mockResponse(200, '''<!DOCTYPE root [
<!ENTITY msg "safe">
]>
<root>&msg;</root>''')

        expect:
        response.xml().text() == 'safe'
    }

    void 'withXmlSlurper allows overriding the parser without mutating the original wrapper'() {
        given:
        def response = mockResponse(200, '<root><item>value</item></root>')
        int factoryCalls = 0

        when:
        def configured = response.withXmlSlurper(factory: {
            factoryCalls++
            new XmlSlurper()
        })

        then:
        response.xml().item.text() == 'value'
        factoryCalls == 0

        when:
        def parsed = configured.xml()

        then:
        parsed.item.text() == 'value'
        factoryCalls == 1
    }

    void 'previousResponse preserves custom xml slurper configuration'() {
        given:
        int factoryCalls = 0
        def previous = rawResponse(301, '<root><item>previous</item></root>')
        def response = TestHttpResponse.wrap(rawResponse(
                200,
                '<root><item>current</item></root>',
                Collections.emptyMap(),
                Optional.of(previous)
        )).withXmlSlurper(factory: {
            factoryCalls++
            new XmlSlurper()
        })

        when:
        def current = response.xml()
        def previousResponse = (TestHttpResponse) response.previousResponse().orElseThrow()
        def previousXml = previousResponse.xml()

        then:
        current.item.text() == 'current'
        previousXml.item.text() == 'previous'
        factoryCalls == 2
    }

    void 'json accessors throw ClassCastException for mismatched JSON shapes'() {
        given:
        def objectResponse = mockResponse(200, '{"a":1}')
        def listResponse = mockResponse(200, '[1,2,3]')

        when:
        objectResponse.jsonList()

        then:
        thrown(ClassCastException)

        when:
        listResponse.json()

        then:
        thrown(ClassCastException)
    }

    void 'withJsonSlurper allows opt-in lax parsing for json access and assertions'() {
        given:
        def body = "{name:'Widget', nested:{ok:true}}"
        def response = mockResponse(200, body, ['Content-Type': ['application/json']])
        def laxResponse = response.withJsonSlurper(parserType: JsonParserType.LAX)

        when: 'using the default parser'
        response.json()

        then:
        thrown(JsonException)

        when: 'using an opt-in lax parser'
        def parsed = laxResponse.json()

        then:
        parsed.name == 'Widget'
        parsed.nested.ok
        laxResponse.assertJson(body).is(laxResponse)
        laxResponse.assertJsonContains('{nested:{ok:true}}').is(laxResponse)

        when: 'the original wrapper remains unchanged'
        response.assertJson(body)

        then:
        thrown(JsonException)
    }

    void 'withJsonSlurper accepts additional JsonSlurper options'() {
        given:
        def body = "{name:'Widget'}"
        def strictBody = '{"name":"Widget"}'
        def response = mockResponse(200, body, ['Content-Type': ['application/json']])
        def configuredResponse = response.withJsonSlurper(
                parserType: JsonParserType.LAX,
                checkDates: true,
                chop: true,
                lazyChop: true,
                maxSizeForInMemory: 64
        )

        expect:
        configuredResponse.json().name == 'Widget'
        configuredResponse.assertJson(body).is(configuredResponse)
        configuredResponse.assertJson(strictBody).is(configuredResponse)
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
        response.getContentType() == 'application/json'
        response.headerValueAsLong('X-Rate-Limit') == 42L
        response.hasHeader('x-request-id')
        response.hasHeaderValue('X-Request-Id', 'req-1')
        !response.hasHeaderValue('X-Request-Id', 'REQ-1')
    }

    @Unroll
    void 'getContentType returns #contentType'(Map<String, List<String>> headers, String contentType) {
        given:
        def withContentType = mockResponse(200, '{}', headers)

        expect:
        withContentType.getContentType() == contentType

        where:
        headers                                               || contentType
        ['Content-Type': ['application/json; charset=UTF-8']] || 'application/json; charset=UTF-8'
        ['content-type': ['text/plain']]                      || 'text/plain'
        ['Location':     ['whatever']]                        || null
    }

    void 'expectHeaders and expectStatus with headers validate status and headers together'() {
        given:
        def response = mockResponse(201, 'created', ['X-Request-Id': ['abc-123']])

        expect:
        response.assertHeaders('X-Request-Id': 'abc-123').is(response)
        response.assertHeaders('X-Request-Id': 'abc-123', 201).is(response)
        response.assertStatus('X-Request-Id': 'abc-123', 201).is(response)

        when:
        response.assertHeaders('X-Request-Id': 'different')

        then:
        thrown(AssertionFailedError)

        when:
        response.assertStatus('X-Request-Id': 'abc-123', 200)

        then:
        thrown(AssertionFailedError)
    }

    void 'expectJson overloads assert JSON with optional status and headers'() {
        given:
        def body = '{"a":1,"nested":{"ok":true}}'
        def response = mockResponse(200, body, ['Content-Type': ['application/json']])

        expect:
        response.assertJson(body).is(response)
        response.assertJson([a: 1, nested: [ok: true]]).is(response)
        response.assertJson(200, 'Content-Type': 'application/json', body).is(response)
        response.assertJson('Content-Type': 'application/json', 200, [a: 1, nested: [ok: true]]).is(response)

        when:
        response.assertJson([a: 2])

        then:
        thrown(AssertionFailedError)
    }

    void 'expectJsonContains overloads validate subset for status and headers'() {
        given:
        def response = mockResponse(200, '{"first":"first","second":null,"n":1}', ['X-Env': ['dev']])

        expect:
        response.assertJsonContains(200, [first: 'first']).is(response)
        response.assertJsonContains(200, [first: 'first', second: null]).is(response)
        response.assertJsonContains('X-Env': 'dev', [second: null]).is(response)
        response.assertJsonContains(['X-Env': 'dev'], 200, [n: 1]).is(response)
        response.assertJsonContains('{"first":"first"}').is(response)
        response.assertJsonContains(200, '{"second":null}').is(response)

        when:
        response.assertJsonContains(200, [missing: true])

        then:
        thrown(AssertionError)

        when:
        response.assertJsonContains('{"missing":true}')

        then:
        thrown(AssertionError)
    }

    void 'status bearing negative text and regex overloads are exercised explicitly'() {
        given:
        def response = mockResponse(200, 'prefix 2026-03-05 suffix', ['X-Env': ['dev']])

        expect:
        response.assertMatches(200, ~/prefix \d{4}-\d{2}-\d{2} suffix/).is(response)
        response.assertNotEquals(200, 'something else').is(response)
        response.assertNotContains(200, 'missing').is(response)
        response.assertNotContainsMatches(200, ~/zzz/).is(response)
        response.assertNotMatches(200, ~/\d{4}-\d{2}-\d{2}/).is(response)

        when:
        response.assertMatches(200, ~/\d{4}-\d{2}-\d{2}/)

        then:
        thrown(AssertionFailedError)

        when:
        response.assertNotEquals(200, 'prefix 2026-03-05 suffix')

        then:
        thrown(AssertionFailedError)

        when:
        response.assertNotContains(200, '2026-03-05')

        then:
        thrown(AssertionFailedError)

        when:
        response.assertNotContainsMatches(200, ~/\d{4}-\d{2}-\d{2}/)

        then:
        thrown(AssertionFailedError)

        when:
        response.assertNotMatches(200, ~/prefix \d{4}-\d{2}-\d{2} suffix/)

        then:
        thrown(AssertionFailedError)
    }

    void 'regex helpers reject null patterns'() {
        given:
        def response = mockResponse(200, 'text')

        when:
        response.assertMatches((Pattern) null)

        then:
        thrown(IllegalArgumentException)

        when:
        response.assertContainsMatches((Pattern) null)

        then:
        thrown(IllegalArgumentException)

        when:
        response.assertNotMatches((Pattern) null)

        then:
        thrown(IllegalArgumentException)

        when:
        response.assertNotContainsMatches((Pattern) null)

        then:
        thrown(IllegalArgumentException)
    }

    private static TestHttpResponse mockResponse(
            int status,
            String bodyText,
            Map<String, List<String>> headerValues = Collections.emptyMap()
    ) {
        TestHttpResponse.wrap(rawResponse(status, bodyText, headerValues))
    }

    private static HttpResponse<String> rawResponse(
            int status,
            String bodyText,
            Map<String, List<String>> headerValues = Collections.emptyMap(),
            Optional<HttpResponse<String>> previousResponse = Optional.empty()
    ) {
        HttpHeaders httpHeaders = HttpHeaders.of(headerValues, { n, v -> true })
        [
                statusCode: { -> status },
                body: { -> bodyText },
                headers: { -> httpHeaders },
                request: { -> null as HttpRequest },
                previousResponse: { -> previousResponse },
                sslSession: { -> Optional.empty() as Optional<SSLSession> },
                uri: { -> URI.create('http://localhost') },
                version: { -> HttpClient.Version.HTTP_1_1 }
        ] as HttpResponse<String>
    }
}
