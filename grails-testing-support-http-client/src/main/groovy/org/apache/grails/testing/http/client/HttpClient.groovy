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

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder

import org.apache.grails.testing.http.client.utils.XmlUtils

/**
 * Test-focused synchronous HTTP client for exercising live endpoints in integration and functional tests.
 * <p>
 * This type wraps {@link java.net.http.HttpClient} and returns {@link TestHttpResponse} for fluent assertions.
 * It is intentionally convenience-oriented, with overloads for common payload formats (JSON, XML, multipart)
 * and optional explicit client usage when tests need custom redirect, timeout, or SSL behavior.
 * <p>
 * Default behavior:
 * <ul>
 *   <li>Shared singleton JDK client for connection reuse across tests.</li>
 *   <li>Client connect timeout of 60 seconds.</li>
 *   <li>Request timeout of 60 seconds on requests built via this helper.</li>
 *   <li>Redirect policy {@code ALWAYS} for the shared singleton client.</li>
 * </ul>
 * <p>
 * Relative URLs are resolved against {@link #baseUrl}. Absolute {@code http://} and {@code https://} URLs are
 * used as-is.
 * <p>
 * Typical usage in a Grails integration test:
 * <pre>
 * import spock.lang.Specification
 *
 * import grails.testing.mixin.integration.Integration
 * import org.apache.grails.testing.http.client.HttpClient
 *
 * @Integration
 * class MySpec extends Specification {
 *
 *     @Autowired HttpClient http
 *
 *     void 'health endpoint responds OK'() {
 *         when:
 *         def response = http.get('/health')
 *
 *         then:
 *         response.expectStatus(200)
 *     }
 * }
 * </pre>
 * <p>
 * Implementing tests must provide {@code local.server.port} (in Grails typically via {@code @Integration}),
 * or otherwise set {@code baseUrl} via constructor or property before issuing relative requests.
 */
@CompileStatic
class HttpClient {

    /**
     * Base URL used to resolve relative request targets, for example {@code http://localhost:8080}.
     */
    String baseUrl

    /**
     * Shared singleton client reused across tests.
     */
    private static volatile java.net.http.HttpClient sharedClient

    private static final Map<String, String> EMPTY = Collections.emptyMap()
    private static final String APPLICATION_JSON = 'application/json'
    private static final String APPLICATION_XML = 'application/xml'
    private static final String HTTP = 'http://'
    private static final String HTTPS = 'https://'

    HttpClient(String baseUrl = null) {
        this.baseUrl = baseUrl
    }

