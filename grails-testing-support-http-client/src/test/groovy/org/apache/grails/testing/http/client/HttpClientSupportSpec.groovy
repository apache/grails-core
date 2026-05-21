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

import com.sun.net.httpserver.HttpServer

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import groovy.xml.XmlSlurper

import io.github.cjstehno.ersatz.GroovyErsatzServer
import io.github.cjstehno.ersatz.encdec.Decoders
import io.github.cjstehno.ersatz.encdec.JsonDecoder
import io.github.cjstehno.ersatz.encdec.MultipartRequestContent
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.apache.grails.testing.http.client.utils.XmlUtils

class HttpClientSupportSpec extends Specification {

    @AutoCleanup GroovyErsatzServer server = new GroovyErsatzServer({})

    void 'getHttpBaseUrl() throws if not set'() {
        given:
        def testClient = new TestClient()

        when:
        testClient.httpBaseUrl

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('No baseUrl set')
    }

    void 'shared client is singleton and reused across threads'() {
        given:
        def testClient = new TestClient()
        def mainThreadClient = testClient.httpClient

        and:
        def otherThreadClientRef = new AtomicReference<HttpClient>()
        def latch = new CountDownLatch(1)

        when:
        Thread.start {
            try {
                otherThreadClientRef.set(testClient.httpClient)
            } finally {
                latch.countDown()
            }
        }

        then:
        latch.await(5, TimeUnit.SECONDS)
        otherThreadClientRef.get() != null
        mainThreadClient.is(otherThreadClientRef.get())
    }

    void 'newHttpRequestWith uses hard-coded default request timeout'() {
        given:
        def testClient = new TestClient('http://localhost:8080')

        when:
        def request = testClient.newHttpRequestWith('/demo')

        then:
        request.timeout().isPresent()
        request.timeout().get() == Duration.ofSeconds(60)
    }

    void 'newHttpClientWith applies custom client builder configuration'() {
        given:
        def testClient = new TestClient()

        when:
        def client = testClient.newHttpClientWith {
            connectTimeout(Duration.ofSeconds(5))
            followRedirects(HttpClient.Redirect.NEVER)
        }

        then:
        client.connectTimeout().isPresent()
        client.connectTimeout().get() == Duration.ofSeconds(5)
        client.followRedirects() == HttpClient.Redirect.NEVER
    }

    void 'newHttpRequestWith allows overriding defaults and resolves relative URI'() {
        given:
        def testClient = new TestClient('http://localhost:8080')

        when:
        def request = testClient.newHttpRequestWith('api/demo') {
            timeout(Duration.ofSeconds(10))
            GET()
        }

        then:
        request.uri().toString() == 'http://localhost:8080/api/demo'
        request.timeout().isPresent()
        request.timeout().get() == Duration.ofSeconds(10)
        request.method() == 'GET'
    }

    void 'httpClientWith without configurer keeps hard-coded defaults'() {
        given:
        def testClient = new TestClient()

        when:
        def client = testClient.newHttpClientWith()

        then:
        client.connectTimeout().isPresent()
        client.connectTimeout().get() == Duration.ofSeconds(60)
        client.followRedirects() == HttpClient.Redirect.ALWAYS
    }

    void 'newHttpRequestWith keeps absolute URI and default timeout when no configurer is provided'() {
        given:
        def testClient = new TestClient('http://localhost:9999')

        when:
        def request = testClient.newHttpRequestWith('https://example.org/ping')

        then:
        request.uri() == URI.create('https://example.org/ping')
        request.timeout().isPresent()
        request.timeout().get() == Duration.ofSeconds(60)
    }

