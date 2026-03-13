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
package functionaltests.requestresponse

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Integration tests for request/response handling patterns including
 * headers, cookies, session management, and request attributes.
 */
@Integration
class RequestResponseSpec extends Specification implements HttpClientSupport {

    // ========== Request Header Tests ==========

    def "echo request headers returns all headers"() {
        when:
        def response = http('/requestResponseTest/echoHeaders',
                'X-Custom-Header': 'TestValue',
                'X-Another-Header': 'AnotherValue'
        )

        then:
        response.expectStatus(200)
        def headers = response.json().headers as Map<String, String>
        findHeader(headers, 'X-Custom-Header') == 'TestValue'
        findHeader(headers, 'X-Another-Header') == 'AnotherValue'
    }

    def "get specific header returns correct value"() {
        when:
        def response = http(
                '/requestResponseTest/getSpecificHeader?headerName=X-Test-Header',
                'X-Test-Header': 'MyTestValue'
        )

        then:
        response.expectJsonContains(200, [headerValue: 'MyTestValue'])
    }

    def "check accept header detects JSON accept type"() {
        when:
        def response = http(
                '/requestResponseTest/checkAcceptHeader',
                'Accept': 'application/json'
        )

        then:
        response.expectJsonContains(200, [acceptsJson: true])
    }

    // ========== Response Header Tests ==========

    def "set custom headers returns headers in response"() {
        when:
        def response = http('/requestResponseTest/setCustomHeaders')

        then:
        response.expectHeaders(200, 'X-Custom-Header': 'CustomValue')
        response.hasHeader('X-Request-Id')
        response.hasHeader('X-Timestamp')
    }

    def "set cache headers configures caching properly"() {
        when:
        def response = http('/requestResponseTest/setCacheHeaders')

        then:
        response.expectHeaders(200,
                'Cache-Control': 'max-age=3600, public',
                'ETag': '"abc123"'
        )
        response.hasHeader('Last-Modified')
    }

    def "set no-cache headers prevents caching"() {
        when:
        def response = http('/requestResponseTest/setNoCacheHeaders')

        then:
        response.expectHeaders(200,
                'Cache-Control': 'no-cache, no-store, must-revalidate',
                'Pragma': 'no-cache',
                'Expires': '0'
        )
    }

    def "set content disposition for file download"() {
        when:
        def response = http('/requestResponseTest/setContentDisposition')

        then:
        response.expectHeaders(200, 'Content-Disposition': 'attachment; filename="report.pdf"')
    }

    def "set multiple custom headers returns all headers"() {
        when:
        def response = http('/requestResponseTest/setMultipleCustomHeaders')

        then:
        response.expectHeaders(200,
                'X-Custom-0': 'Value-0',
                'X-Custom-1': 'Value-1',
                'X-Custom-4': 'Value-4'
        )
    }

    // ========== Cookie Tests ==========

    def "set cookie returns Set-Cookie header"() {
        when:
        def response = http('/requestResponseTest/setCookie?name=myCookie&value=myValue')

        then:
        response.expectJsonContains(200, [
                cookieSet: true,
                name: 'myCookie',
                value: 'myValue'
        ])
        response.headerValue('Set-Cookie')?.contains('myCookie=myValue')
    }

    def "set multiple cookies returns multiple Set-Cookie headers"() {
        when:
        def response = http('/requestResponseTest/setMultipleCookies')

        then:
        response.expectJsonContains(200, [cookiesSet: 3])
        response.headers().allValues('Set-Cookie').size() >= 3
    }

    def "get cookies reads cookies from request"() {
        when:
        def response = sendHttpRequest(httpRequestWith('/requestResponseTest/getCookies') {
            header('Cookie', 'testCookie1=value1; testCookie2=value2')
        })

        then:
        response.expectJson(200, [
                cookies: [
                        testCookie1: 'value1',
                        testCookie2: 'value2'
                ]
        ])
    }

