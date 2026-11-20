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

import grails.converters.JSON

/**
 * Controller demonstrating request/response handling patterns in Grails,
 * including headers, cookies, session, and request attributes.
 */
class RequestResponseTestController {

    static responseFormats = ['json', 'html']

    // ========== Request Header Tests ==========

    def echoHeaders() {
        def headers = [:]
        request.headerNames.each { name ->
            headers[name] = request.getHeader(name)
        }
        render([headers: headers] as JSON)
    }

    def getSpecificHeader() {
        def headerName = params.headerName ?: 'X-Custom-Header'
        def headerValue = request.getHeader(headerName)
        render([headerName: headerName, headerValue: headerValue] as JSON)
    }

    def checkUserAgent() {
        def userAgent = request.getHeader('User-Agent')
        def isBrowser = userAgent?.contains('Mozilla') || userAgent?.contains('Chrome')
        render([userAgent: userAgent, isBrowser: isBrowser] as JSON)
    }

    def checkAcceptHeader() {
        def accept = request.getHeader('Accept')
        def acceptsJson = accept?.contains('application/json')
        def acceptsHtml = accept?.contains('text/html')
        def acceptsAll = accept?.contains('*/*')
        render([accept: accept, acceptsJson: acceptsJson, acceptsHtml: acceptsHtml, acceptsAll: acceptsAll] as JSON)
    }

    def checkContentType() {
        def contentType = request.contentType
        render([contentType: contentType] as JSON)
    }

    // ========== Response Header Tests ==========

    def setCustomHeaders() {
        response.setHeader('X-Custom-Header', 'CustomValue')
        response.setHeader('X-Request-Id', UUID.randomUUID().toString())
        response.setHeader('X-Timestamp', String.valueOf(System.currentTimeMillis()))
        render([status: 'ok', message: 'Headers set'] as JSON)
    }

    def setCacheHeaders() {
        response.setHeader('Cache-Control', 'max-age=3600, public')
        response.setHeader('ETag', '"abc123"')
        response.setHeader('Last-Modified', 'Wed, 21 Oct 2025 07:28:00 GMT')
        render([status: 'ok', cached: true] as JSON)
    }

    def setNoCacheHeaders() {
        response.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate')
        response.setHeader('Pragma', 'no-cache')
        response.setHeader('Expires', '0')
        render([status: 'ok', cached: false] as JSON)
    }

    def setContentDisposition() {
        response.setHeader('Content-Disposition', 'attachment; filename="report.pdf"')
        response.contentType = 'application/pdf'
        render([status: 'ok', downloadable: true] as JSON)
    }

    def setMultipleCustomHeaders() {
        5.times { i ->
            response.setHeader("X-Custom-${i}", "Value-${i}")
        }
        render([status: 'ok', headersSet: 5] as JSON)
    }

    // ========== Cookie Tests ==========

    def setCookie() {
        def cookieName = params.name ?: 'testCookie'
        def cookieValue = params.value ?: 'testValue'
        def maxAge = params.int('maxAge') ?: 3600
        
        def cookie = new jakarta.servlet.http.Cookie(cookieName, cookieValue)
        cookie.maxAge = maxAge
        cookie.path = '/'
        response.addCookie(cookie)
        
        render([status: 'ok', cookieSet: true, name: cookieName, value: cookieValue, maxAge: maxAge] as JSON)
    }

    def setSecureCookie() {
        def cookie = new jakarta.servlet.http.Cookie('secureCookie', 'secureValue')
        cookie.maxAge = 3600
        cookie.path = '/'
        cookie.secure = true
        cookie.httpOnly = true
        response.addCookie(cookie)
        
        render([status: 'ok', secure: true, httpOnly: true] as JSON)
    }

    def setMultipleCookies() {
        3.times { i ->
            def cookie = new jakarta.servlet.http.Cookie("cookie${i}", "value${i}")
            cookie.maxAge = 3600
            cookie.path = '/'
            response.addCookie(cookie)
        }
        render([status: 'ok', cookiesSet: 3] as JSON)
    }

