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

import groovy.transform.NamedDelegate
import groovy.transform.NamedVariant
import groovy.xml.slurpersupport.GPathResult

import org.apache.grails.testing.http.client.utils.JsonUtils
import org.apache.grails.testing.http.client.utils.XmlUtils
import org.opentest4j.AssertionFailedError

/**
 * Fluent assertion wrapper around a JDK {@link HttpResponse} used by the HTTP client testing helpers.
 * <p>
 * This type keeps the underlying response accessible through the standard {@link HttpResponse} interface while
 * adding convenience methods for the kinds of assertions common in integration and functional tests: status codes,
 * headers, body text, structural JSON checks, XML parsing, and regex matching.
 * <p>
 * Assertion helpers are chainable and return {@code this} when successful, making it practical to express tests as:
 * <pre>
 * def response = http('/health')
 * response.assertStatus(200)
 *         .assertContains('UP')
 * </pre>
 * <p>
 * JSON helpers use structural comparison instead of raw string comparison, so object key ordering does not matter.
 * Text and regex helpers work on the response body as a {@link String}. Parsing helpers such as {@link #json},
 * {@link #jsonList}, and {@link #xml} convert the body into richer representations when tests need to inspect
 * structured content directly. XML parsing uses a secure default {@link groovy.xml.XmlSlurper} configuration and can
 * be overridden per wrapper with {@link #withXmlSlurper}.
 * <p>
 * When assertion methods accept a {@code headers} map, only the provided header entries are validated.
 * Additional headers present on the response are ignored unless explicitly included in the expected map.
 * <p>
 * Assertion failures are reported as {@link AssertionFailedError} with expected/actual values when possible.
 * Methods that parse JSON may also surface parsing exceptions if the response body is not valid for the configured
 * parser settings.
 *
 * @since 7.0.10
 */
class TestHttpResponse implements HttpResponse {

    private static final String CONTENT_TYPE = 'Content-Type'
    private static final Map<String, String> EMPTY = Collections.emptyMap()

    private final HttpResponse<?> delegate
    private final JsonUtils.SlurperConfig jsonSlurperConfig
    private final XmlUtils.SlurperConfig xmlSlurperConfig

    TestHttpResponse(HttpResponse<?> response) {
        this(response, null, null)
    }

    TestHttpResponse(HttpResponse<?> response, JsonUtils.SlurperConfig jsonSlurperConfig, XmlUtils.SlurperConfig xmlSlurperConfig) {
        this.delegate = response
        this.jsonSlurperConfig = jsonSlurperConfig ?: new JsonUtils.SlurperConfig()
        this.xmlSlurperConfig = xmlSlurperConfig ?: new XmlUtils.SlurperConfig()
    }

    /**
     * Wraps a raw {@link HttpResponse} in {@link TestHttpResponse} unless it is already wrapped.
     *
     * @param response raw or already wrapped response
     * @return {@code response} unchanged when already a {@link TestHttpResponse}; otherwise a new wrapper
     */
    static TestHttpResponse wrap(HttpResponse<?> response) {
        response instanceof TestHttpResponse ?
                (TestHttpResponse) response : new TestHttpResponse(response)
    }

    /**
     * Returns a new response wrapper that uses the provided
     * {@link org.apache.grails.testing.http.client.utils.JsonUtils.SlurperConfig}
     * for JSON parsing and JSON assertion helpers.
     * <p>
     * This method does not mutate the current wrapper. Instead, it returns a new wrapper sharing the same underlying
     * response while applying the supplied {@link JsonUtils.SlurperConfig} to methods such as {@link #json()},
     * {@link #jsonList()}, {@link #assertJson(CharSequence)}, and {@link #assertJsonContains(CharSequence)}.
     *
     * @param jsonSlurperConfig JSON parser configuration to use for the returned wrapper
     * @return new response wrapper using the supplied JSON parser settings
     */
    @NamedVariant
    TestHttpResponse withJsonSlurper(@NamedDelegate JsonUtils.SlurperConfig jsonSlurperConfig = new JsonUtils.SlurperConfig()) {
        new TestHttpResponse(delegate, jsonSlurperConfig, xmlSlurperConfig)
    }

