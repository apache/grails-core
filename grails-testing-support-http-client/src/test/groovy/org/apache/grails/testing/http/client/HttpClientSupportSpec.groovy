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
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.Flow

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

import spock.lang.Specification

class HttpClientSupportSpec extends Specification {

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

    void 'httpRequestWith uses hard-coded default request timeout'() {
        given:
        def testClient = new TestClient('http://localhost:8080')

        when:
        def request = testClient.httpRequestWith('/demo')

        then:
        request.timeout().isPresent()
        request.timeout().get() == Duration.ofSeconds(60)
    }

    void 'httpClientWith applies custom client builder configuration'() {
        given:
        def testClient = new TestClient()

        when:
        def client = testClient.httpClientWith {
            connectTimeout(Duration.ofSeconds(5))
            followRedirects(HttpClient.Redirect.NEVER)
        }

        then:
        client.connectTimeout().isPresent()
        client.connectTimeout().get() == Duration.ofSeconds(5)
        client.followRedirects() == HttpClient.Redirect.NEVER
    }

    void 'httpRequestWith allows overriding defaults and resolves relative URI'() {
        given:
        def testClient = new TestClient('http://localhost:8080')

        when:
        def request = testClient.httpRequestWith('api/demo') {
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
        def client = testClient.httpClientWith()

        then:
        client.connectTimeout().isPresent()
        client.connectTimeout().get() == Duration.ofSeconds(60)
        client.followRedirects() == HttpClient.Redirect.ALWAYS
    }

    void 'httpRequestWith keeps absolute URI and default timeout when no configurer is provided'() {
        given:
        def testClient = new TestClient('http://localhost:9999')

        when:
        def request = testClient.httpRequestWith('https://example.org/ping')

        then:
        request.uri() == URI.create('https://example.org/ping')
        request.timeout().isPresent()
        request.timeout().get() == Duration.ofSeconds(60)
    }

    void 'httpDelete(path, client) uses DELETE method and provided client'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        def result = testClient.httpDelete('/products/1', jdkClient)

        then:
        jdkClient.lastRequest != null
        jdkClient.lastRequest.method() == 'DELETE'
        jdkClient.lastRequest.uri().toString() == 'http://localhost:8080/products/1'
        result.statusCode() == 200
    }

    void 'httpOptions(path, client) uses OPTIONS method and provided client'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        def result = testClient.httpOptions('/products', jdkClient)