    def "get specific cookie returns correct cookie value"() {
        when:
        def response = sendHttpRequest(httpRequestWith('/requestResponseTest/getSpecificCookie?name=myCookie') {
            header('Cookie', 'myCookie=cookieValue')
        })

        then:
        response.expectJson(200, [
                found: true,
                name: 'myCookie',
                value: 'cookieValue'
        ])
    }

    def "delete cookie sets max-age to 0"() {
        when:
        def response = http('/requestResponseTest/deleteCookie?name=deletedCookie')

        then:
        response.expectJsonContains(200, [deleted: 'deletedCookie'])
        response.headers().allValues('Set-Cookie')?.find { it.contains('Max-Age=0') } ||
                response.headers().allValues('Set-Cookie')?.find { it.contains('Expires=') }
    }

    // ========== Session Tests ==========

    def "set session attribute stores value in session"() {
        when:
        def response = http(
                '/requestResponseTest/setSessionAttribute?key=testKey&value=testValue'
        )

        then:
        response.expectStatus(200)
        with(response.json()) {
            key == 'testKey'
            value == 'testValue'
            sessionId != null
        }
    }

    def "get session attribute retrieves stored value"() {
        given:
        // First set a session attribute
        def setResponse = http(
            '/requestResponseTest/setSessionAttribute?key=retrieveKey&value=retrieveValue'
        )
        def sessionCookie = setResponse.headerValue('Set-Cookie')

        when:
        // Then retrieve it with the same session
        def response = sendHttpRequest(httpRequestWith('/requestResponseTest/getSessionAttribute?key=retrieveKey') {
            if (sessionCookie) {
                def cookieValue = sessionCookie.split(';').first()
                header('Cookie', cookieValue)
            }
            GET()
        })

        then:
        response.expectJsonContains(200, [
                key: 'retrieveKey',
                value: 'retrieveValue',
                found: true
        ])
    }

    def "session counter increments on each request"() {
        given:
        // First request
        def response1 = http('/requestResponseTest/sessionCounter')
        def json1 = response1.json()
        def sessionCookie = response1.headerValue('Set-Cookie')

        when:
        // Second request with same session
        def response2 = sendHttpRequest(httpRequestWith('/requestResponseTest/sessionCounter') {
            if (sessionCookie) {
                def cookieValue = sessionCookie.split(';').first()
                header('Cookie', cookieValue)
            }
            GET()
        })
        def json2 = response2.json()

        then:
        response1.expectStatus(200)
        response2.expectStatus(200)
        json1.count == 1
        json2.count == 2
    }

    // ========== Request Info Tests ==========

    def "get request info returns server details"() {
        when:
        def response = http('/requestResponseTest/getRequestInfo')

        then:
        response.expectJsonContains(200, [
                method: 'GET',
                uri: '/requestResponseTest/getRequestInfo',
                scheme: 'http',
                serverPort: serverPort
        ])
    }

    def "get request parameters returns query parameters"() {
        when:
        def response = http(
                '/requestResponseTest/getRequestParameters?param1=value1&param2=value2'
        )

        then:
        response.expectJson(200, [
                parameters: [
                        param1: 'value1',
                        param2: 'value2'
                ]
        ])
    }

    def "set request attribute stores and retrieves value"() {
        when:
        def response = http('/requestResponseTest/setRequestAttribute?key=myAttr&value=myVal')

        then:
        response.expectJson(200, [
                key: 'myAttr',
                setValue: 'myVal',
                retrievedValue: 'myVal'
        ])
    }

    // ========== Content Type and Encoding Tests ==========

    def "unicode response returns characters in multiple languages"() {
        when:
        def response = http('/requestResponseTest/unicodeResponse')

        then:
        response.expectJson(200, [
                arabic: 'مرحبا بالعالم',
                english: 'Hello World',
                chinese: '你好世界',
                japanese: 'こんにちは世界',
                korean: '안녕하세요 세계',
                emoji: '👋🌍🎉'
        ])
    }

    /**
     * Helper method to find echoed header value in json response case-insensitively.
     * HTTP headers are case-insensitive per spec.
     */
    private String findHeader(Map<String, String> headers, String name) {
        def entry = headers.find { k, v -> k.equalsIgnoreCase(name) }
        return entry?.value
    }
}