    /**
     * Returns a new response wrapper that uses the provided
     * {@link org.apache.grails.testing.http.client.utils.XmlUtils.SlurperConfig}
     * for XML parsing helpers.
     * <p>
     * This method does not mutate the current wrapper. Instead, it returns a new wrapper sharing the same underlying
     * response while applying the supplied XML parser configuration to {@link #xml()}.
     *
     * @param xmlSlurperConfig XML parser configuration to use for the returned wrapper
     * @return new response wrapper using the supplied XML parser settings
     */
    @NamedVariant
    TestHttpResponse withXmlSlurper(@NamedDelegate XmlUtils.SlurperConfig xmlSlurperConfig = new XmlUtils.SlurperConfig()) {
        new TestHttpResponse(delegate, jsonSlurperConfig, xmlSlurperConfig)
    }

    /**
     * Parses the response body as a JSON object.
     *
     * @return parsed JSON object body as a {@link Map}
     * @throws ClassCastException if the parsed body is not a JSON object
     */
    Map json() {
        (Map) JsonUtils.parseText(delegate.body() as String, jsonSlurperConfig)
    }

    /**
     * Parses the response body as a JSON array.
     *
     * @return parsed JSON array body as a {@link List}
     * @throws ClassCastException if the parsed body is not a JSON array
     */
    List jsonList() {
        (List) JsonUtils.parseText(delegate.body() as String, jsonSlurperConfig)
    }

    /**
     * Parses the response body as XML.
     *
     * A secure {@link groovy.xml.XmlSlurper} configuration is used by default, disabling external entity expansion and
     * external DTD loading while remaining namespace aware and non-validating. Override the parser with
     * {@link #withXmlSlurper(XmlUtils.SlurperConfig)} when a test needs custom XML parsing behavior.
     *
     * @return XML body parsed into a {@link GPathResult} for XML assertions
     */
    GPathResult xml() {
        XmlUtils.newXmlSlurper(xmlSlurperConfig).parseText(delegate.body() as String)
    }

    // region STATUS

    /**
     * Asserts response status and optional exact header values.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param status expected HTTP status
     * @return same response for fluent chaining
     */
    TestHttpResponse assertStatus(Map<String, String> headers = EMPTY, int status) {
        verifyStatus(delegate, status)
        verifyHeaders(delegate, headers)
        this
    }

    /**
     * Asserts that the response status is not equal to {@code status}.
     *
     * @param status unexpected HTTP status
     * @return same response for fluent chaining
     */
    TestHttpResponse assertNotStatus(int status) {
        verifyNotStatus(delegate, status)
        this
    }

    // endregion
    // region HEADERS

    /**
     * Returns the first value for the given header, or {@code null} when missing.
     *
     * @param name header name to look up
     * @return first matching value, or {@code null} when no matching header exists
     */
    String headerValue(String name) {
        delegate.headers().firstValue(name).orElse(null)
    }

    /**
     * Returns the first header value for the given name using case-insensitive header-name matching.
     *
     * @param name header name to look up
     * @return first matching value, or {@code null} when no matching header exists
     */
    String headerValueIgnoreCase(String name) {
        def values = headerValuesIgnoreCase(delegate, name)
        if (values) {
            return values.first()
        }
        null
    }

    /**
     *  Returns the first value for the given header as a long.
     *
     *  @throws NoSuchElementException if no header is found
     *  @throws NumberFormatException if a value is found, but does not parse as a Long
     */
    long headerValueAsLong(String name) {
        delegate.headers().firstValueAsLong(name).orElseThrow()
    }

    /**
     * Shortcut for the {@code Content-Type} header value.
     *
     * @return first matching value of the {@code Content-Type} header (ignoring case),
     *         or {@code null} when the header does not exist
     */
    String getContentType() {
        headerValueIgnoreCase(CONTENT_TYPE)
    }

