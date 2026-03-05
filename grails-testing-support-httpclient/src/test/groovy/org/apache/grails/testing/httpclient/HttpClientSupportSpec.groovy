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
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

import spock.lang.Specification

class HttpClientSupportSpec extends Specification {

    void 'getHttpClientRootUri() throws if no port has been assigned'() {
        given:
        def support = new TestSupport(localServerPort: -1)

        when:
        support.httpClientRootUri

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('No server port assigned')
    }

    void 'shared client is singleton and reused across threads'() {
        given:
        def support = new TestSupport(localServerPort: 0)
        def mainThreadClient = support.httpClient

        and:
        def otherThreadClientRef = new AtomicReference<HttpClient>()
        def latch = new CountDownLatch(1)

        when:
        Thread.start {
            try {
                otherThreadClientRef.set(support.httpClient)
            } finally {
                latch.countDown()
            }
        }

        then:
        latch.await(5, TimeUnit.SECONDS)
        otherThreadClientRef.get() != null
        mainThreadClient.is(otherThreadClientRef.get())
    }

    void 'requestWith uses hard-coded default request timeout'() {
        given:
        def support = new TestSupport(localServerPort: 0)

        when:
        def request = support.httpRequestWith('/demo')

        then:
        request.timeout().isPresent()
        request.timeout().get() == Duration.ofSeconds(60)
    }

    void 'httpClientWith applies custom client builder configuration'() {
        given:
        def support = new TestSupport(localServerPort: 0)

        when:
        def client = support.httpClientWith {
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
        def support = new TestSupport(localServerPort: 8080)

        when:
        def request = support.httpRequestWith('api/demo') {
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
        def support = new TestSupport(localServerPort: 0)

        when:
        def client = support.httpClientWith()

        then:
        client.connectTimeout().isPresent()
        client.connectTimeout().get() == Duration.ofSeconds(60)
        client.followRedirects() == HttpClient.Redirect.ALWAYS
    }

    void 'httpRequestWith keeps absolute URI and default timeout when no configurer is provided'() {
        given:
        def support = new TestSupport(localServerPort: 9999)

        when:
        def request = support.httpRequestWith('https://example.org/ping')

        then:
        request.uri() == URI.create('https://example.org/ping')
        request.timeout().isPresent()
        request.timeout().get() == Duration.ofSeconds(60)
    }

    void 'httpDelete(path, client) uses DELETE method and provided client'() {
        given:
        def support = new TestSupport(localServerPort: 8080)
        def client = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        when:
        def result = support.httpDelete('/products/1', client)

        then:
        client.lastRequest != null
        client.lastRequest.method() == 'DELETE'
        client.lastRequest.uri().toString() == 'http://localhost:8080/products/1'
        result.statusCode() == 200
    }

    void 'http(HttpRequest, client) prints timeout warning when request timeout is missing'() {
        given:
        def support = new TestSupport(localServerPort: 8080)
        def request = HttpRequest.newBuilder(URI.create('http://localhost:8080/no-timeout')).GET().build()
        def client = new FakeHttpClient(Optional.of(Duration.ofSeconds(2)))

        and:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out, true, 'UTF-8'))

        when:
        def response = support.http(request, client)

        then:
        response.statusCode() == 200
        client.lastRequest.is(request)
        out.toString('UTF-8').contains('without timeout set')

        cleanup:
        System.setOut(oldOut)
    }

    void 'http(HttpRequest, client) prints connect-timeout warning when custom client has no connect timeout'() {
        given:
        def support = new TestSupport(localServerPort: 8080)
        def request = support.httpRequestWith('/uses-custom-client') { GET() }
        def client = new FakeHttpClient(Optional.empty())

        and:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out, true, 'UTF-8'))

        when:
        def response = support.http(request, client)

        then:
        response.statusCode() == 200
        out.toString('UTF-8').contains('without connect timeout set')

        cleanup:
        System.setOut(oldOut)
    }

    private static class TestSupport implements HttpClientSupport {}

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
