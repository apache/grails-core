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
import java.util.regex.Pattern

import javax.net.ssl.SSLSession

import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult

import org.apache.grails.testing.http.client.utils.JsonAssertUtils
import org.opentest4j.AssertionFailedError

/**
 * Fluent assertion wrapper around a JDK {@link HttpResponse} used by test HTTP helpers.
 *
 * <p>This type exposes convenience methods for common test expectations (status, headers,
 * body text, JSON, XML, and regex matching) while delegating all {@link HttpResponse}
 * interface methods to the wrapped response.</p>
 *
 * <p>Assertion helpers return {@code this} for chaining and throw assertion failures with
 * descriptive expected/actual values when checks do not pass.</p>
 */
class TestHttpResponse implements HttpResponse {

    private static final String CONTENT_TYPE = 'Content-Type'
    private static final Map<String, String> EMPTY = Collections.emptyMap()

    private final HttpResponse<?> delegate

    TestHttpResponse(HttpResponse<?> response) {
        this.delegate = response
    }

    static TestHttpResponse wrap(HttpResponse<?> response) {
        response instanceof TestHttpResponse ?
                (TestHttpResponse) response : new TestHttpResponse(response)
    }

    /** Parses the response body as a JSON object. */
    Map json() {
        (Map) new JsonSlurper().parseText(delegate.body() as String)
    }

    /** Parses the response body as a JSON array. */
    List jsonList() {
        (List) new JsonSlurper().parseText(delegate.body() as String)
    }

    /** Parses the response body as XML. */
    GPathResult xml() {
        new XmlSlurper().parseText(delegate.body() as String)
    }

    // region STATUS

    /**
     * Asserts response status and optional exact header values.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @return same response for fluent chaining
     */
    TestHttpResponse expectStatus(Map<String, String> headers = EMPTY, int status) {
        verifyStatus(delegate, status)
        verifyHeaders(delegate, headers)
        this
    }

    TestHttpResponse expectNotStatus(int status) {
        verifyNotStatus(delegate, status)
        this
    }

    // endregion
    // region HEADERS

    /** Returns the first value for the given header, or {@code null} when missing. */
    String headerValue(String name) {
        delegate.headers().firstValue(name).orElse(null)
    }

    String headerValueIgnoreCase(String name) {
        def values = headerValuesIgnoreCase(delegate, name)
        if (values) {
            return values.first()
        }
        null
    }

    /**
     *  Returns the first value for the given header as a long.
     *  @throws NoSuchElementException if no header is found
     *  @throws NumberFormatException if a value is found, but does not parse as a Long
     */
    long headerValueAsLong(String name) {
        delegate.headers().firstValueAsLong(name).orElseThrow()
    }

    /** Shortcut for the {@code Content-Type} header value. */
    String getContentType() {
        headerValueIgnoreCase(CONTENT_TYPE)
    }

    /** @return {@code true} if the response contains the named header. */
    boolean hasHeader(String name) {
        delegate.headers().firstValue(name).isPresent()
    }

    /** @return {@code true} if the named header exists and equals {@code expected}. */
    boolean hasHeaderValue(String name, String expected) {
        delegate.headers().firstValue(name).map { it == expected }.orElse(false)
    }

    /** @return {@code true} if the named header exists and equals {@code expected} ignoring case. */
    boolean hasHeaderValueIgnoreCase(String name, String expected) {
        def expectedLower = expected == null ? null : expected.toLowerCase(Locale.ENGLISH)
        def values = headerValuesIgnoreCase(delegate, name)
        values.any { String actual ->
            (actual == null && expectedLower == null) ||
                    (actual != null && expectedLower != null && actual.toLowerCase(Locale.ENGLISH) == expectedLower)
        }
    }

    /** Asserts exact values for the provided header names. */
    TestHttpResponse expectHeaders(Map<String, String> expected) {
        verifyHeaders(delegate, expected)
        this
    }