    /**
     * Indicates whether the response contains the named header.
     *
     * @param name header name to look up
     * @return {@code true} if at least one matching header exists
     */
    boolean hasHeader(String name) {
        delegate.headers().firstValue(name).isPresent()
    }

    /**
     * Indicates whether the named header exists and its first value equals {@code expected} exactly.
     *
     * @param name header name to look up
     * @param expected expected first header value
     * @return {@code true} if the first value matches exactly
     */
    boolean hasHeaderValue(String name, String expected) {
        delegate.headers().firstValue(name).map { it == expected }.orElse(false)
    }

    /**
     * Indicates whether a matching header exists and any of its values equals {@code expected} ignoring case.
     *
     * @param name header name to look up case-insensitively
     * @param expected expected header value, compared ignoring case
     * @return {@code true} if a matching value exists
     */
    boolean hasHeaderValueIgnoreCase(String name, String expected) {
        def expectedLower = expected == null ? null : expected.toLowerCase(Locale.ENGLISH)
        def values = headerValuesIgnoreCase(delegate, name)
        values.any { String actual ->
            (actual == null && expectedLower == null) ||
                    (actual != null && expectedLower != null && actual.toLowerCase(Locale.ENGLISH) == expectedLower)
        }
    }

    /**
     * Asserts exact values for the provided header names.
     *
     * @param expected expected header values keyed by header name;
     *        only these entries are checked and extra response headers are ignored
     * @return same response for fluent chaining
     */
    TestHttpResponse assertHeaders(Map<String, String> expected) {
        verifyHeaders(delegate, expected)
        this
    }

    /**
     * Asserts status and exact values for the provided header names.
     *
     * @param expected expected header values keyed by header name;
     *        only these entries are checked and extra response headers are ignored
     * @param status expected HTTP status
     * @return same response for fluent chaining
     */
    TestHttpResponse assertHeaders(Map<String, String> expected, int status) {
        verifyStatus(delegate, status)
        verifyHeaders(delegate, expected)
        this
    }

    /**
     * Asserts case-insensitive header-name/value equality for all expected entries.
     *
     * @param expected expected headers to validate;
     *        only these entries are checked and extra response headers are ignored
     * @return same response for fluent chaining
     */
    TestHttpResponse assertHeadersIgnoreCase(Map<String, String> expected) {
        verifyHeadersIgnoreCase(delegate, expected)
        this
    }

    /**
     * Asserts status and case-insensitive header-name/value equality.
     *
     * @param expected expected headers to validate;
     *        only these entries are checked and extra response headers are ignored
     * @param status expected HTTP status
     * @return same response for fluent chaining
     */
    TestHttpResponse assertHeadersIgnoreCase(Map<String, String> expected, int status) {
        verifyStatus(delegate, status)
        verifyHeadersIgnoreCase(delegate, expected)
        this
    }

    /**
     * Asserts that the provided header values are not present as exact first-value matches.
     *
     * @param headers header/value pairs that must not match the response
     * @return same response for fluent chaining
     */
    TestHttpResponse assertNotHeaders(Map<String, String> headers) {
        verifyNotHeaders(delegate, headers)
        this
    }

    // endregion
    // region JSON
    // region assertJson