    @Unroll
    void 'httpTrace uses TRACE method with #label client'(HttpClient client) {
        given:
        def traceServer = HttpServer.create(new InetSocketAddress(0), 0)
        traceServer.createContext('/products/trace') { exchange ->
            exchange.responseHeaders.add('Content-Type', 'message/http')
            def responseBody = "${exchange.requestMethod} ${exchange.requestURI.path} HTTP/1.1"
            byte[] bytes = responseBody.getBytes('UTF-8')
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.withCloseable {
                it.write(bytes)
            }
        }
        traceServer.start()

        and:
        def testClient = new TestClient("http://localhost:${traceServer.address.port}")

        when:
        def response = testClient.httpTrace('/products/trace', client)

        then:
        response.assertEquals(200, 'TRACE /products/trace HTTP/1.1')

        cleanup:
        traceServer.stop(0)

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'httpHead uses HEAD method with #label client'(HttpClient client) {
        given:
        server.expectations {
            HEAD('/products/1') {
                called(1)
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when:
        def response = testClient.httpHead(server.httpUrl('/products/1'), client)

        then:
        response.assertStatus(204)
        response.body() == ''
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'httpDelete uses DELETE method with #label client'(HttpClient client) {
        given:
        server.expectations {
            DELETE('/products/1') {
                called(1)
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when:
        def response = testClient.httpDelete(server.httpUrl('/products/1'), client)

        then:
        response.assertStatus(204)
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'httpOptions uses OPTIONS method with #label client'(HttpClient client) {
        given:
        server.expectations {
            OPTIONS('/products') {
                called(1)
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when:
        def response = testClient.httpOptions('/products', client)

        then:
        response.assertStatus(204)
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    void 'sendHttpRequest(HttpRequest) prints timeout warning when request timeout is missing'() {
        given:
        server.expectations {
            GET('/no-timeout') {
                called(1)
                responder {
                    body('hello')
                }
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)
        def request = HttpRequest.newBuilder(server.httpUrl('/no-timeout').toURI()).GET().build()

        and:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.out = new PrintStream(out, true, 'UTF-8')

        when:
        def response = testClient.sendHttpRequest(request)

        then:
        response.assertEquals(200, 'hello')
        out.toString('UTF-8').contains('without configured timeout')
        server.verify()

        cleanup:
        System.out = oldOut
    }

    void 'sendHttpRequest(HttpRequest, client) prints connect-timeout warning when custom client has no connect timeout'() {
        given:
        server.expectations {
            GET('/uses-custom-client') {
                called(1)
                responder {
                    body('hello')
                }
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)
        def request = testClient.newHttpRequestWith('/uses-custom-client') { GET() }

        and:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.out = new PrintStream(out, true, 'UTF-8')

        when:
        def response = testClient.sendHttpRequest(request, HttpClient.newHttpClient())

        then:
        response.assertEquals(200, 'hello')
        out.toString('UTF-8').contains('without configured connect timeout')
        server.verify()

        cleanup:
        System.out = oldOut
    }

    void 'http(headers, path, client) forwards request headers'() {
        given:
        server.expectations {
            GET('/products') {
                called(1)
                headers('X-Trace-Id': 'abc-123')
                responder {
                    body('hello')
                }
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)
        def customClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

        when:
        def response = testClient.http('/products', 'X-Trace-Id': 'abc-123', customClient)

        then:
        response.assertEquals(200, 'hello')
        server.verify()
    }

    @Unroll
    void 'post with String body sets method content type and body with #label client'(HttpClient client) {
        given:
        server.expectations {
            POST('/products') {
                called(1)
                decoder('application/json', new JsonDecoder())
                headers('X-Req': '1')
                body([name: 'Widget'], 'application/json')
                responder {
                    body('hello')
                }
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when:
        def response = testClient.httpPost('/products', 'X-Req': '1', '{"name":"Widget"}', 'application/json', client)

        then:
        response.assertEquals(200, 'hello')
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'post with Map body serializes JSON with #label client'(HttpClient client) {
        given:
        server.expectations {
            POST('/products') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'Widget', qty: 2], 'application/json')
                responder {
                    body('hello')
                }
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when:
        def response = testClient.httpPostJson('/products', [name: 'Widget', qty: 2], client)

        then:
        response.assertEquals(200, 'hello')
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'post with MultipartBody sends multipart content type and payload bytes with #label client'(HttpClient client) {
        given:
        server.expectations {
            POST('/upload') {
                called(1)
                decoder('text/plain', Decoders.utf8String)
                decoder('multipart/form-data', Decoders.multipart)
                body(MultipartRequestContent.multipartRequest {
                    part('title', 'demo')
                    part('file', 'a.txt', 'text/plain', 'hello')
                }, 'multipart/form-data')
                responder {
                    body('hello')
                }
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)
        def multipart = MultipartBody.builder()
                .addPart('title', 'demo')
                .addPart('file', 'a.txt', 'text/plain', 'hello')
                .build()

        when:
        def response = testClient.httpPostMultipart('/upload', multipart, client)

        then:
        response.assertEquals(200, 'hello')
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'put and patch map overloads serialize JSON and set correct methods with #label client'(HttpClient client) {
        given:
        server.expectations {
            PUT('/products/1') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'updated'], 'application/json')
            }
            PATCH('/products/1') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'patched'], 'application/json')
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when: 'PUT with map body'
        def response = testClient.httpPutJson('/products/1', [name: 'updated'], client)

        then:
        response.assertStatus(204)

        when: 'PATCH with map body'
        response = testClient.httpPatchJson('/products/1', [name: 'patched'], client)

        then:
        response.assertStatus(204)

        and:
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'httpPostXml uses XML content type and generated payload with #label client'(HttpClient client) {
        given:
        server.expectations {
            POST('/products') {
                called(1)
                headers('X-Req': '1')
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('Widget', 'application/xml')
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when:
        def response = testClient.httpPostXml('/products', 'X-Req': '1', new XmlUtils.Format(omitEmptyAttributes: true), {
            product {
                name('Widget')
            }
        }, client)

        then:
        response.assertStatus(204)
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'httpPutXml uses XML content type and generated payload with #label client'(HttpClient client) {
        given:
        server.expectations {
            PUT('/products/1') {
                called(1)
                headers('X-Req': '2')
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('Updated', 'application/xml')
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when:
        def response = testClient.httpPutXml('/products/1', 'X-Req': '2', {
            product {
                name('Updated')
            }
        }, client)

        then:
        response.assertStatus(204)
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'httpPatchXml uses XML content type and generated payload with #label client'(HttpClient client) {
        given:
        server.expectations {
            PATCH('/products/1') {
                called(1)
                headers('X-Req': '3')
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('Patched', 'application/xml')
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when:
        def response = testClient.httpPatchXml('/products/1', 'X-Req': '3', {
            product {
                name('Patched')
            }
        }, client)

        then:
        response.assertStatus(204)
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'patch map overload with headers serializes json payload with #label client'(HttpClient client) {
        given:
        server.expectations {
            PATCH('/products/1') {
                called(1)
                header('X-Req', '4')
                decoder('application/json', new JsonDecoder())
                body([name: 'patched'], 'application/json')
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when:
        def response = testClient.httpPatchJson('/products/1', 'X-Req': '4', [name: 'patched'], client)

        then:
        response.assertStatus(204)
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    @Unroll
    void 'httpTrace forwards headers with #label client'(HttpClient client) {
        given:
        def traceServer = HttpServer.create(new InetSocketAddress(0), 0)
        def capturedHeader = new AtomicReference<String>()
        traceServer.createContext('/products/trace') { exchange ->
            capturedHeader.set(exchange.requestHeaders.getFirst('X-Req'))
            exchange.responseHeaders.add('Content-Type', 'message/http')
            def responseBody = "${exchange.requestMethod} ${exchange.requestURI.path} HTTP/1.1"
            byte[] bytes = responseBody.getBytes('UTF-8')
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.withCloseable {
                it.write(bytes)
            }
        }
        traceServer.start()

        and:
        def testClient = new TestClient("http://localhost:${traceServer.address.port}")

        when:
        def response = testClient.httpTrace('/products/trace', 'X-Req': '3', client)

        then:
        response.assertEquals(200, 'TRACE /products/trace HTTP/1.1')
        capturedHeader.get() == '3'

        cleanup:
        traceServer.stop(0)

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    void 'no body request helper overloads without explicit clients are called explicitly'() {
        given:
        server.expectations {
            HEAD('/products/head-default') {
                called(1)
            }
            HEAD('/products/head-header') {
                called(1)
                header('X-Req', '4')
            }
            DELETE('/products/delete-default') {
                called(1)
            }
            DELETE('/products/delete-header') {
                called(1)
                header('X-Req', '5')
            }
            OPTIONS('/products/options-default') {
                called(1)
            }
            OPTIONS('/products/options-header') {
                called(1)
                header('X-Req', '6')
            }
        }

        and:
        def traceRequests = Collections.synchronizedMap([:] as Map<String, String>)
        def traceServer = HttpServer.create(new InetSocketAddress(0), 0)
        traceServer.createContext('/products') { exchange ->
            traceRequests[exchange.requestURI.path] = exchange.requestHeaders.getFirst('X-Req')
            exchange.responseHeaders.add('Content-Type', 'message/http')
            def responseBody = "${exchange.requestMethod} ${exchange.requestURI.path} HTTP/1.1"
            byte[] bytes = responseBody.getBytes('UTF-8')
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.withCloseable {
                it.write(bytes)
            }
        }
        traceServer.start()

        and:
        def testClient = new TestClient(server.httpUrl)
        def traceClient = new TestClient("http://localhost:${traceServer.address.port}")

        when:
        def traceDefault = traceClient.httpTrace('/products/trace-default')
        def traceHeader = traceClient.httpTrace('/products/trace-header', 'X-Req': '3')
        def headDefault = testClient.httpHead('/products/head-default')
        def headHeader = testClient.httpHead('/products/head-header', 'X-Req': '4')
        def deleteDefault = testClient.httpDelete('/products/delete-default')
        def deleteHeader = testClient.httpDelete('/products/delete-header', 'X-Req': '5')
        def optionsDefault = testClient.httpOptions('/products/options-default')
        def optionsHeader = testClient.httpOptions('/products/options-header', 'X-Req': '6')

        then:
        traceDefault.assertEquals(200, 'TRACE /products/trace-default HTTP/1.1')
        traceHeader.assertEquals(200, 'TRACE /products/trace-header HTTP/1.1')
        traceRequests['/products/trace-default'] == null
        traceRequests['/products/trace-header'] == '3'
        headDefault.assertStatus(204)
        headDefault.body() == ''
        headHeader.assertStatus(204)
        headHeader.body() == ''
        deleteDefault.assertStatus(204)
        deleteHeader.assertStatus(204)
        optionsDefault.assertStatus(204)
        optionsHeader.assertStatus(204)
        server.verify()

        cleanup:
        traceServer.stop(0)
    }

    void 'json request helper overloads are called explicitly'() {
        given:
        server.expectations {
            POST('/json/post-string') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'PostString'], 'application/json')
            }
            POST('/json/post-default') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'PostDefault'], 'application/json')
            }
            POST('/json/post-client') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'PostClient'], 'application/json')
            }
            POST('/json/post-header') {
                called(1)
                header('X-Req', 'P1')
                decoder('application/json', new JsonDecoder())
                body([name: 'PostHeader'], 'application/json')
            }
            POST('/json/post-header-client') {
                called(1)
                header('X-Req', 'P2')
                decoder('application/json', new JsonDecoder())
                body([name: 'PostHeaderClient'], 'application/json')
            }
            PUT('/json/put-string') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'PutString'], 'application/json')
            }
            PUT('/json/put-default') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'PutDefault'], 'application/json')
            }
            PUT('/json/put-client') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'PutClient'], 'application/json')
            }
            PUT('/json/put-header') {
                called(1)
                header('X-Req', 'P3')
                decoder('application/json', new JsonDecoder())
                body([name: 'PutHeader'], 'application/json')
            }
            PUT('/json/put-header-client') {
                called(1)
                header('X-Req', 'P4')
                decoder('application/json', new JsonDecoder())
                body([name: 'PutHeaderClient'], 'application/json')
            }
            PATCH('/json/patch-string') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'PatchString'], 'application/json')
            }
            PATCH('/json/patch-default') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'PatchDefault'], 'application/json')
            }
            PATCH('/json/patch-client') {
                called(1)
                decoder('application/json', new JsonDecoder())
                body([name: 'PatchClient'], 'application/json')
            }
            PATCH('/json/patch-header') {
                called(1)
                header('X-Req', 'P5')
                decoder('application/json', new JsonDecoder())
                body([name: 'PatchHeader'], 'application/json')
            }
            PATCH('/json/patch-header-client') {
                called(1)
                header('X-Req', 'P6')
                decoder('application/json', new JsonDecoder())
                body([name: 'PatchHeaderClient'], 'application/json')
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)
        def customClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

        when:
        def postString = testClient.httpPostJson('/json/post-string', '{"name":"PostString"}')
        def postDefault = testClient.httpPostJson('/json/post-default', [name: 'PostDefault'])
        def postClient = testClient.httpPostJson('/json/post-client', [name: 'PostClient'], customClient)
        def postHeader = testClient.httpPostJson('/json/post-header', 'X-Req': 'P1', [name: 'PostHeader'])
        def postHeaderClient = testClient.httpPostJson('/json/post-header-client', 'X-Req': 'P2', [name: 'PostHeaderClient'], customClient)
        def putString = testClient.httpPutJson('/json/put-string', '{"name":"PutString"}')
        def putDefault = testClient.httpPutJson('/json/put-default', [name: 'PutDefault'])
        def putClient = testClient.httpPutJson('/json/put-client', [name: 'PutClient'], customClient)
        def putHeader = testClient.httpPutJson('/json/put-header', 'X-Req': 'P3', [name: 'PutHeader'])
        def putHeaderClient = testClient.httpPutJson('/json/put-header-client', 'X-Req': 'P4', [name: 'PutHeaderClient'], customClient)
        def patchString = testClient.httpPatchJson('/json/patch-string', '{"name":"PatchString"}')
        def patchDefault = testClient.httpPatchJson('/json/patch-default', [name: 'PatchDefault'])
        def patchClient = testClient.httpPatchJson('/json/patch-client', [name: 'PatchClient'], customClient)
        def patchHeader = testClient.httpPatchJson('/json/patch-header', 'X-Req': 'P5', [name: 'PatchHeader'])
        def patchHeaderClient = testClient.httpPatchJson('/json/patch-header-client', 'X-Req': 'P6', [name: 'PatchHeaderClient'], customClient)

        then:
        [postString, postDefault, postClient, postHeader, postHeaderClient,
         putString, putDefault, putClient, putHeader, putHeaderClient,
         patchString, patchDefault, patchClient, patchHeader, patchHeaderClient].each {
            it.assertStatus(204)
        }
        server.verify()
    }

    void 'xml request helper overloads are called explicitly'() {
        given:
        server.expectations {
            POST('/xml/post-string') {
                called(1)
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PostString', 'application/xml')
            }
            POST('/xml/post-default') {
                called(1)
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PostDefault', 'application/xml')
            }
            POST('/xml/post-client') {
                called(1)
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PostClient', 'application/xml')
            }
            POST('/xml/post-header') {
                called(1)
                header('X-Req', 'X1')
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PostHeader', 'application/xml')
            }
            POST('/xml/post-header-client') {
                called(1)
                header('X-Req', 'X2')
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PostHeaderClient', 'application/xml')
            }
            PUT('/xml/put-default') {
                called(1)
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PutDefault', 'application/xml')
            }
            PUT('/xml/put-client') {
                called(1)
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PutClient', 'application/xml')
            }
            PUT('/xml/put-header') {
                called(1)
                header('X-Req', 'X3')
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PutHeader', 'application/xml')
            }
            PUT('/xml/put-header-client') {
                called(1)
                header('X-Req', 'X4')
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PutHeaderClient', 'application/xml')
            }
            PATCH('/xml/patch-default') {
                called(1)
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PatchDefault', 'application/xml')
            }
            PATCH('/xml/patch-client') {
                called(1)
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PatchClient', 'application/xml')
            }
            PATCH('/xml/patch-header') {
                called(1)
                header('X-Req', 'X5')
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PatchHeader', 'application/xml')
            }
            PATCH('/xml/patch-header-client') {
                called(1)
                header('X-Req', 'X6')
                decoder('application/xml') { bytes, _ ->
                    new XmlSlurper().parseText(new String(bytes, 'utf-8')).text()
                }
                body('PatchHeaderClient', 'application/xml')
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)
        def customClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        def format = new XmlUtils.Format()

        when:
        def postString = testClient.httpPostXml('/xml/post-string', '<product><name>PostString</name></product>')
        def postDefault = testClient.httpPostXml('/xml/post-default', format, {
            product {
                name('PostDefault')
            }
        })
        def postClient = testClient.httpPostXml('/xml/post-client', format, {
            product {
                name('PostClient')
            }
        }, customClient)
        def postHeader = testClient.httpPostXml('/xml/post-header', 'X-Req': 'X1', format, {
            product {
                name('PostHeader')
            }
        })
        def postHeaderClient = testClient.httpPostXml('/xml/post-header-client', 'X-Req': 'X2', format, {
            product {
                name('PostHeaderClient')
            }
        }, customClient)
        def putDefault = testClient.httpPutXml('/xml/put-default', format, {
            product {
                name('PutDefault')
            }
        })
        def putClient = testClient.httpPutXml('/xml/put-client', format, {
            product {
                name('PutClient')
            }
        }, customClient)
        def putHeader = testClient.httpPutXml('/xml/put-header', 'X-Req': 'X3', format, {
            product {
                name('PutHeader')
            }
        })
        def putHeaderClient = testClient.httpPutXml('/xml/put-header-client', 'X-Req': 'X4', format, {
            product {
                name('PutHeaderClient')
            }
        }, customClient)
        def patchDefault = testClient.httpPatchXml('/xml/patch-default', format, {
            product {
                name('PatchDefault')
            }
        })
        def patchClient = testClient.httpPatchXml('/xml/patch-client', format, {
            product {
                name('PatchClient')
            }
        }, customClient)
        def patchHeader = testClient.httpPatchXml('/xml/patch-header', 'X-Req': 'X5', format, {
            product {
                name('PatchHeader')
            }
        })
        def patchHeaderClient = testClient.httpPatchXml('/xml/patch-header-client', 'X-Req': 'X6', format, {
            product {
                name('PatchHeaderClient')
            }
        }, customClient)

        then:
        [postString, postDefault, postClient, postHeader, postHeaderClient,
         putDefault, putClient, putHeader, putHeaderClient,
         patchDefault, patchClient, patchHeader, patchHeaderClient].each {
            it.assertStatus(204)
        }
        server.verify()
    }

    void 'multipart request helper overload with headers and default client is called explicitly'() {
        given:
        server.expectations {
            POST('/upload-with-header') {
                called(1)
                header('X-Req', 'M1')
                decoder('text/plain', Decoders.utf8String)
                decoder('multipart/form-data', Decoders.multipart)
                body(MultipartRequestContent.multipartRequest {
                    part('title', 'demo')
                    part('file', 'a.txt', 'text/plain', 'hello')
                }, 'multipart/form-data')
                responder {
                    body('hello')
                }
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)
        def multipart = MultipartBody.builder()
                .addPart('title', 'demo')
                .addPart('file', 'a.txt', 'text/plain', 'hello')
                .build()

        when:
        def response = testClient.httpPostMultipart('/upload-with-header', 'X-Req': 'M1', multipart)

        then:
        response.assertEquals(200, 'hello')
        server.verify()
    }

    @Unroll
    void 'head delete and options with headers forward header and method with #label client'(HttpClient client) {
        given:
        server.expectations {
            HEAD('/products/head') {
                called(1)
                header('X-Req', '4')
            }
            DELETE('/products/1') {
                called(1)
                header('X-Req', '5')
            }
            OPTIONS('/products') {
                called(1)
                header('X-Req', '6')
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)

        when:
        def headResponse = testClient.httpHead('/products/head', 'X-Req': '4', client)
        def deleteResponse = testClient.httpDelete('/products/1', 'X-Req': '5', client)
        def optionsResponse = testClient.httpOptions('/products', 'X-Req': '6', client)

        then:
        headResponse.assertStatus(204)
        deleteResponse.assertStatus(204)
        optionsResponse.assertStatus(204)
        server.verify()

        where:
        label    | client
        'shared' | null
        'custom' | HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }

    void 'newHttpRequestWith throws IllegalArgumentException for null pathOrUrl'() {
        given:
        def testClient = new TestClient()

        when:
        testClient.newHttpRequestWith(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('pathOrUrl must not be null')
    }

    void 'newHttpRequestWith relative path without baseUrl throws IllegalStateException'() {
        given:
        def testClient = new TestClient()

        when:
        testClient.newHttpRequestWith('/products')

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('No baseUrl set')
    }

    void 'sendHttpRequest(HttpRequest, client) does not print timeout warnings when timeout is present and client is configured'() {
        given:
        server.expectations {
            GET('/ok') {
                called(1)
            }
        }

        and:
        def testClient = new TestClient(server.httpUrl)
        def request = testClient.newHttpRequestWith('/ok') { GET() }
        def customClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

        and:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.out = new PrintStream(out, true, 'UTF-8')

        when:
        def response = testClient.sendHttpRequest(request, customClient)

        then:
        response.assertStatus(204)
        out.toString('UTF-8').trim().isEmpty()

        cleanup:
        System.out = oldOut
    }

    private static class TestClient implements HttpClientSupport {

        private String base

        TestClient() {}
        TestClient(String baseUrl) {
            this.base = baseUrl
        }

        @Override
        String getHttpBaseUrl() {
            if (base) {
                return base
            }
            HttpClientSupport.super.httpBaseUrl
        }
    }
}