    /** Asserts status and exact values for the provided header names. */
    TestHttpResponse expectHeaders(Map<String, String> expected, int status) {
        verifyStatus(delegate, status)
        verifyHeaders(delegate, expected)
        this
    }

    /**
     * Asserts case-insensitive header-name/value equality for all expected entries.
     *
     * @param expected expected headers to validate
     * @return same response for fluent chaining
     */
    TestHttpResponse expectHeadersIgnoreCase(Map<String, String> expected) {
        verifyHeadersIgnoreCase(delegate, expected)
        this
    }

    /**
     * Asserts status and case-insensitive header-name/value equality.
     *
     * @param expected expected headers to validate
     * @param status expected HTTP status
     * @return same response for fluent chaining
     */
    TestHttpResponse expectHeadersIgnoreCase(Map<String, String> expected, int status) {
        verifyStatus(delegate, status)
        verifyHeadersIgnoreCase(delegate, expected)
        this
    }

    TestHttpResponse expectNotHeaders(Map<String, String> headers) {
        verifyNotHeaders(delegate, headers)
        this
    }

    // endregion
    // region JSON
    // region expectJson

    /** Asserts response JSON tree equality. */
    TestHttpResponse expectJson(Map<String, String> headers = EMPTY, Map<String, ?> json) {
        expectJson(headers, (Object) json)
    }

    /** Asserts response JSON tree equality. */
    TestHttpResponse expectJson(Map<String, String> headers = EMPTY, List<?> json) {
        expectJson(headers, (Object) json)
    }

    /**
     * Asserts response JSON tree equality against a JSON-compatible object.
     *
     * <p>Uses structural comparison (for example, object key order does not matter).</p>
     */
    TestHttpResponse expectJson(Map<String, String> headers = EMPTY, Object json) {
        verifyHeaders(delegate, headers)
        verifyJsonTree(delegate, json)
        this
    }

    /**
     * Asserts response JSON tree equality against a JSON document string.
     */
    TestHttpResponse expectJson(Map<String, String> headers = EMPTY, CharSequence json) {
        verifyHeaders(delegate, headers)
        verifyJsonTree(delegate, json)
        this
    }

    /** Asserts response status and JSON tree equality. */
    TestHttpResponse expectJson(Map<String, String> headers = EMPTY, int status, Map<String, ?> json) {
        expectJson(headers, status, (Object) json)
    }

    /** Asserts response status and JSON tree equality. */
    TestHttpResponse expectJson(Map<String, String> headers = EMPTY, int status, List<?> json) {
        expectJson(headers, status, (Object) json)
    }

    /** Asserts response status and JSON tree equality. */
    TestHttpResponse expectJson(Map<String, String> headers = EMPTY, int status, Object json) {
        verifyStatus(delegate, status)
        expectJson(headers, json)
    }

    /** Asserts response status and JSON tree equality. */
    TestHttpResponse expectJson(Map<String, String> headers = EMPTY, int status, CharSequence json) {
        verifyStatus(delegate, status)
        expectJson(headers, json)
    }

    // endregion
    // region expectJsonContains

    /** Asserts response JSON tree contains expected subset. */
    TestHttpResponse expectJsonContains(Map<String, String> headers = EMPTY, Map<String, ?> json) {
        expectJsonContains(headers, (Object) json)
    }

    /** Asserts response JSON tree contains expected subset. */
    TestHttpResponse expectJsonContains(Map<String, String> headers = EMPTY, List<?> json) {
        expectJsonContains(headers, (Object) json)
    }

    /**
     * Asserts response JSON contains the expected subset.
     *
     * <p>For objects, expected keys must exist; for arrays, expected elements are matched by index.</p>
     */
    TestHttpResponse expectJsonContains(Map<String, String> headers = EMPTY, Object json) {
        verifyHeaders(delegate, headers)
        verifyJsonTreeContains(delegate, json)
        this
    }