    def getCookies() {
        def cookies = request.cookies?.collectEntries { cookie ->
            [cookie.name, cookie.value]
        } ?: [:]
        render([cookies: cookies] as JSON)
    }

    def getSpecificCookie() {
        def cookieName = params.name ?: 'testCookie'
        def cookie = request.cookies?.find { it.name == cookieName }
        render([
            found: cookie != null,
            name: cookie?.name,
            value: cookie?.value
        ] as JSON)
    }

    def deleteCookie() {
        def cookieName = params.name ?: 'testCookie'
        def cookie = new jakarta.servlet.http.Cookie(cookieName, '')
        cookie.maxAge = 0
        cookie.path = '/'
        response.addCookie(cookie)
        render([status: 'ok', deleted: cookieName] as JSON)
    }

    // ========== Session Tests ==========

    def setSessionAttribute() {
        def key = params.key ?: 'testKey'
        def value = params.value ?: 'testValue'
        session[key] = value
        render([status: 'ok', sessionId: session.id, key: key, value: value] as JSON)
    }

    def getSessionAttribute() {
        def key = params.key ?: 'testKey'
        def value = session[key]
        render([sessionId: session.id, key: key, value: value, found: value != null] as JSON)
    }

    def getAllSessionAttributes() {
        def attributes = [:]
        session.attributeNames.each { name ->
            // Skip internal attributes
            if (!name.startsWith('org.') && !name.startsWith('SPRING_')) {
                attributes[name] = session.getAttribute(name)?.toString()
            }
        }
        render([sessionId: session.id, attributes: attributes] as JSON)
    }

    def removeSessionAttribute() {
        def key = params.key ?: 'testKey'
        def previousValue = session[key]
        session.removeAttribute(key)
        render([status: 'ok', key: key, previousValue: previousValue, removed: true] as JSON)
    }

    def invalidateSession() {
        def oldSessionId = session.id
        session.invalidate()
        render([status: 'ok', invalidated: true, oldSessionId: oldSessionId] as JSON)
    }

    def sessionCounter() {
        def count = session.counter ?: 0
        count++
        session.counter = count
        render([sessionId: session.id, count: count] as JSON)
    }

    // ========== Request Attribute Tests ==========

    def setRequestAttribute() {
        def key = params.key ?: 'requestAttr'
        def value = params.value ?: 'requestValue'
        request.setAttribute(key, value)
        // Read it back to verify
        def retrieved = request.getAttribute(key)
        render([key: key, setValue: value, retrievedValue: retrieved] as JSON)
    }

    def getRequestInfo() {
        render([
            method: request.method,
            uri: request.requestURI,
            url: request.requestURL.toString(),
            queryString: request.queryString,
            contextPath: request.contextPath,
            servletPath: request.servletPath,
            scheme: request.scheme,
            serverName: request.serverName,
            serverPort: request.serverPort,
            remoteAddr: request.remoteAddr,
            localAddr: request.localAddr,
            protocol: request.protocol
        ] as JSON)
    }

    def getRequestParameters() {
        def params = request.parameterMap.collectEntries { k, v ->
            [k, v.length == 1 ? v[0] : v.toList()]
        }
        render([parameters: params] as JSON)
    }

    // ========== Content Type and Encoding Tests ==========

    def setContentType() {
        def contentType = params.contentType ?: 'application/json'
        response.contentType = contentType
        render([contentType: contentType] as JSON)
    }

    def setCharacterEncoding() {
        def encoding = params.encoding ?: 'UTF-8'
        response.characterEncoding = encoding
        render([encoding: encoding, message: 'Encoding set to ' + encoding] as JSON)
    }

    def unicodeResponse() {
        response.characterEncoding = 'UTF-8'
        render([
            english: 'Hello World',
            chinese: '你好世界',
            japanese: 'こんにちは世界',
            korean: '안녕하세요 세계',
            arabic: 'مرحبا بالعالم',
            emoji: '👋🌍🎉'
        ] as JSON)
    }
}