    /**
     * Asserts response JSON tree equality against a JSON object.
     *
     * @param headers expected headers; ignored when empty
     * @param json expected JSON object
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJson(Map<String, String> headers = EMPTY, Map<String, ?> json) {
        assertJson(headers, (Object) json)
    }

    /**
     * Asserts response JSON tree equality against a JSON array.
     *
     * @param headers expected headers; ignored when empty
     * @param json expected JSON array
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJson(Map<String, String> headers = EMPTY, List<?> json) {
        assertJson(headers, (Object) json)
    }

    /**
     * Asserts response JSON tree equality against a JSON-compatible object.
     *
     * <p>Uses structural comparison (for example, object key order does not matter).</p>
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param json expected JSON-compatible object tree
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJson(Map<String, String> headers = EMPTY, Object json) {
        verifyHeaders(delegate, headers)
        verifyJsonTree(delegate, json)
        this
    }

    /**
     * Asserts response JSON tree equality against a JSON document string.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param json expected JSON document text
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJson(Map<String, String> headers = EMPTY, CharSequence json) {
        verifyHeaders(delegate, headers)
        verifyJsonTree(delegate, json)
        this
    }

    /**
     * Asserts response status and JSON tree equality against a JSON object.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param json expected JSON object
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJson(Map<String, String> headers = EMPTY, int status, Map<String, ?> json) {
        assertJson(headers, status, (Object) json)
    }

    /**
     * Asserts response status and JSON tree equality against a JSON array.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param json expected JSON array
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJson(Map<String, String> headers = EMPTY, int status, List<?> json) {
        assertJson(headers, status, (Object) json)
    }

    /**
     * Asserts response status and JSON tree equality against a JSON-compatible object.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param json expected JSON-compatible object tree
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJson(Map<String, String> headers = EMPTY, int status, Object json) {
        verifyStatus(delegate, status)
        assertJson(headers, json)
    }

    /**
     * Asserts response status and JSON tree equality against JSON text.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param json expected JSON document text
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJson(Map<String, String> headers = EMPTY, int status, CharSequence json) {
        verifyStatus(delegate, status)
        assertJson(headers, json)
    }

    // endregion
    // region assertJsonContains

    /**
     * Asserts response JSON contains an expected object subset.
     *
     * @param headers expected headers; ignored when empty
     * @param json expected subset object
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJsonContains(Map<String, String> headers = EMPTY, Map<String, ?> json) {
        assertJsonContains(headers, (Object) json)
    }

    /**
     * Asserts response JSON contains an expected array subset.
     *
     * @param headers expected headers; ignored when empty
     * @param json expected subset array
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJsonContains(Map<String, String> headers = EMPTY, List<?> json) {
        assertJsonContains(headers, (Object) json)
    }

    /**
     * Asserts response JSON contains the expected subset.
     *
     * <p>For objects, expected keys must exist; for arrays, expected elements are matched by index.</p>
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param json expected JSON-compatible subset
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJsonContains(Map<String, String> headers = EMPTY, Object json) {
        verifyHeaders(delegate, headers)
        verifyJsonTreeContains(delegate, json)
        this
    }

    /**
     * Asserts response JSON contains the expected subset expressed as JSON text.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param json expected subset encoded as JSON text
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJsonContains(Map<String, String> headers = EMPTY, CharSequence json) {
        verifyHeaders(delegate, headers)
        verifyJsonTreeContains(delegate, json)
        this
    }

    /**
     * Asserts response status and JSON tree contains an expected object subset.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param json expected subset object
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJsonContains(Map<String, String> headers = EMPTY, int status, Map<String, ?> json) {
        assertJsonContains(headers, status, (Object) json)
    }

    /**
     * Asserts response status and JSON tree contains an expected array subset.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param json expected subset array
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJsonContains(Map<String, String> headers = EMPTY, int status, List<?> json) {
        assertJsonContains(headers, status, (Object) json)
    }

    /**
     * Asserts response status and JSON tree contains an expected JSON-compatible subset.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param json expected JSON-compatible subset
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJsonContains(Map<String, String> headers = EMPTY, int status, Object json) {
        verifyStatus(delegate, status)
        assertJsonContains(headers, json)
    }

    /**
     * Asserts response status and JSON tree contains the expected subset expressed as JSON text.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param json expected subset encoded as JSON text
     * @return same response for fluent chaining
     */
    TestHttpResponse assertJsonContains(Map<String, String> headers = EMPTY, int status, CharSequence json) {
        verifyStatus(delegate, status)
        assertJsonContains(headers, json)
    }