        then:
        jdkClient.lastRequest != null
        jdkClient.lastRequest.method() == 'OPTIONS'
        jdkClient.lastRequest.uri().toString() == 'http://localhost:8080/products'
        result.statusCode() == 200
    }

    void 'sendHttpRequest(HttpRequest, client) prints timeout warning when request timeout is missing'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def request = HttpRequest.newBuilder(URI.create('http://localhost:8080/no-timeout')).GET().build()
        def client = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        and:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out, true, 'UTF-8'))

        when:
        def response = testClient.sendHttpRequest(request, client)

        then:
        response.statusCode() == 200
        client.lastRequest.is(request)
        out.toString('UTF-8').contains('without timeout set')

        cleanup:
        System.setOut(oldOut)
    }

    void 'sendHttpRequest(HttpRequest, client) prints connect-timeout warning when custom client has no connect timeout'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def request = testClient.httpRequestWith('/uses-custom-client') { GET() }
        def client = new FakeHttpClient(Optional.empty())

        and:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out, true, 'UTF-8'))

        when:
        def response = testClient.sendHttpRequest(request, client)

        then:
        response.statusCode() == 200
        out.toString('UTF-8').contains('without connect timeout set')

        cleanup:
        System.setOut(oldOut)
    }

    void 'http(headers, path, client) forwards request headers'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        def result = testClient.http(['X-Trace-Id': 'abc-123'], '/products', jdkClient)

        then:
        result.statusCode() == 200
        jdkClient.lastRequest.headers().firstValue('X-Trace-Id').orElse(null) == 'abc-123'
    }

    void 'post with String body sets method content type and body'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        def result = testClient.httpPost(['X-Req': '1'], '/products', '{"name":"Widget"}', 'application/json', jdkClient)

        then:
        result.statusCode() == 200
        jdkClient.lastRequest.method() == 'POST'
        jdkClient.lastRequest.headers().firstValue('Content-Type').orElse(null) == 'application/json'
        jdkClient.lastRequest.headers().firstValue('X-Req').orElse(null) == '1'
        readRequestBody(jdkClient.lastRequest) == '{"name":"Widget"}'
    }

    void 'post with Map body serializes JSON'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        testClient.httpPostJson('/products', [name: 'Widget', qty: 2], jdkClient)

        then:
        jdkClient.lastRequest.method() == 'POST'
        jdkClient.lastRequest.headers().firstValue('Content-Type').orElse(null) == 'application/json'
        def body = readRequestBody(jdkClient.lastRequest)
        body.contains('"name":"Widget"')
        body.contains('"qty":2')
    }

    void 'post with MultipartBody sends multipart content type and payload bytes'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))
        def multipart = MultipartBody.builder()
                .addPart('title', 'demo')
                .addPart('file', 'a.txt', 'text/plain', 'hello')
                .build()

        when:
        testClient.httpPostMultipart('/upload', multipart, jdkClient)

        then:
        jdkClient.lastRequest.method() == 'POST'
        def contentType = jdkClient.lastRequest.headers().firstValue('Content-Type').orElse(null)
        contentType.startsWith('multipart/form-data; boundary=')
        def body = readRequestBody(jdkClient.lastRequest)
        body.contains('name="title"')
        body.contains('name="file"; filename="a.txt"')
        body.contains('hello')
    }

    void 'put and patch map overloads serialize JSON and set correct methods'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when: 'PUT with map body'
        testClient.httpPutJson('/products/1', [name: 'updated'], jdkClient)

        then:
        jdkClient.lastRequest.method() == 'PUT'
        jdkClient.lastRequest.headers().firstValue('Content-Type').orElse(null) == 'application/json'
        readRequestBody(jdkClient.lastRequest).contains('"name":"updated"')

        when: 'PATCH with map body'
        testClient.httpPatchJson('/products/1', [name: 'patched'], jdkClient)

        then:
        jdkClient.lastRequest.method() == 'PATCH'
        jdkClient.lastRequest.headers().firstValue('Content-Type').orElse(null) == 'application/json'
        readRequestBody(jdkClient.lastRequest).contains('"name":"patched"')
    }

    void 'postXml uses XML content type and generated payload'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        testClient.httpPostXml('/products', 'X-Req': '1', {
            product {
                name('Widget')
            }
        }, jdkClient)

        then:
        jdkClient.lastRequest.method() == 'POST'
        jdkClient.lastRequest.headers().firstValue('Content-Type').orElse(null) == 'application/xml'
        jdkClient.lastRequest.headers().firstValue('X-Req').orElse(null) == '1'
        readRequestBody(jdkClient.lastRequest).contains('<name>Widget</name>')
    }

    void 'putXml uses XML content type and generated payload'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        testClient.httpPutXml('/products/1', 'X-Req': '2', {
            product {
                name('Updated')
            }
        }, jdkClient)

        then:
        jdkClient.lastRequest.method() == 'PUT'
        jdkClient.lastRequest.headers().firstValue('Content-Type').orElse(null) == 'application/xml'
        jdkClient.lastRequest.headers().firstValue('X-Req').orElse(null) == '2'
        readRequestBody(jdkClient.lastRequest).contains('<name>Updated</name>')
    }

    void 'patchXml uses XML content type and generated payload'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        testClient.httpPatchXml('/products/1', 'X-Req': '3', {
            product {
                name('Patched')
            }
        }, jdkClient)

        then:
        jdkClient.lastRequest.method() == 'PATCH'
        jdkClient.lastRequest.headers().firstValue('Content-Type').orElse(null) == 'application/xml'
        jdkClient.lastRequest.headers().firstValue('X-Req').orElse(null) == '3'
        readRequestBody(jdkClient.lastRequest).contains('<name>Patched</name>')
    }

    void 'patch map overload with headers and client serializes json payload'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        testClient.httpPatchJson('/products/1', 'X-Req': '4', [name: 'patched'], jdkClient)

        then:
        jdkClient.lastRequest.method() == 'PATCH'
        jdkClient.lastRequest.headers().firstValue('Content-Type').orElse(null) == 'application/json'
        jdkClient.lastRequest.headers().firstValue('X-Req').orElse(null) == '4'
        readRequestBody(jdkClient.lastRequest).contains('"name":"patched"')
    }

    void 'delete and options with headers forward header and method'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def jdkClient = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        testClient.httpDelete('/products/1', 'X-Req': '5', jdkClient)

        then:
        jdkClient.lastRequest.method() == 'DELETE'
        jdkClient.lastRequest.headers().firstValue('X-Req').orElse(null) == '5'

        when:
        testClient.httpOptions('/products', 'X-Req': '6', jdkClient)

        then:
        jdkClient.lastRequest.method() == 'OPTIONS'
        jdkClient.lastRequest.headers().firstValue('X-Req').orElse(null) == '6'
    }

    void 'httpRequestWith throws IllegalArgumentException for null pathOrUrl'() {
        given:
        def testClient = new TestClient('http://localhost:8080')

        when:
        testClient.httpRequestWith(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('pathOrUrl must not be null')
    }

    void 'httpRequestWith relative path without baseUrl throws IllegalStateException'() {
        given:
        def testClient = new TestClient()

        when:
        testClient.httpRequestWith('/products')

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('No baseUrl set')
    }

    void 'sendHttpRequest(HttpRequest, client) does not print timeout warnings when timeout is present and client is configured'() {
        given:
        def testClient = new TestClient('http://localhost:8080')
        def request = testClient.httpRequestWith('/ok') { GET() }
        def client = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        and:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out, true, 'UTF-8'))

        when:
        def response = testClient.sendHttpRequest(request, client)

        then:
        response.statusCode() == 200
        out.toString('UTF-8').trim().isEmpty()

        cleanup:
        System.setOut(oldOut)
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
            HttpClientSupport.super.getHttpBaseUrl()
        }
    }

    private static String readRequestBody(HttpRequest request) {
        def publisher = request.bodyPublisher().orElse(null)
        if (!publisher) {
            return ''
        }
        def bytes = new ByteArrayOutputStream()
        def done = new CountDownLatch(1)
        def failure = new AtomicReference<Throwable>()
        publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
            private Flow.Subscription subscription

            @Override
            void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription
                subscription.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(ByteBuffer item) {
                def chunk = new byte[item.remaining()]
                item.get(chunk)
                bytes.write(chunk)
            }

            @Override
            void onError(Throwable throwable) {
                failure.set(throwable)
                done.countDown()
            }

            @Override
            void onComplete() {
                done.countDown()
            }
        })

        if (!done.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError('Timed out reading request body publisher' as Object)
        }
        if (failure.get()) {
            throw new AssertionError('Failed reading request body', failure.get())
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8)
    }

    private static class FakeHttpClient extends HttpClient {

        private final Optional<Duration> connectTimeoutValue
        HttpRequest lastRequest

        FakeHttpClient(Optional<Duration> connectTimeoutValue) {
            this.connectTimeoutValue = connectTimeoutValue
        }

        @Override
        Optional<Duration> connectTimeout() {
            connectTimeoutValue
        }

        @Override
        <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            this.lastRequest = request
            [
                    statusCode: { -> 200 },
                    body: { -> 'OK' as T },
                    headers: { -> HttpHeaders.of([:], { k, v -> true }) },
                    request: { -> request },
                    previousResponse: { -> Optional.empty() },
                    sslSession: { -> Optional.empty() },
                    uri: { -> request.uri() },
                    version: { -> Version.HTTP_1_1 }
            ] as HttpResponse<T>
        }

        @Override Optional<CookieHandler> cookieHandler() { Optional.empty() }
        @Override Redirect followRedirects() { Redirect.NEVER }
        @Override Optional<ProxySelector> proxy() { Optional.empty() }
        @Override SSLContext sslContext() { null }
        @Override SSLParameters sslParameters() { null }
        @Override Optional<Authenticator> authenticator() { Optional.empty() }
        @Override Version version() { Version.HTTP_1_1 }
        @Override Optional<Executor> executor() { Optional.empty() }

        @Override
        <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException('not needed for tests')
        }

        @Override
        <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException('not needed for tests')
        }
    }
}
