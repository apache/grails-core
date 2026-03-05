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
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder

import org.springframework.beans.factory.annotation.Value

/**
 * Test support trait that provides a lazily-created JDK {@link HttpClient} for HTTP-based tests.
 * <p>
 * The client is stored as a static singleton so tests can reuse pooled connections efficiently.
 * <p>
 * Typical usage in a Grails Spock integration specification (where {@code local.server.port} is injected):
 * <pre>
 * import grails.testing.mixin.integration.Integration
 * import spock.lang.Specification
 *
 * @Integration
 * class MySpec extends Specification implements HttpClientSupport {
 *
 *     void 'health endpoint responds OK'() {
 *         when:
 *         def response = http('/health')
 *
 *         then:
 *         response.expectStatus(200)
 *     }
 * }
 * </pre>
 * <p>
 * Implementing tests must provide {@code local.server.port} (in Grails typically via {@code @Integration}),
 * or otherwise override {@code getHttpClientBaseURL()}.
 */
@CompileStatic
trait HttpClientSupport {

    @Value('${local.server.port}')
    int localServerPort = -1

    /**
     * Shared singleton client reused across tests.
     */
    private static volatile HttpClient sharedClient

    /**
     * @return the shared singleton client instance, creating it on first access.
     */
    HttpClient getHttpClient() {
        def client = sharedClient
        if (!client) {
            synchronized (HttpClientSupport) {
                client = sharedClient
                if (!client) {
                    client = initClient()
                    sharedClient = client
                }
            }
        }
        client
    }

    /**
     * Base URI used when resolving relative request paths.
     *
     * @return base URI such as {@code http://localhost:8080}
     * @throws IllegalStateException when {@code localServerPort} is not assigned
     */
    String getHttpClientRootUri() {
        if (localServerPort == -1) {
            throw new IllegalStateException(
                    'No server port assigned. ' +
                    'Did you remember to annotate your test ' +
                    'class with @Integration or @SpringBootTest?'
            )
        }
        "http://localhost:$localServerPort"
    }

    /**
     * Resolve a relative path (for example {@code /health}) against {@link #getHttpClientRootUri()}.
     * Absolute HTTP/HTTPS URLs are returned as-is.
     *
     * @param pathOrUrl relative path or absolute URL
     * @return resolved request URI
     */
    private URI resolveHttpUri(CharSequence pathOrUrl) {
        if (pathOrUrl == null) {
            throw new IllegalArgumentException('pathOrUrl must not be null')
        }
        def str = pathOrUrl.toString()
        if (str.startsWith('http://') || str.startsWith('https://')) {
            return URI.create(str)
        }
        def base = httpClientRootUri
        def b = base.endsWith('/') ? base[0..-2] : base
        def p = str.startsWith('/') ? str : "/$str"
        return URI.create("$b$p")
    }

    /**
     * Sends a pre-built request.
     *
     * @param request request to send
     * @param client optional explicit client; falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    HttpResponse<String> http(HttpRequest request, HttpClient client = null) {
        if (!request.timeout().isPresent()) {
            warn("Sending HttpRequest to [${request.uri()}] without timeout set.",
                    "Offending class is [${getClass().name}].",
                    'Consider using httpRequestWith() or setting timeout(...) on your custom request.')
        }
        send(client, request)
    }

    private HttpResponse<String> send(HttpClient client, Map<String, String> headers, HttpRequest.Builder requestBuilder) {
        headers?.each { k, v -> requestBuilder.header(k, v) }
        send(client, requestBuilder.build())
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) {
        if (client && !client.connectTimeout().isPresent()) {
            warn("Using HttpClient without connect timeout set when connecting to [${request.uri()}].",
                    "Offending class is [${getClass().name}].",
                    'Consider using httpClientWith() or setting connectTimeout(...) on your custom client.')
        }
        (client ?: httpClient).send(request, HttpResponse.BodyHandlers.ofString())
    }

    /**
     * Convenience GET with an explicit client and no custom headers.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param client client to execute request with
     * @return response with String body
     */
    HttpResponse<String> http(CharSequence pathOrUrl, HttpClient client) {
        http(Collections.emptyMap(), pathOrUrl, client)
    }

    /**
     * Convenience GET.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param client optional explicit client; falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    HttpResponse<String> http(
            Map<String, String> headers = Collections.emptyMap(),
            CharSequence pathOrUrl,
            HttpClient client = null
    ) {
        send(client, headers, requestBuilder(pathOrUrl).GET())
    }

    /**
     * Convenience POST using a String body.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body request payload; null is treated as empty string
     * @param contentType request content type, defaults to {@code application/json}
     * @param client optional explicit client; falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    HttpResponse<String> httpPost(
            Map<String, String> headers = Collections.emptyMap(),
            CharSequence pathOrUrl,
            String body,
            String contentType = 'application/json',
            HttpClient client = null
    ) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header('Content-Type', contentType)
                        .POST(HttpRequest.BodyPublishers.ofString(body ?: ''))
        )
    }

    /**
     * Convenience POST for multipart payloads.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body prebuilt multipart payload
     * @param client optional explicit client; falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    HttpResponse<String> httpPost(
            Map<String, String> headers = Collections.emptyMap(),
            CharSequence pathOrUrl,
            MultipartBody body,
            HttpClient client = null
    ) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header('Content-Type', body.contentType)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes))
        )
    }

    /**
     * Convenience POST for JSON map payloads.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body map serialized as JSON
     * @param client optional explicit client; falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    HttpResponse<String> httpPost(
            Map<String, String> headers = Collections.emptyMap(),
            CharSequence pathOrUrl,
            Map<String, Object> body,
            HttpClient client = null
    ) {
        httpPost(headers, pathOrUrl, JsonOutput.toJson(body), 'application/json', client)
    }

    /**
     * Convenience PUT for JSON map payloads.
     */
    HttpResponse<String> httpPut(
            Map<String, String> headers = Collections.emptyMap(),
            CharSequence pathOrUrl,
            Map<String, Object> body,
            HttpClient client = null
    ) {
        httpPut(headers, pathOrUrl, JsonOutput.toJson(body), 'application/json', client)
    }

