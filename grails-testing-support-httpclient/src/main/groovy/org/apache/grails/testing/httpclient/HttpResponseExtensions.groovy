package org.apache.grails.testing.httpclient

import java.net.http.HttpResponse
import java.util.regex.Pattern

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult

import org.opentest4j.AssertionFailedError

/**
 * Extension methods for {@link HttpResponse} used in HTTP/integration tests.
 * <p>
 * Provides convenience accessors ({@code json()}, {@code xml()}, header helpers) and fluent assertion
 * helpers ({@code expect*}/{@code expectNot*}) for status, headers, body content, regex matching,
 * and JSON structure checks.
 * <p>
 * Regex semantics:
 * <ul>
 *   <li>{@code expectContainsMatches} uses partial matching ({@code find()})</li>
 *   <li>{@code expectMatches} uses full-body matching ({@code matches()})</li>
 * </ul>
 */
class HttpResponseExtensions {

    /** Parses the response body as a JSON object. */
    static Map json(HttpResponse<?> self) {
        (Map) new JsonSlurper().parseText(self.body() as String)
    }

    /** Parses the response body as a JSON array. */
    static List jsonList(HttpResponse<?> self) {
        (List) new JsonSlurper().parseText(self.body() as String)
    }

    /** Parses the response body as XML. */
    static GPathResult xml(HttpResponse<?> self) {
        new XmlSlurper().parseText(self.body() as String)
    }

    /** Returns the first value for the given header, or {@code null} when missing. */
    static String headerValue(HttpResponse<?> self, String name) {
        self.headers().firstValue(name).orElse(null)
    }

    /**
     *  Returns the first value for the given header as a long.
     *  @throws NoSuchElementException if no header is found
     *  @throws NumberFormatException if a value is found, but does not parse as a Long
     */
    static long headerValueAsLong(HttpResponse<?> self, String name) {
        self.headers().firstValueAsLong(name).orElseThrow()
    }

    /** Shortcut for the {@code Content-Type} header value. */
    static String getContentType(HttpResponse<?> self) {
        headerValue(self, 'Content-Type')
    }

    /** @return {@code true} if the response contains the named header. */
    static boolean hasHeader(HttpResponse<?> self, String name) {
        self.headers().firstValue(name).isPresent()
    }

    /** @return {@code true} if the named header exists and equals {@code expected}. */
    static boolean hasHeaderValue(HttpResponse<?> self, String name, String expected) {
        self.headers().firstValue(name).map { it == expected }.orElse(false)
    }

    /** @return {@code true} if the named header exists and equals {@code expected} ignoring case. */
    static boolean hasHeaderValueIgnoreCase(HttpResponse<?> self, String name, String expected) {
        def expectedLower = expected == null ? null : expected.toLowerCase(Locale.ENGLISH)
        def values = headerValuesIgnoreCase(self, name)
        values.any { String actual ->
            (actual == null && expectedLower == null) ||
                    (actual != null && expectedLower != null && actual.toLowerCase(Locale.ENGLISH) == expectedLower)
        }
    }