    /**
     * Asserts response JSON contains the expected subset expressed as JSON text.
     */
    TestHttpResponse expectJsonContains(Map<String, String> headers = EMPTY, CharSequence json) {
        verifyHeaders(delegate, headers)
        verifyJsonTreeContains(delegate, json)
        this
    }

    /** Asserts response status and JSON tree contains expected subset. */
    TestHttpResponse expectJsonContains(Map<String, String> headers = EMPTY, int status, Map<String, ?> json) {
        expectJsonContains(headers, status, (Object) json)
    }

    /** Asserts response status and JSON tree contains expected subset. */
    TestHttpResponse expectJsonContains(Map<String, String> headers = EMPTY, int status, List<?> json) {
        expectJsonContains(headers, status, (Object) json)
    }

    /** Asserts response status and JSON tree contains expected subset. */
    TestHttpResponse expectJsonContains(Map<String, String> headers = EMPTY, int status, Object json) {
        verifyStatus(delegate, status)
        expectJsonContains(headers, json)
    }

    /** Asserts response JSON tree contains expected subset. */
    TestHttpResponse expectJsonContains(Map<String, String> headers = EMPTY, int status, CharSequence json) {
        verifyStatus(delegate, status)
        expectJsonContains(headers, json)
    }

    // endregion
    // endregion
    // region TEXT BODY

    /** Asserts response body equality. */
    TestHttpResponse expect(Map<String, String> headers = EMPTY, CharSequence body) {
        verifyHeaders(delegate, headers)
        verifyBody(delegate, body)
        this
    }

    /** Asserts status and response body equality. */
    TestHttpResponse expect(Map<String, String> headers = EMPTY, int status, CharSequence body) {
        verifyStatus(delegate, status)
        expect(headers, body)
        this
    }

    /** Asserts response body contains text. */
    TestHttpResponse expectContains(Map<String, String> headers = EMPTY, CharSequence body) {
        verifyHeaders(delegate, headers)
        verifyBodyContains(delegate, body)
        this
    }

    /** Asserts status and response body contains text. */
    TestHttpResponse expectContains(Map<String, String> headers = EMPTY, int status, CharSequence body) {
        verifyStatus(delegate, status)
        expectContains(headers, body)
    }

    /** Asserts response body contains a regex match ({@code find()}). */
    TestHttpResponse expectContainsMatches(Map<String, String> headers = EMPTY, Pattern pattern) {
        verifyHeaders(delegate, headers)
        verifyContainsMatches(delegate, pattern)
        this
    }

    /** Asserts status and response body contains a regex match ({@code find()}). */
    TestHttpResponse expectContainsMatches(Map<String, String> headers = EMPTY, int status, Pattern pattern) {
        verifyStatus(delegate, status)
        expectContainsMatches(headers, pattern)
    }

    /** Asserts response body fully matches the regex ({@code matches()}). */
    TestHttpResponse expectMatches(Map<String, String> headers = EMPTY, Pattern pattern) {
        verifyHeaders(delegate, headers)
        verifyMatches(delegate, pattern)
        this
    }

    /** Asserts status and response body fully matches the regex ({@code matches()}). */
    TestHttpResponse expectMatches(Map<String, String> headers = EMPTY, int status, Pattern pattern) {
        verifyStatus(delegate, status)
        expectMatches(headers, pattern)
    }

    /** Asserts full response body not equals. */
    TestHttpResponse expectNotBody(Map<String, String> headers = EMPTY, CharSequence body) {
        verifyHeaders(delegate, headers)
        verifyNotBody(delegate, body)
        this
    }

    /** Asserts status equals and full response body not equals. */
    TestHttpResponse expectNotBody(Map<String, String> headers = EMPTY, int status, CharSequence body) {
        verifyStatus(delegate, status)
        expectNotBody(headers, body)
    }