    /**
     * @return the shared singleton JDK Http client instance, creating it on first access.
     */
    static java.net.http.HttpClient getHttpClient() {
        def client = sharedClient
        if (!client) {
            synchronized (HttpClient) {
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
     * Base URL used when resolving relative request paths.
     *
     * @return base URL such as {@code http://localhost:8080}
     * @throws IllegalStateException when no {@code baseUrl} has been set
     */
    String getBaseUrl() {
        if (!baseUrl) {
            throw new IllegalStateException('No baseUrl set')
        }
        baseUrl
    }

    // region GET HELPERS

    /**
     * GET request.
     *
     * @param pathOrUrl relative path or absolute URL
     * @return response with String body
     */
    TestHttpResponse get(CharSequence pathOrUrl) {
        get(EMPTY, pathOrUrl, null)
    }

    TestHttpResponse get(Map<String, String> headers, CharSequence pathOrUrl) {
        get(headers, pathOrUrl, null)
    }

    /**
     * GET with an explicit client and no custom headers.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param client client to execute request with
     * @return response with String body
     */
    TestHttpResponse get(CharSequence pathOrUrl, java.net.http.HttpClient client) {
        get(EMPTY, pathOrUrl, client)
    }

    /**
     * GET with explicit client and headers.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param client optional explicit client; if null, falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    TestHttpResponse get(Map<String, String> headers, CharSequence pathOrUrl, java.net.http.HttpClient client) {
        send(client, headers, requestBuilder(pathOrUrl).GET())
    }

    // endregion
    // region POST HELPERS

    /**
     * POST request with explicit content type using the shared default client.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param body request payload
     * @param contentType request {@code Content-Type}
     * @return response with String body
     */
    TestHttpResponse post(CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        post(EMPTY, pathOrUrl, body, contentType, null)
    }

    /**
     * POST request with explicit content type and explicit client.
     */
    TestHttpResponse post(CharSequence pathOrUrl, CharSequence body, CharSequence contentType, java.net.http.HttpClient client) {
        post(EMPTY, pathOrUrl, body, contentType, client)
    }

    /**
     * POST request with headers and explicit content type using the shared default client.
     */
    TestHttpResponse post(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        post(headers, pathOrUrl, body, contentType, null)
    }

    /**
     * POST request with headers, explicit content type, and explicit client.
     */
    TestHttpResponse post(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType, java.net.http.HttpClient client) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header('Content-Type', contentType.toString())
                        .POST(HttpRequest.BodyPublishers.ofString(body?.toString() ?: ''))
        )
    }

    // region POST JSON

    /**
     * POST JSON string payload with shared default client.
     */
    TestHttpResponse postJson(CharSequence pathOrUrl, CharSequence body) {
        post(EMPTY, pathOrUrl, body, APPLICATION_JSON, null)
    }

    /**
     * POST JSON string payload with explicit client.
     */
    TestHttpResponse postJson(CharSequence pathOrUrl, CharSequence body, java.net.http.HttpClient client) {
        post(EMPTY, pathOrUrl, body, APPLICATION_JSON, client)
    }

    /**
     * POST JSON string payload with custom headers.
     */
    TestHttpResponse postJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body) {
        post(headers, pathOrUrl, body, APPLICATION_JSON, null)
    }

    /**
     * POST JSON string payload with custom headers and explicit client.
     */
    TestHttpResponse postJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, java.net.http.HttpClient client) {
        post(headers, pathOrUrl, body, APPLICATION_JSON, client)
    }