    /**
     * Asserts response status and optional headers.
     *
     * @return same response for fluent chaining
     */
    static HttpResponse<?> expectStatus(HttpResponse<?> self, Map<String, String> headers = Collections.emptyMap(),
                                        int status) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        self
    }

    /** Asserts exact values for the provided header names. */
    static HttpResponse<?> expectHeaders(HttpResponse<?> self, Map<String, String> expected) {
        verifyHeaders(self, expected)
        self
    }

    /** Asserts status and exact values for the provided header names. */
    static HttpResponse<?> expectHeaders(HttpResponse<?> self, Map<String, String> expected, int status) {
        verifyStatus(self, status)
        verifyHeaders(self, expected)
        self
    }

    /** Asserts exact header values with case-insensitive header-name/value matching. */
    static HttpResponse<?> expectHeadersIgnoreCase(HttpResponse<?> self, Map<String, String> expected) {
        verifyHeadersIgnoreCase(self, expected)
        self
    }

    /** Asserts status and exact header values with case-insensitive name/value matching. */
    static HttpResponse<?> expectHeadersIgnoreCase(HttpResponse<?> self, Map<String, String> expected, int status) {
        verifyStatus(self, status)
        verifyHeadersIgnoreCase(self, expected)
        self
    }

    /** Asserts JSON tree equality against the provided JSON text. */
    static HttpResponse<?> expectJson(HttpResponse<?> self, CharSequence json) {
        verifyJsonTree(self, json.toString())
        self
    }

    /** Asserts JSON tree equality against the provided map. */
    static HttpResponse<?> expectJson(HttpResponse<?> self, Map json) {
        verifyJsonTree(self, json)
        self
    }

    /** Asserts full response body equality. */
    static HttpResponse<?> expect(HttpResponse<?> self, CharSequence body) {
        verifyBody(self, body)
        self
    }

    /** Asserts status, headers, and full response body equality. */
    static HttpResponse<?> expect(HttpResponse<?> self, Map<String, String> headers = Collections.emptyMap(),
                                  int status, CharSequence body) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyBody(self, body)
        self
    }

    /** Asserts response body contains the expected text and optional headers. */
    static HttpResponse<?> expectContains(HttpResponse<?> self, Map<String, String> headers = Collections.emptyMap(),
                                          CharSequence body) {
        verifyHeaders(self, headers)
        verifyBodyContains(self, body)
        self
    }

    /** Asserts status, headers, and that response body contains the expected text. */
    static HttpResponse<?> expectContains(HttpResponse<?> self, Map<String, String> headers = Collections.emptyMap(),
                                          int status, CharSequence body) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyBodyContains(self, body)
        self
    }

    /** Asserts status, headers, and JSON tree equality against the provided map. */
    static HttpResponse<?> expectJson(HttpResponse<?> self, Map<String, String> headers = Collections.emptyMap(),
                                      int status, Map json) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyJsonTree(self, json)
        self
    }

    /** Asserts status, headers, and JSON tree equality against the provided JSON text. */
    static HttpResponse<?> expectJson(HttpResponse<?> self, Map<String, String> headers = Collections.emptyMap(),
                                      int status, CharSequence json) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyJsonTree(self, json.toString())
        self
    }

    /** Asserts response JSON contains the expected subset and status. */
    static HttpResponse<?> expectJsonContains(HttpResponse<?> self, int status, Object expected) {
        verifyStatus(self, status)
        verifyJsonContains(self, expected)
        self
    }

    /** Asserts response JSON contains the expected subset and optional headers. */
    static HttpResponse<?> expectJsonContains(HttpResponse<?> self,
                                              Map<String, String> headers = Collections.emptyMap(), Map expected) {
        verifyHeaders(self, headers)
        verifyJsonContains(self, (Object) expected)
        self
    }

    /** Asserts response JSON contains the expected subset with status and headers. */
    static HttpResponse<?> expectJsonContains(HttpResponse<?> self, Map<String, String> headers = Collections.emptyMap(),
                                   int status, Map expected) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyJsonContains(self, (Object) expected)
        self
    }

    /** Asserts response body contains a regex match ({@code find()}). */
    static HttpResponse<?> expectContainsMatches(HttpResponse<?> self, Pattern pattern) {
        verifyContainsMatches(self, pattern)
        self
    }

    /** Asserts status, headers, and that response body contains a regex match ({@code find()}). */
    static HttpResponse<?> expectContainsMatches(HttpResponse<?> self,
                                                 Map<String, String> headers = Collections.emptyMap(), int status,
                                                 Pattern pattern) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyContainsMatches(self, pattern)
        self
    }

    /** Asserts response body fully matches the regex ({@code matches()}). */
    static HttpResponse<?> expectMatches(HttpResponse<?> self, Pattern pattern) {
        verifyMatches(self, pattern)
        self
    }

    /** Asserts status, headers, and full regex match ({@code matches()}). */
    static HttpResponse<?> expectMatches(HttpResponse<?> self, Map<String, String> headers = Collections.emptyMap(),
                                         int status, Pattern pattern) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyMatches(self, pattern)
        self
    }

    /** Asserts response status does not equal {@code status}. */
    static HttpResponse<?> expectNotStatus(HttpResponse<?> self, int status) {
        verifyNotStatus(self, status)
        self
    }

    /** Asserts provided headers do not match the given values. */
    static HttpResponse<?> expectNotHeaders(HttpResponse<?> self, Map<String, String> expected) {
        verifyNotHeaders(self, expected)
        self
    }

    /** Asserts full response body is not equal to {@code body}. */
    static HttpResponse<?> expectNot(HttpResponse<?> self, CharSequence body) {
        verifyNotBody(self, body)
        self
    }

    /** Asserts status/headers and that full response body is not equal to {@code body}. */
    static HttpResponse<?> expectNot(HttpResponse<?> self, Map<String, String> headers = Collections.emptyMap(),
                                     int status, CharSequence body) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyNotBody(self, body)
        self
    }

    /** Asserts response body does not contain {@code body}. */
    static HttpResponse<?> expectNotContains(HttpResponse<?> self, CharSequence body) {
        verifyNotBodyContains(self, body)
        self
    }

    /** Asserts status/headers and that response body does not contain {@code body}. */
    static HttpResponse<?> expectNotContains(HttpResponse<?> self, Map<String, String> headers = Collections.emptyMap(),
                                             int status, CharSequence body) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyNotBodyContains(self, body)
        self
    }

    /** Asserts response body does not contain a regex match ({@code find()}). */
    static HttpResponse<?> expectNotContainsMatches(HttpResponse<?> self, Pattern pattern) {
        verifyNotContainsMatches(self, pattern)
        self
    }

    /** Asserts status/headers and that response body does not contain a regex match ({@code find()}). */
    static HttpResponse<?> expectNotContainsMatches(HttpResponse<?> self,
                                                    Map<String, String> headers = Collections.emptyMap(),
                                                    int status,
                                                    Pattern pattern) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyNotContainsMatches(self, pattern)
        self
    }

    /** Asserts response body does not fully match the regex ({@code matches()}). */
    static HttpResponse<?> expectNotMatches(HttpResponse<?> self, Pattern pattern) {
        verifyNotMatches(self, pattern)
        self
    }

    /** Asserts status/headers and that response body does not fully match the regex ({@code matches()}). */
    static HttpResponse<?> expectNotMatches(HttpResponse<?> self,
                                            Map<String, String> headers = Collections.emptyMap(),
                                            int status,
                                            Pattern pattern) {
        verifyStatus(self, status)
        verifyHeaders(self, headers)
        verifyNotMatches(self, pattern)
        self
    }

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

    private static void verifyBody(HttpResponse<?> r, CharSequence expectedBody) {
        def actual = r.body().toString()
        def expected = expectedBody.toString()
        if (actual != expected) {
            throw new AssertionFailedError('Body differs', expected, actual)
        }
    }

    private static void verifyBodyContains(HttpResponse<?> r, CharSequence text) {
        def body = r.body().toString()
        def expected = text.toString()
        if (!body.contains(expected)) {
            throw new AssertionFailedError('Body does not contain value', expected, body)
        }
    }

    private static void verifyJsonTree(HttpResponse<?> r, Map expected) {
        def actual = new JsonSlurper().parseText(r.body().toString())
        verifyJson(actual, expected)
    }

    private static void verifyJsonTree(HttpResponse<?> r, String expectedJson) {
        def actual = prettyCanonicalJsonSorted(r.body().toString())
        def expected = prettyCanonicalJsonSorted(expectedJson)
        verify(actual, expected, 'JSON tree differs')
    }

    private static void verifyJsonTree(Object actual, Map expected) {
        def a = canonicalFromObject(actual)
        def e = canonicalFromObject(expected)
        if (a != e) {
            throw new AssertionFailedError('JSON tree differs', e, a)
        }
    }

    private static void verify(Object actual, Object expected, String errorMessage) {
        if (actual != expected) {
            throw new AssertionFailedError(errorMessage, expected, actual)
        }
    }

    private static void verifyJson(Object actual, Map expected) {
        verifyJsonTree(actual, expected)
    }

    private static String prettyCanonicalJsonSorted(CharSequence json) {
        def tree = new JsonSlurper().parseText(json.toString())
        def sortedTree = deepSortJson(tree)
        JsonOutput.prettyPrint(JsonOutput.toJson(sortedTree))
    }

    private static void verifyJsonContains(HttpResponse<?> r, Object expected) {
        def actual = new JsonSlurper().parseText(r.body()?.toString() ?: '')
        def errors = []
        jsonContains(actual, expected, '$', errors)
        assert errors.isEmpty() : """JSON does not contain expected subset.

Expected subset:
${prettyCanonicalFromObject(expected)}

Actual:
${prettyCanonicalFromObject(actual)}

Mismatches:
- ${errors.join('\n- ')}
"""
    }

    private static void jsonContains(Object actual, Object expected, String path, List<String> errors) {
        if (expected instanceof Map) {
            if (!(actual instanceof Map)) {
                errors.add("$path expected object but was ${typeName(actual)}")
                return
            }
            expected.each { k, v ->
                if (!actual.containsKey(k)) {
                    errors.add("$path missing key '$k'")
                } else {
                    jsonContains(actual[k], v, "$path.$k", errors)
                }
            }
            return
        }

        if (expected instanceof List) {
            if (!(actual instanceof List)) {
                errors.add("$path expected array but was ${typeName(actual)}")
                return
            }
            if (actual.size() < expected.size()) {
                errors.add("$path expected array size >= ${expected.size()} but was ${actual.size()}")
                return
            }
            for (int i = 0; i < expected.size(); i++) {
                jsonContains(actual[i], expected[i], "$path[$i]", errors)
            }
            return
        }

        if (actual != expected) {
            errors.add("$path expected ${repr(expected)} but was ${repr(actual)}")
        }
    }

    private static String canonicalFromObject(Object obj) {
        def sorted = JsonOutput.toJson(deepSortJson(obj))
        if (sorted.length() > 300) {
            return JsonOutput.prettyPrint(sorted)
        }
        sorted
    }

    private static String prettyCanonicalFromObject(Object obj) {
        def sorted = deepSortJson(obj)
        JsonOutput.prettyPrint(JsonOutput.toJson(sorted))
    }

    private static Object deepSortJson(Object v) {
        if (v instanceof Map) {
            def sorted = new TreeMap()
            v.each { k, val -> sorted[k.toString()] = deepSortJson(val) }
            return sorted
        }
        if (v instanceof List) {
            return v.collect { deepSortJson(it) }
        }
        return v
    }

    private static String typeName(Object v) {
        v == null ? 'null' : v.getClass().name
    }

    private static String repr(Object v) {
        v == null ? 'null' : v.toString()
    }

    private static void verifyMatches(HttpResponse<?> r, Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException('pattern must not be null')
        }
        def actual = r.body()?.toString() ?: ''
        if (!pattern.matcher(actual).matches()) {
            throw new AssertionFailedError('Body does not fully match pattern', pattern.toString(), actual)
        }
    }

    private static void verifyContainsMatches(HttpResponse<?> r, Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException('pattern must not be null')
        }
        def actual = r.body()?.toString() ?: ''
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

    private static void verifyNotBody(HttpResponse<?> r, CharSequence unexpectedBody) {
        def actual = r.body().toString()
        def unexpected = unexpectedBody.toString()
        if (actual == unexpected) {
            throw new AssertionFailedError('Body should not match', "not $unexpected", actual)
        }
    }

    private static void verifyNotBodyContains(HttpResponse<?> r, CharSequence text) {
        def actual = r.body().toString()
        def unexpected = text.toString()
        if (actual.contains(unexpected)) {
            throw new AssertionFailedError('Body should not contain value', "not containing $unexpected", actual)
        }
    }

    private static void verifyNotMatches(HttpResponse<?> r, Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException('pattern must not be null')
        }
        def actual = r.body()?.toString() ?: ''
        if (pattern.matcher(actual).matches()) {
            throw new AssertionFailedError('Body should not fully match pattern', "not ${pattern}", actual)
        }
    }

    private static void verifyNotContainsMatches(HttpResponse<?> r, Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException('pattern must not be null')
        }
        def actual = r.body()?.toString() ?: ''
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
}
