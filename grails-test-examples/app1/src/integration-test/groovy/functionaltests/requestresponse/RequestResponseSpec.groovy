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

import groovy.json.JsonSlurper

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.cookie.Cookie
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

/**
 * Integration tests for request/response handling patterns including
 * headers, cookies, session management, and request attributes.
 */
@Integration
class RequestResponseSpec extends Specification implements HttpClientSupport {

    /**
     * Helper method to find header value case-insensitively.
     * HTTP headers are case-insensitive per spec.
     */
    private String findHeader(Map headers, String name) {
        def entry = headers.find { k, v -> k.equalsIgnoreCase(name) }
        return entry?.value
    }

    // ========== Request Header Tests ==========

    def "echo request headers returns all headers"() {
        when:
        def response = httpClient.exchange(
            HttpRequest.GET('/requestResponseTest/echoHeaders')
                .header('X-Custom-Header', 'TestValue')
                .header('X-Another-Header', 'AnotherValue'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        findHeader(json.headers, 'X-Custom-Header') == 'TestValue'
        findHeader(json.headers, 'X-Another-Header') == 'AnotherValue'
    }

    def "get specific header returns correct value"() {
        when:
        def response = httpClient.exchange(
            HttpRequest.GET('/requestResponseTest/getSpecificHeader?headerName=X-Test-Header')
                .header('X-Test-Header', 'MyTestValue'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        // Header name might be normalized/lowercased
        json.headerValue == 'MyTestValue'
    }

    def "check accept header detects JSON accept type"() {
        when:
        def response = httpClient.exchange(
            HttpRequest.GET('/requestResponseTest/checkAcceptHeader')
                .accept('application/json'),
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.acceptsJson == true
    }

    // ========== Response Header Tests ==========

    def "set custom headers returns headers in response"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/setCustomHeaders',
            String
        )

        then:
        response.status == HttpStatus.OK
        response.header('X-Custom-Header') == 'CustomValue'
        response.header('X-Request-Id') != null
        response.header('X-Timestamp') != null
    }

    def "set cache headers configures caching properly"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/setCacheHeaders',
            String
        )

        then:
        response.status == HttpStatus.OK
        response.header('Cache-Control') == 'max-age=3600, public'
        response.header('ETag') == '"abc123"'
        response.header('Last-Modified') != null
    }

    def "set no-cache headers prevents caching"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/setNoCacheHeaders',
            String
        )

        then:
        response.status == HttpStatus.OK
        response.header('Cache-Control') == 'no-cache, no-store, must-revalidate'
        response.header('Pragma') == 'no-cache'
        response.header('Expires') == '0'
    }

    def "set content disposition for file download"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/setContentDisposition',
            String
        )

        then:
        response.status == HttpStatus.OK
        response.header('Content-Disposition') == 'attachment; filename="report.pdf"'
    }

    def "set multiple custom headers returns all headers"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/setMultipleCustomHeaders',
            String
        )

        then:
        response.status == HttpStatus.OK
        response.header('X-Custom-0') == 'Value-0'
        response.header('X-Custom-1') == 'Value-1'
        response.header('X-Custom-4') == 'Value-4'
    }

    // ========== Cookie Tests ==========

    def "set cookie returns Set-Cookie header"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/setCookie?name=myCookie&value=myValue',
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.cookieSet == true
        json.name == 'myCookie'
        json.value == 'myValue'
        response.header('Set-Cookie')?.contains('myCookie=myValue')
    }

    def "set multiple cookies returns multiple Set-Cookie headers"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/setMultipleCookies',
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.cookiesSet == 3
        response.headers.getAll('Set-Cookie').size() >= 3
    }

    def "get cookies reads cookies from request"() {
        when:
        def request = HttpRequest.GET('/requestResponseTest/getCookies')
            .cookie(Cookie.of('testCookie1', 'value1'))
            .cookie(Cookie.of('testCookie2', 'value2'))
        def response = httpClient.exchange(request, String)
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.cookies['testCookie1'] == 'value1'
        json.cookies['testCookie2'] == 'value2'
    }

    def "get specific cookie returns correct cookie value"() {
        when:
        def request = HttpRequest.GET('/requestResponseTest/getSpecificCookie?name=myCookie')
            .cookie(Cookie.of('myCookie', 'cookieValue'))
        def response = httpClient.exchange(request, String)
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.found == true
        json.name == 'myCookie'
        json.value == 'cookieValue'
    }

    def "delete cookie sets max-age to 0"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/deleteCookie?name=deletedCookie',
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.deleted == 'deletedCookie'
        response.header('Set-Cookie')?.contains('Max-Age=0') || response.header('Set-Cookie')?.contains('Expires=')
    }

    // ========== Session Tests ==========

    def "set session attribute stores value in session"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/setSessionAttribute?key=testKey&value=testValue',
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.key == 'testKey'
        json.value == 'testValue'
        json.sessionId != null
    }

    def "get session attribute retrieves stored value"() {
        given:
        // First set a session attribute
        def setResponse = httpClient.exchange(
            '/requestResponseTest/setSessionAttribute?key=retrieveKey&value=retrieveValue',
            String
        )
        def sessionCookie = setResponse.header('Set-Cookie')

        when:
        // Then retrieve it with the same session
        def getRequest = HttpRequest.GET(
                '/requestResponseTest/getSessionAttribute?key=retrieveKey'
        )
        if (sessionCookie) {
            def cookieValue = sessionCookie.split(';')[0]
            getRequest = getRequest.header('Cookie', cookieValue)
        }
        def response = httpClient.exchange(getRequest, String)
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.key == 'retrieveKey'
        json.value == 'retrieveValue'
        json.found == true
    }

    def "session counter increments on each request"() {
        given:
        // First request
        def response1 = httpClient.exchange(
            '/requestResponseTest/sessionCounter',
            String
        )
        def json1 = new JsonSlurper().parseText(response1.body())
        def sessionCookie = response1.header('Set-Cookie')

        when:
        // Second request with same session
        def request2 = HttpRequest.GET('/requestResponseTest/sessionCounter')
        if (sessionCookie) {
            def cookieValue = sessionCookie.split(';')[0]
            request2 = request2.header('Cookie', cookieValue)
        }
        def response2 = httpClient.exchange(request2, String)
        def json2 = new JsonSlurper().parseText(response2.body())

        then:
        response1.status == HttpStatus.OK
        response2.status == HttpStatus.OK
        json1.count == 1
        json2.count == 2
    }

    // ========== Request Info Tests ==========

    def "get request info returns server details"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/getRequestInfo',
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.method == 'GET'
        json.uri == '/requestResponseTest/getRequestInfo'
        json.scheme == 'http'
        json.serverPort == serverPort
    }

    def "get request parameters returns query parameters"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/getRequestParameters?param1=value1&param2=value2',
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.parameters['param1'] == 'value1'
        json.parameters['param2'] == 'value2'
    }

    def "set request attribute stores and retrieves value"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/setRequestAttribute?key=myAttr&value=myVal',
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.key == 'myAttr'
        json.setValue == 'myVal'
        json.retrievedValue == 'myVal'
    }

    // ========== Content Type and Encoding Tests ==========

    def "unicode response returns characters in multiple languages"() {
        when:
        def response = httpClient.exchange(
            '/requestResponseTest/unicodeResponse',
            String
        )
        def json = new JsonSlurper().parseText(response.body())

        then:
        response.status == HttpStatus.OK
        json.english == 'Hello World'
        json.chinese == '你好世界'
        json.japanese == 'こんにちは世界'
        json.korean == '안녕하세요 세계'
        json.emoji == '👋🌍🎉'
    }
}