    /**
     * Convenience PUT using a String body.
     */
    HttpResponse<String> httpPut(
            Map<String, String> headers = Collections.emptyMap(),
            CharSequence pathOrUrl,
            String body,
            String contentType = 'application/json',
            HttpClient client = null
    ) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header('Content-Type', contentType)
                        .PUT(HttpRequest.BodyPublishers.ofString(body ?: ''))
        )
    }

    /**
     * Convenience PATCH for JSON map payloads.
     */
    HttpResponse<String> httpPatch(
            Map<String, String> headers = Collections.emptyMap(),
            CharSequence pathOrUrl,
            Map<String, Object> body,
            HttpClient client = null
    ) {
        httpPatch(headers, pathOrUrl, JsonOutput.toJson(body), 'application/json', client)
    }

    /**
     * Convenience PATCH using a String body.
     */
    HttpResponse<String> httpPatch(
            Map<String, String> headers = Collections.emptyMap(),
            CharSequence pathOrUrl,
            String body,
            String contentType = 'application/json',
            HttpClient client = null
    ) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header('Content-Type', contentType)
                        .method('PATCH', HttpRequest.BodyPublishers.ofString(body ?: ''))
        )
    }

    /** Convenience DELETE with no custom headers. */
    HttpResponse<String> httpDelete(CharSequence pathOrUrl) {
        httpDelete(Collections.emptyMap(), pathOrUrl, null)
    }

    /** Convenience DELETE with explicit client and no custom headers. */
    HttpResponse<String> httpDelete(CharSequence pathOrUrl, HttpClient client) {
        httpDelete(Collections.emptyMap(), pathOrUrl, client)
    }

    /** Convenience DELETE with headers and shared client. */
    HttpResponse<String> httpDelete(Map<String, String> headers, CharSequence pathOrUrl) {
        httpDelete(headers, pathOrUrl, null)
    }

    /**
     * Convenience DELETE.
     */
    HttpResponse<String> httpDelete(Map<String, String> headers, CharSequence pathOrUrl, HttpClient client) {
        send(client, headers, requestBuilder(pathOrUrl).DELETE())
    }

    /**
     * Convenience OPTIONS request.
     */
    HttpResponse<String> httpOptions(
            Map<String, String> headers = Collections.emptyMap(),
            CharSequence pathOrUrl,
            HttpClient client = null
    ) {
        send(client, headers, requestBuilder(pathOrUrl).method('OPTIONS', HttpRequest.BodyPublishers.noBody())
        )
    }

    /**
     * Builds XML text using {@link MarkupBuilder}.
     *
     * @param dsl closure that writes XML markup
     * @return generated XML payload string
     */
    String xmlPayload(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> dsl) {
        def writer = new StringWriter()
        def markupBuilder = new MarkupBuilder(writer)
        def c = (Closure) dsl.clone()
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.delegate = markupBuilder
        c.call()
        writer.toString()
    }

    /**
     * Creates an {@link HttpClient} with default configuration and optional customizations.
     *
     * @param configurer optional http builder configurer closure
     * @return built client
     */
    HttpClient httpClientWith(
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = HttpClient.Builder) Closure<?> configurer = null
    ) {
        def builder = initClientBuilder()
        if (configurer) {
            configurer.resolveStrategy = Closure.DELEGATE_FIRST
            configurer.delegate = builder
            configurer.call(builder)
        }
        builder.build()
    }

    /**
     * Creates an {@link HttpRequest} with default configuration and optional customizations.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param configurer optional request builder configurer closure
     * @return built request
     */
    HttpRequest httpRequestWith(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = HttpRequest.Builder) Closure<?> configurer = null
    ) {
        def builder = requestBuilder(pathOrUrl)
        if (configurer) {
            configurer.resolveStrategy = Closure.DELEGATE_FIRST
            configurer.delegate = builder
            configurer.call(builder)
        }
        builder.build()
    }

    private HttpRequest.Builder requestBuilder(CharSequence pathOrUrl) {
        def builder = HttpRequest.newBuilder(resolveHttpUri(pathOrUrl))
        setDefaultRequestConfig(builder)
        builder
    }

    private HttpClient initClient() {
        initClientBuilder().build()
    }

    private HttpClient.Builder initClientBuilder() {
        HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .followRedirects(HttpClient.Redirect.ALWAYS)
    }

    private void setDefaultRequestConfig(HttpRequest.Builder builder) {
        builder.timeout(Duration.ofSeconds(60))
    }

    private warn(String[] lines) {
        println('*** WARNING ***')
        lines.each {
            println(it)
        }
        println()
    }
}