    /**
     * POST JSON object payload (serialized with {@link JsonOutput#toJson(Object)}).
     */
    TestHttpResponse postJson(CharSequence pathOrUrl, Map<String, Object> body) {
        post(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    /**
     * POST JSON object payload (serialized with {@link JsonOutput#toJson(Object)}) and explicit client.
     */
    TestHttpResponse postJson(CharSequence pathOrUrl, Map<String, Object> body, java.net.http.HttpClient client) {
        post(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    /**
     * POST JSON object payload with custom headers.
     */
    TestHttpResponse postJson(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body) {
        post(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    /**
     * POST JSON object payload with custom headers and explicit client.
     */
    TestHttpResponse postJson(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body, java.net.http.HttpClient client) {
        post(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    // endregion
    // region POST XML

    /**
     * POST XML generated by the provided markup DSL closure.
     */
    TestHttpResponse postXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body) {
        post(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse postXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            java.net.http.HttpClient client
    ) {
        post(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    TestHttpResponse postXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        post(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse postXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            java.net.http.HttpClient client
    ) {
        post(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    // endregion
    // region POST MULTIPART

    /**
     * POST multipart payload.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param body prebuilt multipart payload
     * @return response with String body
     */
    TestHttpResponse postMultipart(CharSequence pathOrUrl, MultipartBody body) {
        postMultipart(EMPTY, pathOrUrl, body, null)
    }

    /**
     * POST multipart payload.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param body prebuilt multipart payload
     * @param client explicit client; if null, falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    TestHttpResponse postMultipart(CharSequence pathOrUrl, MultipartBody body, java.net.http.HttpClient client) {
        postMultipart(EMPTY, pathOrUrl, body, client)
    }

    /**
     * POST multipart payload.
     *
     * @param headers optional extra request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body prebuilt multipart payload
     * @return response with String body
     */
    TestHttpResponse postMultipart(Map<String, String> headers, CharSequence pathOrUrl, MultipartBody body) {
        postMultipart(headers, pathOrUrl, body, null)
    }

    /**
     * POST multipart payload.
     *
     * @param headers optional extra request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body prebuilt multipart payload
     * @param client explicit client; if null, falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    TestHttpResponse postMultipart(Map<String, String> headers, CharSequence pathOrUrl, MultipartBody body, java.net.http.HttpClient client) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header('Content-Type', body.contentType)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes))
        )
    }

    // endregion
    // endregion
    // region PUT HELPERS

    /**
     * PUT request with explicit content type using the shared default client.
     */
    TestHttpResponse put(CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        put(EMPTY, pathOrUrl, body, contentType, null)
    }

    /**
     * PUT request with explicit content type and explicit client.
     */
    TestHttpResponse put(CharSequence pathOrUrl, CharSequence body, CharSequence contentType, java.net.http.HttpClient client) {
        put(EMPTY, pathOrUrl, body, contentType, client)
    }

    /**
     * PUT request with headers and explicit content type using the shared default client.
     */
    TestHttpResponse put(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        put(headers, pathOrUrl, body, contentType, null)
    }

    /**
     * PUT request with headers, explicit content type, and explicit client.
     */
    TestHttpResponse put(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType, java.net.http.HttpClient client) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header('Content-Type', contentType.toString())
                        .PUT(HttpRequest.BodyPublishers.ofString(body?.toString() ?: ''))
        )
    }

    // region PUT JSON

    /**
     * PUT JSON string payload with shared default client.
     */
    TestHttpResponse putJson(CharSequence pathOrUrl, CharSequence body) {
        put(EMPTY, pathOrUrl, body, APPLICATION_JSON, null)
    }

    TestHttpResponse putJson(CharSequence pathOrUrl, CharSequence body, java.net.http.HttpClient client) {
        put(EMPTY, pathOrUrl, body, APPLICATION_JSON, client)
    }

    TestHttpResponse putJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body) {
        put(headers, pathOrUrl, body, APPLICATION_JSON, null)
    }

    TestHttpResponse putJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, java.net.http.HttpClient client) {
        put(headers, pathOrUrl, body, APPLICATION_JSON, client)
    }

    TestHttpResponse putJson(CharSequence pathOrUrl, Map<String, Object> body) {
        put(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    TestHttpResponse putJson(CharSequence pathOrUrl, Map<String, Object> body, java.net.http.HttpClient client) {
        put(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    TestHttpResponse putJson(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body) {
        put(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    TestHttpResponse putJson(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body, java.net.http.HttpClient client) {
        put(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    // endregion
    // region PUT XML

    /**
     * PUT XML generated by the provided markup DSL closure.
     */
    TestHttpResponse putXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body) {
        put(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse putXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            java.net.http.HttpClient client
    ) {
        put(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    TestHttpResponse putXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        put(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse putXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            java.net.http.HttpClient client
    ) {
        put(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    // endregion
    // endregion
    // region PATCH HELPERS

    /**
     * PATCH request with explicit content type using the shared default client.
     */
    TestHttpResponse patch(CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        patch(EMPTY, pathOrUrl, body, contentType, null)
    }

    /**
     * PATCH request with explicit content type and explicit client.
     */
    TestHttpResponse patch(CharSequence pathOrUrl, CharSequence body, CharSequence contentType, java.net.http.HttpClient client) {
        patch(EMPTY, pathOrUrl, body, contentType, client)
    }

    /**
     * PATCH request with headers and explicit content type using the shared default client.
     */
    TestHttpResponse patch(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        patch(headers, pathOrUrl, body, contentType, null)
    }

    /**
     * PATCH request with headers, explicit content type, and explicit client.
     */
    TestHttpResponse patch(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType, java.net.http.HttpClient client) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header('Content-Type', contentType.toString())
                        .method('PATCH', HttpRequest.BodyPublishers.ofString(body?.toString() ?: ''))
        )
    }

    // region PATCH JSON

    /**
     * PATCH JSON object payload (serialized with {@link JsonOutput#toJson(Object)}).
     */
    TestHttpResponse patchJson(CharSequence pathOrUrl, Map<String, Object> body) {
        patch(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    /**
     * PATCH JSON object payload (serialized with {@link JsonOutput#toJson(Object)}) and explicit client.
     */
    TestHttpResponse patchJson(CharSequence pathOrUrl, Map<String, Object> body, java.net.http.HttpClient client) {
        patch(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    /**
     * PATCH JSON object payload with custom headers using the shared default client.
     */
    TestHttpResponse patch(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body) {
        patch(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    /**
     * PATCH JSON object payload with custom headers and explicit client.
     */
    TestHttpResponse patch(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body, java.net.http.HttpClient client) {
        patch(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    // region PATCH JSON

    /**
     * PATCH JSON string payload with shared default client.
     */
    TestHttpResponse patchJson(CharSequence pathOrUrl, CharSequence body) {
        patch(EMPTY, pathOrUrl, body, APPLICATION_JSON, null)
    }

    TestHttpResponse patchJson(CharSequence pathOrUrl, CharSequence body, java.net.http.HttpClient client) {
        patch(EMPTY, pathOrUrl, body, APPLICATION_JSON, client)
    }

    TestHttpResponse patchJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body) {
        patch(headers, pathOrUrl, body, APPLICATION_JSON, null)
    }

    TestHttpResponse patchJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, java.net.http.HttpClient client) {
        patch(headers, pathOrUrl, body, APPLICATION_JSON, client)
    }

    // endregion
    // region PATCH XML

    /**
     * PATCH XML generated by the provided markup DSL closure.
     */
    TestHttpResponse patchXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body) {
        patch(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse patchXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            java.net.http.HttpClient client
    ) {
        patch(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    TestHttpResponse patchXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        patch(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse patchXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            java.net.http.HttpClient client
    ) {
        patch(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    // endregion
    // endregion
    // region DELETE HELPERS

    /**
     * DELETE request using the shared default client.
     */
    TestHttpResponse delete(CharSequence pathOrUrl) {
        delete(EMPTY, pathOrUrl, null)
    }

    /**
     * DELETE request with explicit client.
     */
    TestHttpResponse delete(CharSequence pathOrUrl, java.net.http.HttpClient client) {
        delete(EMPTY, pathOrUrl, client)
    }

    /**
     * DELETE request with custom headers using the shared default client.
     */
    TestHttpResponse delete(Map<String, String> headers, CharSequence pathOrUrl) {
        delete(headers, pathOrUrl, null)
    }

    /**
     * DELETE request with custom headers and explicit client.
     */
    TestHttpResponse delete(Map<String, String> headers, CharSequence pathOrUrl, java.net.http.HttpClient client) {
        send(client, headers, requestBuilder(pathOrUrl).DELETE())
    }

    // endregion
    // region OPTIONS HELPERS

    /**
     * OPTIONS request using the shared default client.
     */
    TestHttpResponse options(CharSequence pathOrUrl) {
        options(EMPTY, pathOrUrl, null)
    }

    /**
     * OPTIONS request with explicit client.
     */
    TestHttpResponse options(CharSequence pathOrUrl, java.net.http.HttpClient client) {
        options(EMPTY, pathOrUrl, client)
    }

    /**
     * OPTIONS request with custom headers using the shared default client.
     */
    TestHttpResponse options(Map<String, String> headers, CharSequence pathOrUrl) {
        options(headers, pathOrUrl, null)
    }

    /**
     * OPTIONS request with custom headers and explicit client.
     */
    TestHttpResponse options(Map<String, String> headers, CharSequence pathOrUrl, java.net.http.HttpClient client) {
        send(client, headers, requestBuilder(pathOrUrl).method('OPTIONS', HttpRequest.BodyPublishers.noBody()))
    }

    // endregion
    // region GENERAL PUBLIC HELPERS

    /**
     * Sends a pre-built request.
     *
     * @param request request to send
     * @param client optional explicit client; falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    static TestHttpResponse sendRequest(HttpRequest request, java.net.http.HttpClient client = null) {
        if (!request.timeout().isPresent()) {
            warn("Sending HttpRequest to [${request.uri()}] without timeout set.",
                    "Offending class is [${getClass().name}].",
                    'Consider using requestWith() or setting timeout(...) on your custom request.')
        }
        send(client, request)
    }

    /**
     * Creates an {@link java.net.http.HttpClient} with default configuration and optional customizations.
     *
     * @param configurer optional http builder configurer closure
     * @return built client
     */
    static java.net.http.HttpClient newClientWith(
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = java.net.http.HttpClient.Builder)
                    Closure<?> configurer = null
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
     * <p>
     * Requests built through this helper always start with a timeout of 60 seconds and can be
     * overridden in {@code configurer}.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param configurer optional request builder configurer closure
     * @return built request
     */
    HttpRequest requestWith(
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

    // endregion
    // region PRIVATE HELPERS & UTILS

    private static TestHttpResponse send(java.net.http.HttpClient client, Map<String, String> headers, HttpRequest.Builder requestBuilder) {
        headers.each { k, v -> requestBuilder.header(k, v) }
        send(client, requestBuilder.build())
    }

    private static TestHttpResponse send(java.net.http.HttpClient client, HttpRequest request) {
        if (client && !client.connectTimeout().isPresent()) {
            warn("Using HttpClient without connect timeout set when connecting to [${request.uri()}].",
                    "Offending class is [${getClass().name}].",
                    'Consider using newClientWith() or setting connectTimeout(...) on your custom client.')
        }
        def response = (client ?: httpClient).send(request, HttpResponse.BodyHandlers.ofString())
        TestHttpResponse.wrap(response)
    }

    /**
     * Resolve a relative path (for example {@code /health}) against {@link #getBaseUrl()}.
     * Absolute HTTP/HTTPS URLs are returned as-is.
     *
     * @param pathOrUrl relative path or absolute URL
     * @return resolved request URI
     */
    private URI resolveHttpUri(CharSequence pathOrUrl) {
        if (pathOrUrl == null) {
            throw new IllegalArgumentException('pathOrUrl must not be null')
        }
        def str = pathOrUrl as String
        if (!isRelativeUrl(str)) {
            return URI.create(str)
        }
        return URI.create(normalizeUrl(getBaseUrl(), str))
    }

    private static boolean isRelativeUrl(String url) {
        !url.startsWith(HTTP) && !url.startsWith(HTTPS)
    }

    private static String normalizeUrl(String base, String path) {
        def b = base.endsWith('/') ? base[0..-2] : base
        def p = path.startsWith('/') ? path : "/$path"
        "$b$p"
    }

    private HttpRequest.Builder requestBuilder(CharSequence pathOrUrl) {
        def builder = HttpRequest.newBuilder(resolveHttpUri(pathOrUrl))
        setDefaultRequestConfig(builder)
        builder
    }

    private static java.net.http.HttpClient initClient() {
        initClientBuilder().build()
    }

    private static java.net.http.HttpClient.Builder initClientBuilder() {
        java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
    }

    private static void setDefaultRequestConfig(HttpRequest.Builder builder) {
        builder.timeout(Duration.ofSeconds(60))
    }

    private static warn(String[] lines) {
        println('*** WARNING ***')
        lines.each {
            println(it)
        }
        println()
    }

    // endregion
}