    /** Asserts response body not contains text. */
    TestHttpResponse expectNotBodyContains(Map<String, String> headers = EMPTY, CharSequence text) {
        verifyHeaders(delegate, headers)
        verifyNotBodyContains(delegate, text)
        this
    }

    /** Asserts status equals and response body not contains text. */
    TestHttpResponse expectNotBodyContains(Map<String, String> headers = EMPTY, int status, CharSequence text) {
        verifyStatus(delegate, status)
        expectNotBodyContains(headers, text)
    }

    /** Asserts response body not contains a regex match ({@code find()}). */
    TestHttpResponse expectNotBodyContainsMatches(Map<String, String> headers = EMPTY, Pattern pattern) {
        verifyHeaders(delegate, headers)
        verifyNotContainsMatches(delegate, pattern)
        this
    }

    /** Asserts status equals and response body not contains a regex match ({@code find()}). */
    TestHttpResponse expectNotBodyContainsMatches(Map<String, String> headers = EMPTY, int status, Pattern pattern) {
        verifyStatus(delegate, status)
        expectNotBodyContainsMatches(headers, pattern)
    }

    /** Asserts response body not matches regex ({@code matches()}).*/
    TestHttpResponse expectNotBodyMatches(Map<String, String> headers = EMPTY, Pattern pattern) {
        verifyHeaders(delegate, headers)
        verifyNotMatches(delegate, pattern)
        this
    }

    /** Asserts status equals and response body not matches regex ({@code matches()}). */
    TestHttpResponse expectNotBodyMatches(Map<String, String> headers = EMPTY, int status, Pattern pattern) {
        verifyStatus(delegate, status)
        expectNotBodyMatches(headers, pattern)
    }

    // endregion
    // region INTERFACE HttpResponse

    @Override
    int statusCode() {
        delegate.statusCode()
    }

    @Override
    HttpRequest request() {
        delegate.request()
    }

    @Override
    Optional<HttpResponse<?>> previousResponse() {
        delegate.previousResponse().map { wrap((HttpResponse) it) }
    }

    @Override
    HttpHeaders headers() {
        delegate.headers()
    }

    @Override
    String body() {
        delegate.body()
    }

    @Override
    Optional<SSLSession> sslSession() {
        delegate.sslSession()
    }

    @Override
    URI uri() {
        delegate.uri()
    }

    @Override
    HttpClient.Version version() {
        delegate.version()
    }

    // endregion
    // region VERIFIERS

    private static void verifyStatus(HttpResponse<?> r, int expected) {
        int actual = r.statusCode()
        verify(actual, expected, 'HTTP Status differs')
    }

    private static void verifyHeaders(HttpResponse<?> r, Map<String, String> expectedHeaders) {
        if (!expectedHeaders) {
            return
        }
        expectedHeaders.each { String name, String expected ->
            def actual = r.headers().firstValue(name).orElse(null)
            if (actual != expected) {
                throw new AssertionFailedError("Header differs for '$name'", expected, actual)
            }
        }
    }

    private static void verifyBody(HttpResponse<?> r, CharSequence expected) {
        def actual = r.body() as String
        if (actual != expected) {
            throw new AssertionFailedError('Body differs', expected, actual)
        }
    }

    private static void verifyBodyContains(HttpResponse<?> r, CharSequence expected) {
        def body = r.body() as String
        if (!body.contains(expected)) {
            throw new AssertionFailedError('Body does not contain value', expected, body)
        }
    }

    private static void verifyJsonTree(HttpResponse<?> r, Object expected) {
        JsonAssertUtils.verifyJsonTree(r, expected)
    }

    private static void verifyJsonTree(HttpResponse<?> r, CharSequence expectedJson) {
        JsonAssertUtils.verifyJsonTree(r, expectedJson)
    }

    private static void verifyJsonTreeContains(HttpResponse<?> r, Object expected) {
        JsonAssertUtils.verifyJsonTreeContains(r, expected)
    }

