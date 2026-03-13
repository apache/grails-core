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
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder

import org.springframework.beans.factory.annotation.Value

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
 * Relative URLs are resolved against {@link #getHttpBaseUrl}. Absolute {@code http://} and {@code https://} URLs are
 * used as-is.
 * <p>
 * Typical usage in a Grails integration test:
 * <pre>
 * import spock.lang.Specification
 *
 * import grails.testing.mixin.integration.Integration
 * import org.apache.grails.testing.http.client.HttpClientSupport
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
 * or otherwise override {@code getBaseUrl()}.
 *
 * @since 7.0.9
 */
@CompileStatic
trait HttpClientSupport {

    @Value('${local.server.port}')
    int httpLocalServerPort = -1

    @Value('${server.servlet.context-path:/}')
    String httpContextPath

    /**
     * Base URL used to resolve relative request targets, for example {@code http://localhost:8080}.
     */
    private String resolvedBaseUrl

    /**
     * Shared singleton client reused across tests.
     */
    private static volatile HttpClient sharedClient

    private static final Map<String, String> EMPTY = Collections.emptyMap()
    private static final String APPLICATION_JSON = 'application/json'
    private static final String APPLICATION_XML = 'application/xml'
    private static final String HTTP = 'http://'
    private static final String HTTPS = 'https://'

    /**
     * @return the shared singleton JDK Http client instance, creating it on first access.
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

    String getResolvedBaseUrl() {
        resolvedBaseUrl ?: resolveBaseUrl()
    }

    private String resolveBaseUrl() {
        if (httpLocalServerPort == -1) {
            return null
        }
        def path = httpContextPath?.trim()
        path = (!path || path == '/') ? '' : path.startsWith('/') ? path : "/$path"
        resolvedBaseUrl = "http://localhost:$httpLocalServerPort$path"
    }

    /**
     * Base URL used when resolving relative request paths.
     *
     * @return base URL such as {@code http://localhost:8080}
     * @throws IllegalStateException if value was not initialized correctly
     */
    String getHttpBaseUrl() {
        def baseUrl = getResolvedBaseUrl()
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
    TestHttpResponse http(CharSequence pathOrUrl) {
        http(EMPTY, pathOrUrl, null)
    }

    TestHttpResponse http(Map<String, String> headers, CharSequence pathOrUrl) {
        http(headers, pathOrUrl, null)
    }

    /**
     * GET with an explicit client and no custom headers.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param client client to execute request with
     * @return response with String body
     */
    TestHttpResponse http(CharSequence pathOrUrl, HttpClient client) {
        http(EMPTY, pathOrUrl, client)
    }

    /**
     * GET with explicit client and headers.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param client optional explicit client; if null, falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    TestHttpResponse http(Map<String, String> headers, CharSequence pathOrUrl, HttpClient client) {
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
    TestHttpResponse httpPost(CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        httpPost(EMPTY, pathOrUrl, body, contentType, null)
    }

    /**
     * POST request with explicit content type and explicit client.
     */
    TestHttpResponse httpPost(CharSequence pathOrUrl, CharSequence body, CharSequence contentType, HttpClient client) {
        httpPost(EMPTY, pathOrUrl, body, contentType, client)
    }

    /**
     * POST request with headers and explicit content type using the shared default client.
     */
    TestHttpResponse httpPost(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        httpPost(headers, pathOrUrl, body, contentType, null)
    }

    /**
     * POST request with headers, explicit content type, and explicit client.
     */
    TestHttpResponse httpPost(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType, HttpClient client) {
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
    TestHttpResponse httpPostJson(CharSequence pathOrUrl, CharSequence body) {
        httpPost(EMPTY, pathOrUrl, body, APPLICATION_JSON, null)
    }

    /**
     * POST JSON string payload with explicit client.
     */
    TestHttpResponse httpPostJson(CharSequence pathOrUrl, CharSequence body, HttpClient client) {
        httpPost(EMPTY, pathOrUrl, body, APPLICATION_JSON, client)
    }

    /**
     * POST JSON string payload with custom headers.
     */
    TestHttpResponse httpPostJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body) {
        httpPost(headers, pathOrUrl, body, APPLICATION_JSON, null)
    }

    /**
     * POST JSON string payload with custom headers and explicit client.
     */
    TestHttpResponse httpPostJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, HttpClient client) {
        httpPost(headers, pathOrUrl, body, APPLICATION_JSON, client)
    }

    /**
     * POST JSON object payload (serialized with {@link JsonOutput#toJson(Object)}).
     */
    TestHttpResponse httpPostJson(CharSequence pathOrUrl, Map<String, Object> body) {
        httpPost(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    /**
     * POST JSON object payload (serialized with {@link JsonOutput#toJson(Object)}) and explicit client.
     */
    TestHttpResponse httpPostJson(CharSequence pathOrUrl, Map<String, Object> body, HttpClient client) {
        httpPost(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    /**
     * POST JSON object payload with custom headers.
     */
    TestHttpResponse httpPostJson(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body) {
        httpPost(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    /**
     * POST JSON object payload with custom headers and explicit client.
     */
    TestHttpResponse httpPostJson(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body, HttpClient client) {
        httpPost(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    // endregion
    // region POST XML

    /**
     * POST XML generated by the provided markup DSL closure.
     */
    TestHttpResponse httpPostXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body) {
        httpPost(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse httpPostXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPost(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    TestHttpResponse httpPostXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        httpPost(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse httpPostXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPost(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
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
    TestHttpResponse httpPostMultipart(CharSequence pathOrUrl, MultipartBody body) {
        httpPostMultipart(EMPTY, pathOrUrl, body, null)
    }

    /**
     * POST multipart payload.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param body prebuilt multipart payload
     * @param client explicit client; if null, falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    TestHttpResponse httpPostMultipart(CharSequence pathOrUrl, MultipartBody body, HttpClient client) {
        httpPostMultipart(EMPTY, pathOrUrl, body, client)
    }

    /**
     * POST multipart payload.
     *
     * @param headers optional extra request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body prebuilt multipart payload
     * @return response with String body
     */
    TestHttpResponse httpPostMultipart(Map<String, String> headers, CharSequence pathOrUrl, MultipartBody body) {
        httpPostMultipart(headers, pathOrUrl, body, null)
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
    TestHttpResponse httpPostMultipart(Map<String, String> headers, CharSequence pathOrUrl, MultipartBody body, HttpClient client) {
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
    TestHttpResponse httpPut(CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        httpPut(EMPTY, pathOrUrl, body, contentType, null)
    }

    /**
     * PUT request with explicit content type and explicit client.
     */
    TestHttpResponse httpPut(CharSequence pathOrUrl, CharSequence body, CharSequence contentType, HttpClient client) {
        httpPut(EMPTY, pathOrUrl, body, contentType, client)
    }

    /**
     * PUT request with headers and explicit content type using the shared default client.
     */
    TestHttpResponse httpPut(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        httpPut(headers, pathOrUrl, body, contentType, null)
    }

    /**
     * PUT request with headers, explicit content type, and explicit client.
     */
    TestHttpResponse httpPut(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType, HttpClient client) {
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
    TestHttpResponse httpPutJson(CharSequence pathOrUrl, CharSequence body) {
        httpPut(EMPTY, pathOrUrl, body, APPLICATION_JSON, null)
    }

    TestHttpResponse httpPutJson(CharSequence pathOrUrl, CharSequence body, HttpClient client) {
        httpPut(EMPTY, pathOrUrl, body, APPLICATION_JSON, client)
    }

    TestHttpResponse httpPutJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body) {
        httpPut(headers, pathOrUrl, body, APPLICATION_JSON, null)
    }

    TestHttpResponse httpPutJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, HttpClient client) {
        httpPut(headers, pathOrUrl, body, APPLICATION_JSON, client)
    }

    TestHttpResponse httpPutJson(CharSequence pathOrUrl, Map<String, Object> body) {
        httpPut(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    TestHttpResponse httpPutJson(CharSequence pathOrUrl, Map<String, Object> body, HttpClient client) {
        httpPut(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    TestHttpResponse httpPutJson(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body) {
        httpPut(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    TestHttpResponse httpPutJson(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body, HttpClient client) {
        httpPut(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    // endregion
    // region PUT XML

    /**
     * PUT XML generated by the provided markup DSL closure.
     */
    TestHttpResponse httpPutXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body) {
        httpPut(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse httpPutXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPut(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    TestHttpResponse httpPutXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        httpPut(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse httpPutXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPut(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    // endregion
    // endregion
    // region PATCH HELPERS

    /**
     * PATCH request with explicit content type using the shared default client.
     */
    TestHttpResponse httpPatch(CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        httpPatch(EMPTY, pathOrUrl, body, contentType, null)
    }

    /**
     * PATCH request with explicit content type and explicit client.
     */
    TestHttpResponse httpPatch(CharSequence pathOrUrl, CharSequence body, CharSequence contentType, HttpClient client) {
        httpPatch(EMPTY, pathOrUrl, body, contentType, client)
    }

    /**
     * PATCH request with headers and explicit content type using the shared default client.
     */
    TestHttpResponse httpPatch(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType) {
        httpPatch(headers, pathOrUrl, body, contentType, null)
    }

    /**
     * PATCH request with headers, explicit content type, and explicit client.
     */
    TestHttpResponse httpPatch(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, CharSequence contentType, HttpClient client) {
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
    TestHttpResponse httpPatchJson(CharSequence pathOrUrl, Map<String, Object> body) {
        httpPatch(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    /**
     * PATCH JSON object payload (serialized with {@link JsonOutput#toJson(Object)}) and explicit client.
     */
    TestHttpResponse httpPatchJson(CharSequence pathOrUrl, Map<String, Object> body, HttpClient client) {
        httpPatch(EMPTY, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    /**
     * PATCH JSON object payload with custom headers using the shared default client.
     */
    TestHttpResponse httpPatchJson(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body) {
        httpPatch(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, null)
    }

    /**
     * PATCH JSON object payload with custom headers and explicit client.
     */
    TestHttpResponse httpPatchJson(Map<String, String> headers, CharSequence pathOrUrl, Map<String, Object> body, HttpClient client) {
        httpPatch(headers, pathOrUrl, JsonOutput.toJson(body), APPLICATION_JSON, client)
    }

    // region PATCH JSON

    /**
     * PATCH JSON string payload with shared default client.
     */
    TestHttpResponse httpPatchJson(CharSequence pathOrUrl, CharSequence body) {
        httpPatch(EMPTY, pathOrUrl, body, APPLICATION_JSON, null)
    }

    TestHttpResponse httpPatchJson(CharSequence pathOrUrl, CharSequence body, HttpClient client) {
        httpPatch(EMPTY, pathOrUrl, body, APPLICATION_JSON, client)
    }

    TestHttpResponse httpPatchJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body) {
        httpPatch(headers, pathOrUrl, body, APPLICATION_JSON, null)
    }

    TestHttpResponse httpPatchJson(Map<String, String> headers, CharSequence pathOrUrl, CharSequence body, HttpClient client) {
        httpPatch(headers, pathOrUrl, body, APPLICATION_JSON, client)
    }

    // endregion
    // region PATCH XML

    /**
     * PATCH XML generated by the provided markup DSL closure.
     */
    TestHttpResponse httpPatchXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body) {
        httpPatch(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse httpPatchXml(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPatch(EMPTY, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    TestHttpResponse httpPatchXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        httpPatch(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, null)
    }

    TestHttpResponse httpPatchXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPatch(headers, pathOrUrl, XmlUtils.toXml(body), APPLICATION_XML, client)
    }

    // endregion
    // endregion
    // region DELETE HELPERS

    /**
     * DELETE request using the shared default client.
     */
    TestHttpResponse httpDelete(CharSequence pathOrUrl) {
        httpDelete(EMPTY, pathOrUrl, null)
    }

    /**
     * DELETE request with explicit client.
     */
    TestHttpResponse httpDelete(CharSequence pathOrUrl, HttpClient client) {
        httpDelete(EMPTY, pathOrUrl, client)
    }

    /**
     * DELETE request with custom headers using the shared default client.
     */
    TestHttpResponse httpDelete(Map<String, String> headers, CharSequence pathOrUrl) {
        httpDelete(headers, pathOrUrl, null)
    }

    /**
     * DELETE request with custom headers and explicit client.
     */
    TestHttpResponse httpDelete(Map<String, String> headers, CharSequence pathOrUrl, HttpClient client) {
        send(client, headers, requestBuilder(pathOrUrl).DELETE())
    }

    // endregion
    // region OPTIONS HELPERS

    /**
     * OPTIONS request using the shared default client.
     */
    TestHttpResponse httpOptions(CharSequence pathOrUrl) {
        httpOptions(EMPTY, pathOrUrl, null)
    }

    /**
     * OPTIONS request with explicit client.
     */
    TestHttpResponse httpOptions(CharSequence pathOrUrl, HttpClient client) {
        httpOptions(EMPTY, pathOrUrl, client)
    }

    /**
     * OPTIONS request with custom headers using the shared default client.
     */
    TestHttpResponse httpOptions(Map<String, String> headers, CharSequence pathOrUrl) {
        httpOptions(headers, pathOrUrl, null)
    }

    /**
     * OPTIONS request with custom headers and explicit client.
     */
    TestHttpResponse httpOptions(Map<String, String> headers, CharSequence pathOrUrl, HttpClient client) {
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
    TestHttpResponse sendHttpRequest(HttpRequest request, HttpClient client = null) {
        if (!request.timeout().isPresent()) {
            warn(
                    "Sending HttpRequest to [${request.uri()}] without timeout set.",
                    "Offending class is [${getClass().name}].",
                    'Consider using requestWith() or setting timeout(...) on your custom request.'
            )
        }
        send(client, request)
    }

    /**
     * Creates an {@link java.net.http.HttpClient} with default configuration and optional customizations.
     *
     * @param configurer optional http builder configurer closure
     * @return built client
     */
    HttpClient httpClientWith(
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = HttpClient.Builder)
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
    HttpRequest httpRequestWith(
            CharSequence pathOrUrl,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = HttpRequest.Builder)
                    Closure<?> configurer = null
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

    private TestHttpResponse send(HttpClient client, Map<String, String> headers, HttpRequest.Builder requestBuilder) {
        headers.each { k, v -> requestBuilder.header(k, v) }
        send(client, requestBuilder.build())
    }

    private TestHttpResponse send(HttpClient client, HttpRequest request) {
        if (client && !client.connectTimeout().isPresent()) {
            warn(
                    "Using HttpClient without connect timeout set when connecting to [${request.uri()}].",
                    "Offending class is [${getClass().name}].",
                    'Consider using newClientWith() or setting connectTimeout(...) on your custom client.'
            )
        }
        def response = (client ?: httpClient).send(request, HttpResponse.BodyHandlers.ofString())
        TestHttpResponse.wrap(response)
    }

    /**
     * Resolve a relative path (for example {@code /health}) against {@link #getHttpBaseUrl()}.
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
        return URI.create(normalizeUrl(getHttpBaseUrl(), str))
    }

    private boolean isRelativeUrl(String url) {
        !url.startsWith(HTTP) && !url.startsWith(HTTPS)
    }

    private String normalizeUrl(String base, String path) {
        def b = base.endsWith('/') ? base[0..-2] : base
        def p = path.startsWith('/') ? path : "/$path"
        "$b$p"
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

    private void warn(String[] lines) {
        println('*** WARNING ***')
        lines.each {
            println(it)
        }
        println()
    }

    // endregion
}
