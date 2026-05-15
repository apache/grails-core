/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * 'License'); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.http.client

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import groovy.json.JsonGenerator
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
 *         response.assertStatus(200)
 *     }
 * }
 * </pre>
 * <p>
 * Implementing tests must provide {@code local.server.port} (in Grails typically via {@code @Integration}),
 * or otherwise override {@link #getHttpBaseUrl}.
 *
 * @since 7.0.10
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
    private static final Object HTTP_CLIENT_SYNC_LOCK = new Object()

    private static final Map<String, String> EMPTY = Collections.emptyMap()
    private static final String APPLICATION_JSON = 'application/json'
    private static final String APPLICATION_XML = 'application/xml'
    private static final String CONTENT_TYPE = 'Content-Type'
    private static final String HTTP = 'http://'
    private static final String HTTPS = 'https://'

    private static final String HEAD = 'HEAD'
    private static final String OPTIONS = 'OPTIONS'
    private static final String PATCH = 'PATCH'
    private static final String TRACE = 'TRACE'

    /**
     * @return the shared singleton JDK Http client instance, creating it on first access.
     */
    HttpClient getHttpClient() {
        def client = sharedClient
        if (!client) {
            synchronized (HTTP_CLIENT_SYNC_LOCK) {
                client = sharedClient
                if (!client) {
                    client = initClient()
                    sharedClient = client
                }
            }
        }
        client
    }

    private String getResolvedBaseUrl() {
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

    /**
     * GET request.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param client optional {@link HttpClient}; falls back to default client
     * @return response with String body
     */
    TestHttpResponse http(CharSequence pathOrUrl, HttpClient client) {
        http(EMPTY, pathOrUrl, client)
    }

    /**
     * GET request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @return response with String body
     */
    TestHttpResponse http(Map<String, String> headers, CharSequence pathOrUrl) {
        http(headers, pathOrUrl, null)
    }

    /**
     * GET request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param client optional {@link HttpClient}; falls back to default client
     * @return response with String body
     */
    TestHttpResponse http(Map<String, String> headers, CharSequence pathOrUrl, HttpClient client) {
        send(client, headers, requestBuilder(pathOrUrl).GET())
    }

    // endregion
    // region POST HELPERS

    /**
     * POST request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body request payload
     * @param contentType request {@code Content-Type}
     * @param client optional client; if null, falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    TestHttpResponse httpPost(
            Map<String, String> headers = EMPTY,
            CharSequence pathOrUrl,
            CharSequence body,
            CharSequence contentType,
            HttpClient client = null
    ) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header(CONTENT_TYPE, contentType.toString())
                        .POST(HttpRequest.BodyPublishers.ofString(body?.toString() ?: ''))
        )
    }

    // region POST JSON

    /**
     * POST JSON string payload.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body request payload
     * @param client optional client; if null, falls back to {@link #getHttpClient()}
     * @return response with String body
     */
    TestHttpResponse httpPostJson(
            Map<String, String> headers = EMPTY,
            CharSequence pathOrUrl,
            CharSequence body,
            HttpClient client = null
    ) {
        httpPost(headers, pathOrUrl, body, APPLICATION_JSON, client)
    }

    /**
     * POST JSON payload from {@link Map}.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link groovy.json.JsonGenerator} for custom formatting
     * @param body request payload
     * @return response with String body
     */
    TestHttpResponse httpPostJson(
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body
    ) {
        httpPostJson(EMPTY, pathOrUrl, jsonGenerator, body, null)
    }

    /**
     * POST JSON payload from {@link Map}.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link groovy.json.JsonGenerator} for custom formatting
     * @param body request payload
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpPostJson(
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body,
            HttpClient client
    ) {
        httpPostJson(EMPTY, pathOrUrl, jsonGenerator, body, client)
    }

    /**
     * POST JSON payload from {@link Map}.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link groovy.json.JsonGenerator} for custom formatting
     * @param body request payload
     * @return response with String body
     */
    TestHttpResponse httpPostJson(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body
    ) {
        httpPostJson(headers, pathOrUrl, jsonGenerator, body, null)
    }

    /**
     * POST JSON payload from {@link Map}.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body request payload
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpPostJson(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body,
            HttpClient client
    ) {
        def json = jsonGenerator ? jsonGenerator.toJson(body) : JsonOutput.toJson(body)
        httpPost(headers, pathOrUrl, json, APPLICATION_JSON, client)
    }

    // endregion
    // region POST XML

    /**
     * POST XML string payload.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body request payload
     * @param client optional {@link HttpClient}; falls back to default client
     * @return response with String body
     */
    TestHttpResponse httpPostXml(
            Map<String, String> headers = EMPTY,
            CharSequence pathOrUrl,
            CharSequence body,
            HttpClient client = null
    ) {
        httpPost(headers, pathOrUrl, body, APPLICATION_XML, client)
    }

    /**
     * POST XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @return response with String body
     */
    TestHttpResponse httpPostXml(
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        httpPostXml(EMPTY, pathOrUrl, format, body, null)
    }

    /**
     * POST XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpPostXml(
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPostXml(EMPTY, pathOrUrl, format, body, client)
    }

    /**
     * POST XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @return response with String body
     */
    TestHttpResponse httpPostXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        httpPostXml(headers, pathOrUrl, format, body, null)
    }

    /**
     * POST XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpPostXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPost(headers, pathOrUrl, XmlUtils.toXml(format, body), APPLICATION_XML, client)
    }

    // endregion
    // region POST MULTIPART

    /**
     * POST multipart payload.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param body prebuilt {@link MultipartBody} payload
     * @param client {@link HttpClient} instance to use
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
     * @param body prebuilt {@link MultipartBody} payload
     * @param client optional {@link HttpClient} instance; falls back default client
     * @return response with String body
     */
    TestHttpResponse httpPostMultipart(
            Map<String, String> headers = EMPTY,
            CharSequence pathOrUrl,
            MultipartBody body,
            HttpClient client = null
    ) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header(CONTENT_TYPE, body.contentType)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes))
        )
    }

    // endregion
    // endregion
    // region PUT HELPERS

    /**
     * PUT request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body request payload
     * @param contentType request {@code Content-Type}
     * @param client optional {@link HttpClient} instance; falls back default client
     * @return response with String body
     */
    TestHttpResponse httpPut(
            Map<String, String> headers = EMPTY,
            CharSequence pathOrUrl,
            CharSequence body,
            CharSequence contentType,
            HttpClient client = null
    ) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header(CONTENT_TYPE, contentType.toString())
                        .PUT(HttpRequest.BodyPublishers.ofString(body?.toString() ?: ''))
        )
    }

    // region PUT JSON

    /**
     * PUT JSON string payload.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body request payload
     * @param client optional {@link HttpClient} instance; falls back to default client
     * @return response with String body
     */
    TestHttpResponse httpPutJson(
            Map<String, String> headers = EMPTY,
            CharSequence pathOrUrl,
            CharSequence body,
            HttpClient client = null
    ) {
        httpPut(headers, pathOrUrl, body, APPLICATION_JSON, client)
    }

    /**
     * PUT JSON payload from {@link Map}.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link JsonGenerator} for custom formatting
     * @param body request payload
     * @return response with String body
     */
    TestHttpResponse httpPutJson(
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body
    ) {
        httpPutJson(EMPTY, pathOrUrl, jsonGenerator, body, null)
    }

    /**
     * PUT JSON payload from {@link Map}.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link JsonGenerator} for custom formatting
     * @param body request payload
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpPutJson(
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body,
            HttpClient client
    ) {
        httpPutJson(EMPTY, pathOrUrl, jsonGenerator, body, client)
    }

    /**
     * PUT JSON payload from {@link Map}.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link JsonGenerator} for custom formatting
     * @param body request payload
     * @return response with String body
     */
    TestHttpResponse httpPutJson(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body
    ) {
        httpPutJson(headers, pathOrUrl, jsonGenerator, body, null)
    }

    /**
     * PUT JSON payload from {@link Map}.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link JsonGenerator} for custom formatting
     * @param body request payload
     * @param client optional {@link HttpClient} instance; falls back to default client
     * @return response with String body
     */
    TestHttpResponse httpPutJson(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body,
            HttpClient client
    ) {
        def json = jsonGenerator ? jsonGenerator.toJson(body) : JsonOutput.toJson(body)
        httpPutJson(headers, pathOrUrl, json, client)
    }

    // endregion
    // region PUT XML

    /**
     * PUT XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @return response with String body
     */
    TestHttpResponse httpPutXml(
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        httpPutXml(EMPTY, pathOrUrl, format, body, null)
    }

    /**
     * PUT XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpPutXml(
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPutXml(EMPTY, pathOrUrl, format, body, client)
    }

    /**
     * PUT XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @return response with String body
     */
    TestHttpResponse httpPutXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        httpPutXml(headers, pathOrUrl, format, body, null)
    }

    /**
     * PUT XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpPutXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPut(headers, pathOrUrl, XmlUtils.toXml(format, body), APPLICATION_XML, client)
    }

    // endregion
    // endregion
    // region PATCH HELPERS

    /**
     * PATCH request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body request payload
     * @param contentType request {@code Content-Type}
     * @param client optional {@link HttpClient} instance; falls back default client
     * @return response with String body
     */
    TestHttpResponse httpPatch(
            Map<String, String> headers = EMPTY,
            CharSequence pathOrUrl,
            CharSequence body,
            CharSequence contentType,
            HttpClient client = null
    ) {
        send(client, headers,
                requestBuilder(pathOrUrl)
                        .header(CONTENT_TYPE, contentType.toString())
                        .method(PATCH, HttpRequest.BodyPublishers.ofString(body?.toString() ?: ''))
        )
    }

    // region PATCH JSON

    /**
     * PATCH JSON string payload.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param body request payload
     * @param client optional {@link HttpClient} instance; falls back to default client
     * @return response with String body
     */
    TestHttpResponse httpPatchJson(
            Map<String, String> headers = EMPTY,
            CharSequence pathOrUrl,
            CharSequence body,
            HttpClient client = null
    ) {
        httpPatch(headers, pathOrUrl, body, APPLICATION_JSON, client)
    }

    /**
     * PATCH JSON payload from {@link Map}.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link JsonGenerator} for custom formatting
     * @param body request payload
     * @return response with String body
     */
    TestHttpResponse httpPatchJson(
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body
    ) {
        httpPatchJson(EMPTY, pathOrUrl, jsonGenerator, body, null)
    }

    /**
     * PATCH JSON payload from {@link Map}.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link JsonGenerator} for custom formatting
     * @param body request payload
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpPatchJson(
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body,
            HttpClient client
    ) {
        httpPatchJson(EMPTY, pathOrUrl, jsonGenerator, body, client)
    }

    /**
     * PATCH JSON payload from {@link Map}.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link JsonGenerator} for custom formatting
     * @param body request payload
     * @return response with String body
     */
    TestHttpResponse httpPatchJson(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body
    ) {
        httpPatchJson(headers, pathOrUrl, jsonGenerator, body, null)
    }

    /**
     * PATCH JSON payload from {@link Map}.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param jsonGenerator optional {@link JsonGenerator} for custom formatting
     * @param body request payload
     * @param client optional {@link HttpClient} instance; falls back to default client
     * @return response with String body
     */
    TestHttpResponse httpPatchJson(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            JsonGenerator jsonGenerator = null,
            Map<String, Object> body,
            HttpClient client
    ) {
        def json = jsonGenerator ? jsonGenerator.toJson(body) : JsonOutput.toJson(body)
        httpPatchJson(headers, pathOrUrl, json, client)
    }

    // endregion
    // region PATCH XML

    /**
     * PATCH XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @return response with String body
     */
    TestHttpResponse httpPatchXml(
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        httpPatchXml(EMPTY, pathOrUrl, format, body, null)
    }

    /**
     * PATCH XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpPatchXml(
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPatchXml(EMPTY, pathOrUrl, format, body, client)
    }

    /**
     * PATCH XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @return response with String body
     */
    TestHttpResponse httpPatchXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body
    ) {
        httpPatchXml(headers, pathOrUrl, format, body, null)
    }

    /**
     * PATCH XML generated by the provided {@link MarkupBuilder} DSL closure.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param format optional xml format configuration
     * @param body {@link MarkupBuilder} DSL closure
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpPatchXml(
            Map<String, String> headers,
            CharSequence pathOrUrl,
            XmlUtils.Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> body,
            HttpClient client
    ) {
        httpPatch(headers, pathOrUrl, XmlUtils.toXml(format, body), APPLICATION_XML, client)
    }

    // endregion
    // endregion
    // region TRACE HELPERS

    /**
     * TRACE request.
     *
     * @param pathOrUrl relative path or absolute URL
     * @return response with String body
     */
    TestHttpResponse httpTrace(CharSequence pathOrUrl) {
        httpTrace(EMPTY, pathOrUrl, null)
    }

    /**
     * TRACE request.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpTrace(CharSequence pathOrUrl, HttpClient client) {
        httpTrace(EMPTY, pathOrUrl, client)
    }

    /**
     * TRACE request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @return response with String body
     */
    TestHttpResponse httpTrace(Map<String, String> headers, CharSequence pathOrUrl) {
        httpTrace(headers, pathOrUrl, null)
    }

    /**
     * TRACE request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpTrace(Map<String, String> headers, CharSequence pathOrUrl, HttpClient client) {
        send(client, headers, requestBuilder(pathOrUrl).method(TRACE, HttpRequest.BodyPublishers.noBody()))
    }

    // endregion
    // region HEAD HELPERS

    /**
     * HEAD request.
     *
     * @param pathOrUrl relative path or absolute URL
     * @return response with String body (typically empty for HEAD responses)
     */
    TestHttpResponse httpHead(CharSequence pathOrUrl) {
        httpHead(EMPTY, pathOrUrl, null)
    }

    /**
     * HEAD request.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param client {@link HttpClient} instance to use
     * @return response with String body (typically empty for HEAD responses)
     */
    TestHttpResponse httpHead(CharSequence pathOrUrl, HttpClient client) {
        httpHead(EMPTY, pathOrUrl, client)
    }

    /**
     * HEAD request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @return response with String body (typically empty for HEAD responses)
     */
    TestHttpResponse httpHead(Map<String, String> headers, CharSequence pathOrUrl) {
        httpHead(headers, pathOrUrl, null)
    }

    /**
     * HEAD request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param client {@link HttpClient} instance to use
     * @return response with String body (typically empty for HEAD responses)
     */
    TestHttpResponse httpHead(Map<String, String> headers, CharSequence pathOrUrl, HttpClient client) {
        send(client, headers, requestBuilder(pathOrUrl).method(HEAD, HttpRequest.BodyPublishers.noBody()))
    }

    // endregion
    // region DELETE HELPERS

    /**
     * DELETE request.
     *
     * @param pathOrUrl relative path or absolute URL
     * @return response with String body
     */
    TestHttpResponse httpDelete(CharSequence pathOrUrl) {
        httpDelete(EMPTY, pathOrUrl, null)
    }

    /**
     * DELETE request.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpDelete(CharSequence pathOrUrl, HttpClient client) {
        httpDelete(EMPTY, pathOrUrl, client)
    }

    /**
     * DELETE request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @return response with String body
     */
    TestHttpResponse httpDelete(Map<String, String> headers, CharSequence pathOrUrl) {
        httpDelete(headers, pathOrUrl, null)
    }

    /**
     * DELETE request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpDelete(Map<String, String> headers, CharSequence pathOrUrl, HttpClient client) {
        send(client, headers, requestBuilder(pathOrUrl).DELETE())
    }

    // endregion
    // region OPTIONS HELPERS

    /**
     * OPTIONS request.
     *
     * @param pathOrUrl relative path or absolute URL
     * @return response with String body
     */
    TestHttpResponse httpOptions(CharSequence pathOrUrl) {
        httpOptions(EMPTY, pathOrUrl, null)
    }

    /**
     * OPTIONS request.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpOptions(CharSequence pathOrUrl, HttpClient client) {
        httpOptions(EMPTY, pathOrUrl, client)
    }

    /**
     * OPTIONS request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @return response with String body
     */
    TestHttpResponse httpOptions(Map<String, String> headers, CharSequence pathOrUrl) {
        httpOptions(headers, pathOrUrl, null)
    }

    /**
     * OPTIONS request.
     *
     * @param headers optional request headers
     * @param pathOrUrl relative path or absolute URL
     * @param client {@link HttpClient} instance to use
     * @return response with String body
     */
    TestHttpResponse httpOptions(Map<String, String> headers, CharSequence pathOrUrl, HttpClient client) {
        send(client, headers, requestBuilder(pathOrUrl).method(OPTIONS, HttpRequest.BodyPublishers.noBody()))
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
                    "Sending HttpRequest to [${request.uri()}] without configured timeout.",
                    "Offending class is [${getClass().name}].",
                    'Consider using newHttpRequestWith() or setting timeout(...) on your custom request.'
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
    HttpClient newHttpClientWith(
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
     * <p>
     * Requests built through this helper always start with a timeout of 60 seconds and can be
     * overridden in {@code configurer}.
     *
     * @param pathOrUrl relative path or absolute URL
     * @param configurer optional request builder configurer closure
     * @return built request
     */
    HttpRequest newHttpRequestWith(
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

    private TestHttpResponse send(HttpClient client, Map<String, String> headers, HttpRequest.Builder requestBuilder) {
        headers.each { k, v -> requestBuilder.header(k, v) }
        send(client, requestBuilder.build())
    }

    private TestHttpResponse send(HttpClient client, HttpRequest request) {
        if (client && !client.connectTimeout().isPresent()) {
            warn(
                    "Using HttpClient without configured connect timeout when connecting to [${request.uri()}].",
                    "Offending class is [${getClass().name}].",
                    'Consider using newHttpClientWith() or setting connectTimeout(...) on your custom client.'
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