    // endregion
    // endregion
    // region TEXT BODY

    /**
     * Asserts exact response body equality.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param body expected response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertEquals(Map<String, String> headers = EMPTY, CharSequence body) {
        verifyHeaders(delegate, headers)
        verifyBody(delegate, body)
        this
    }

    /**
     * Asserts status and exact response body equality.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param body expected response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertEquals(Map<String, String> headers = EMPTY, int status, CharSequence body) {
        verifyStatus(delegate, status)
        assertEquals(headers, body)
        this
    }

    /**
     * Asserts that the response body contains {@code body} as a substring.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param body substring expected to be present in the response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertContains(Map<String, String> headers = EMPTY, CharSequence body) {
        verifyHeaders(delegate, headers)
        verifyBodyContains(delegate, body)
        this
    }

    /**
     * Asserts status and that the response body contains {@code body} as a substring.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param body substring expected to be present in the response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertContains(Map<String, String> headers = EMPTY, int status, CharSequence body) {
        verifyStatus(delegate, status)
        assertContains(headers, body)
    }

    /**
     * Asserts that the response body contains a regex match using {@link java.util.regex.Matcher#find()}.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param pattern pattern that must be found somewhere in the response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertContainsMatches(Map<String, String> headers = EMPTY, Pattern pattern) {
        verifyHeaders(delegate, headers)
        verifyContainsMatches(delegate, pattern)
        this
    }

    /**
     * Asserts status and that the response body contains a regex match using {@code find()}.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param pattern pattern that must be found somewhere in the response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertContainsMatches(Map<String, String> headers = EMPTY, int status, Pattern pattern) {
        verifyStatus(delegate, status)
        assertContainsMatches(headers, pattern)
    }

    /**
     * Asserts that the entire response body matches the regex using {@link java.util.regex.Matcher#matches()}.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param pattern pattern that must match the full response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertMatches(Map<String, String> headers = EMPTY, Pattern pattern) {
        verifyHeaders(delegate, headers)
        verifyMatches(delegate, pattern)
        this
    }

    /**
     * Asserts status and that the entire response body matches the regex using {@code matches()}.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param pattern pattern that must match the full response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertMatches(Map<String, String> headers = EMPTY, int status, Pattern pattern) {
        verifyStatus(delegate, status)
        assertMatches(headers, pattern)
    }

    /**
     * Asserts that the full response body does not equal {@code body}.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param body unexpected full response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertNotEquals(Map<String, String> headers = EMPTY, CharSequence body) {
        verifyHeaders(delegate, headers)
        verifyNotBody(delegate, body)
        this
    }

    /**
     * Asserts status and that the full response body does not equal {@code body}.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param body unexpected full response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertNotEquals(Map<String, String> headers = EMPTY, int status, CharSequence body) {
        verifyStatus(delegate, status)
        assertNotEquals(headers, body)
    }

    /**
     * Asserts that the response body does not contain {@code text} as a substring.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param text unexpected substring
     * @return same response for fluent chaining
     */
    TestHttpResponse assertNotContains(Map<String, String> headers = EMPTY, CharSequence text) {
        verifyHeaders(delegate, headers)
        verifyNotBodyContains(delegate, text)
        this
    }

    /**
     * Asserts status and that the response body does not contain {@code text} as a substring.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param text unexpected substring
     * @return same response for fluent chaining
     */
    TestHttpResponse assertNotContains(Map<String, String> headers = EMPTY, int status, CharSequence text) {
        verifyStatus(delegate, status)
        assertNotContains(headers, text)
    }