    private static void verifyJsonTreeContains(HttpResponse<?> r, CharSequence expectedJson) {
        JsonAssertUtils.verifyJsonTreeContains(r, expectedJson)
    }

    private static void verifyMatches(HttpResponse<?> r, Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException('pattern must not be null')
        }
        def actual = r.body() as String ?: ''
        if (!pattern.matcher(actual).matches()) {
            throw new AssertionFailedError('Body does not fully match pattern', pattern.toString(), actual)
        }
    }

    private static void verifyContainsMatches(HttpResponse<?> r, Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException('pattern must not be null')
        }
        def actual = r.body() as String ?: ''
        if (!pattern.matcher(actual).find()) {
            throw new AssertionFailedError('Body does not contain pattern match', pattern.toString(), actual)
        }
    }

    private static void verifyNotStatus(HttpResponse<?> r, int unexpected) {
        int actual = r.statusCode()
        if (actual == unexpected) {
            throw new AssertionFailedError('HTTP Status should not match', "not $unexpected", actual)
        }
    }

    private static void verifyNotHeaders(HttpResponse<?> r, Map<String, String> unexpectedHeaders) {
        if (!unexpectedHeaders) {
            return
        }
        unexpectedHeaders.each { String name, String unexpected ->
            def actual = r.headers().firstValue(name).orElse(null)
            if (actual == unexpected) {
                throw new AssertionFailedError("Header should not match for '$name'", "not $unexpected", actual)
            }
        }
    }

    private static void verifyNotBody(HttpResponse<?> r, CharSequence text) {
        def actual = r.body() as String
        if (actual == text) {
            throw new AssertionFailedError('Body should not match', "not $text", actual)
        }
    }

    private static void verifyNotBodyContains(HttpResponse<?> r, CharSequence text) {
        def actual = r.body() as String
        if (actual.contains(text)) {
            throw new AssertionFailedError('Body should not contain value', "not containing $text", actual)
        }
    }

    private static void verifyNotMatches(HttpResponse<?> r, Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException('pattern must not be null')
        }
        def actual = r.body() as String ?: ''
        if (pattern.matcher(actual).matches()) {
            throw new AssertionFailedError('Body should not fully match pattern', "not ${pattern}", actual)
        }
    }

    private static void verifyNotContainsMatches(HttpResponse<?> r, Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException('pattern must not be null')
        }
        def actual = r.body() as String ?: ''
        if (pattern.matcher(actual).find()) {
            throw new AssertionFailedError('Body should not contain pattern match', "not ${pattern}", actual)
        }
    }

    private static void verifyHeadersIgnoreCase(HttpResponse<?> r, Map<String, String> expectedHeaders) {
        if (!expectedHeaders) {
            return
        }
        expectedHeaders.each { String name, String expectedValue ->
            def values = headerValuesIgnoreCase(r, name)
            if (!values) {
                throw new AssertionFailedError("Header differs for '$name'", expectedValue, null)
            }
            def expectedLower = expectedValue == null ? null : expectedValue.toLowerCase(Locale.ENGLISH)
            def matches = values.any { String actual ->
                (actual == null && expectedLower == null) ||
                        (actual != null && expectedLower != null && actual.toLowerCase(Locale.ENGLISH) == expectedLower)
            }
            if (!matches) {
                throw new AssertionFailedError("Header differs for '$name' (ignore case)", expectedValue, values.first())
            }
        }
    }

    private static void verify(Object actual, Object expected, String errorMessage) {
        if (actual != expected) {
            throw new AssertionFailedError(errorMessage, expected, actual)
        }
    }

    private static List<String> headerValuesIgnoreCase(HttpResponse<?> r, String name) {
        if (!name) {
            return Collections.emptyList()
        }
        def result = [] as List<String>
        r.headers().map().each { key, values ->
            if (key?.equalsIgnoreCase(name) && values) {
                result.addAll(values)
            }
        }
        result
    }

    // endregion
}