    /**
     * Asserts that the response body does not contain a regex match using {@code find()}.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param pattern pattern that must not be found anywhere in the response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertNotContainsMatches(Map<String, String> headers = EMPTY, Pattern pattern) {
        verifyHeaders(delegate, headers)
        verifyNotContainsMatches(delegate, pattern)
        this
    }

    /**
     * Asserts status and that the response body does not contain a regex match using {@code find()}.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param pattern pattern that must not be found anywhere in the response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertNotContainsMatches(Map<String, String> headers = EMPTY, int status, Pattern pattern) {
        verifyStatus(delegate, status)
        assertNotContainsMatches(headers, pattern)
    }

    /**
     * Asserts that the response body does not fully match the regex using {@code matches()}.
     *
     * @param headers expected headers to validate;
     *        ignored when empty and treated as a subset match when provided,
     *        meaning additional response headers are ignored
     * @param pattern pattern that must not match the full response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertNotMatches(Map<String, String> headers = EMPTY, Pattern pattern) {
        verifyHeaders(delegate, headers)
        verifyNotMatches(delegate, pattern)
        this
    }

    /**
     * Asserts status and that the response body does not fully match the regex using {@code matches()}.
     *
     * @param headers expected headers; ignored when empty
     * @param status expected HTTP status
     * @param pattern pattern that must not match the full response body
     * @return same response for fluent chaining
     */
    TestHttpResponse assertNotMatches(Map<String, String> headers = EMPTY, int status, Pattern pattern) {
        verifyStatus(delegate, status)
        assertNotMatches(headers, pattern)
    }

    // endregion
    // region INTERFACE HttpResponse

    /** @return delegated HTTP status code */
    @Override
    int statusCode() {
        delegate.statusCode()
    }

    /** @return delegated original request */
    @Override
    HttpRequest request() {
        delegate.request()
    }

    /**
     * Returns the previous response, wrapped as {@link TestHttpResponse} when present.
     *
     * @return optional previous response wrapper preserving this instance's JSON and XML parser configuration
     */
    @Override
    Optional<HttpResponse<?>> previousResponse() {
        delegate.previousResponse().map { new TestHttpResponse((HttpResponse) it, jsonSlurperConfig, xmlSlurperConfig) }
    }

    /** @return delegated response headers */
    @Override
    HttpHeaders headers() {
        delegate.headers()
    }

    /** @return delegated response body as a String */
    @Override
    String body() {
        delegate.body()
    }

    /** @return delegated SSL session, if present */
    @Override
    Optional<SSLSession> sslSession() {
        delegate.sslSession()
    }

    /** @return delegated response URI */
    @Override
    URI uri() {
        delegate.uri()
    }

    @Override
    /** @return delegated HTTP protocol version */
    HttpClient.Version version() {
        delegate.version()
    }

    // endregion
    // region VERIFIERS

    private static void verifyStatus(HttpResponse<?> r, int expected) {
        verify(r.statusCode(), expected, 'HTTP Status differs')
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

    private void verifyJsonTree(HttpResponse<?> r, Object expected) {
        JsonUtils.verifyJsonTree(r, expected, jsonSlurperConfig)
    }

    private void verifyJsonTree(HttpResponse<?> r, CharSequence expectedJson) {
        JsonUtils.verifyJsonTree(r, expectedJson, jsonSlurperConfig)
    }

    private void verifyJsonTreeContains(HttpResponse<?> r, Object expected) {
        JsonUtils.verifyJsonTreeContains(r, expected, jsonSlurperConfig)
    }

    private void verifyJsonTreeContains(HttpResponse<?> r, CharSequence expectedJson) {
        JsonUtils.verifyJsonTreeContains(r, expectedJson, jsonSlurperConfig)
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
            throw new AssertionFailedError('Body should not fully match pattern', "not $pattern", actual)
        }
    }

    private static void verifyNotContainsMatches(HttpResponse<?> r, Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException('pattern must not be null')
        }
        def actual = r.body() as String ?: ''
        if (pattern.matcher(actual).find()) {
            throw new AssertionFailedError('Body should not contain pattern match', "not $pattern", actual)
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
        r.headers().map()
                .findAll { key, values -> key?.equalsIgnoreCase(name) && values }
                .collectMany { key, values -> values }
    }

    // endregion
}
