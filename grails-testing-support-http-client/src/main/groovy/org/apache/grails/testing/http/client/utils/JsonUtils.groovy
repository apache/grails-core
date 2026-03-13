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
package org.apache.grails.testing.http.client.utils

import java.net.http.HttpResponse

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import org.opentest4j.AssertionFailedError

/**
 * Utility method for handling JSON.
 *
 * <p>JSON assertion helpers used by HTTP test response expectations.</p>
 *
 * <p>This utility provides two styles of validation:</p>
 * <ul>
 *   <li><b>Tree equality</b> via {@code verifyJsonTree(java.net.http.HttpResponse, Object)} and
 *   {@code #verifyJsonTree(java.net.http.HttpResponse, CharSequence)}, where object key order is ignored.</li>
 *   <li><b>Subset matching</b> via {@code verifyJsonTreeContains(java.net.http.HttpResponse, Object)} and
 *   {@code verifyJsonTreeContains(java.net.http.HttpResponse, CharSequence)}, where expected map keys
 *   must exist and expected list elements are matched by index.</li>
 * </ul>
 *
 * <p>Failures include canonicalized JSON representations to make diffs easier to read.</p>
 *
 * @since 7.0.9
 */
@CompileStatic
class JsonUtils {

    /**
     * Verifies that the response JSON is structurally equal to the expected object tree.
     *
     * <p>Map keys are compared independent of declaration order.</p>
     *
     * @param response HTTP response with a JSON body
     * @param expected expected JSON-compatible object (for example {@code Map}, {@code List}, scalar)
     * @throws AssertionFailedError if the JSON trees differ
     */
    static void verifyJsonTree(HttpResponse<?> response, Object expected) {
        compareJsonTree(new JsonSlurper().parseText(response.body() as String), expected)
    }

    /**
     * Verifies that the response JSON is structurally equal to the expected JSON string.
     *
     * <p>Both sides are parsed and canonicalized before comparison, so key ordering does not affect equality.</p>
     *
     * @param response HTTP response with a JSON body
     * @param expectedJson expected JSON document text
     * @throws AssertionFailedError if the JSON trees differ
     */
    static void verifyJsonTree(HttpResponse<?> response, CharSequence expectedJson) {
        def actual = prettyCanonicalJsonSorted(response.body() as String)
        def expected = prettyCanonicalJsonSorted(expectedJson)
        if (actual != expected) {
            throw new AssertionFailedError('JSON tree differs', expected, actual)
        }
    }

    /**
     * Verifies that the response JSON contains the expected subset.
     *
     * <p>Subset rules:</p>
     * <ul>
     *   <li>For maps, every expected key must exist and recursively match.</li>
     *   <li>For lists, expected elements are compared by index and the actual list must be at least as large.</li>
     *   <li>Scalar values use strict equality.</li>
     * </ul>
     *
     * @param response HTTP response with a JSON body
     * @param expected expected subset expressed as JSON-compatible objects
     * @throws AssertionError if the response does not contain the expected subset
     */
    static void verifyJsonTreeContains(HttpResponse<?> response, Object expected) {
        def actual = new JsonSlurper().parseText(response.body()?.toString() ?: '')
        verifyJsonTreeContainsParsed(actual, expected)
    }

    /**
     * Verifies that the response JSON contains the expected subset represented as JSON text.
     *
     * @param response HTTP response with a JSON body
     * @param expectedJson expected subset as JSON text
     * @throws AssertionError if the response does not contain the expected subset
     */
    static void verifyJsonTreeContains(HttpResponse<?> response, CharSequence expectedJson) {
        def actual = new JsonSlurper().parseText(response.body()?.toString() ?: '')
        def expected = new JsonSlurper().parseText(expectedJson?.toString() ?: '')
        verifyJsonTreeContainsParsed(actual, expected)
    }

    private static void verifyJsonTreeContainsParsed(Object actual, Object expected) {
        def errors = [] as List<String>
        verifyJsonContains(actual, expected, '$', errors)
        if (!errors.isEmpty()) {
            throw new AssertionError((Object) """JSON does not contain expected subset.

Expected subset:
${prettyCanonicalFromObject(expected)}

Actual:
${prettyCanonicalFromObject(actual)}

Mismatches:
- ${errors.join('\n- ')}
""")
        }
    }

    private static void compareJsonTree(Object actual, Object expected) {
        def a = canonicalFromObject(actual)
        def e = canonicalFromObject(expected)
        if (a != e) {
            throw new AssertionFailedError('JSON tree differs', e, a)
        }
    }

    private static String prettyCanonicalJsonSorted(CharSequence json) {
        def tree = new JsonSlurper().parseText(json.toString())
        def sortedTree = deepSortJson(tree)
        JsonOutput.prettyPrint(JsonOutput.toJson(sortedTree))
    }

    private static void verifyJsonContains(Object actual, Object expected, String path, List<String> errors) {
        if (expected instanceof Map) {
            if (!(actual instanceof Map)) {
                errors.add(("$path expected object but was ${typeName(actual)}").toString())
                return
            }
            expected.each { k, v ->
                if (!actual.containsKey(k)) {
                    errors.add(("$path missing key '$k'").toString())
                } else {
                    verifyJsonContains(actual[k], v, "$path.$k", errors)
                }
            }
            return
        }

        if (expected instanceof List) {
            if (!(actual instanceof List)) {
                errors.add(("$path expected array but was ${typeName(actual)}").toString())
                return
            }
            if (actual.size() < expected.size()) {
                errors.add(("$path expected array size >= ${expected.size()} but was ${actual.size()}").toString())
                return
            }
            for (int i = 0; i < expected.size(); i++) {
                verifyJsonContains(actual[i], expected[i], "$path[$i]", errors)
            }
            return
        }

        if (actual != expected) {
            errors.add(("$path expected ${repr(expected)} but was ${repr(actual)}").toString())
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

    private static Object deepSortJson(Object value) {
        if (value instanceof Map) {
            def sorted = new TreeMap()
            value.each { k, val -> sorted[k.toString()] = deepSortJson(val) }
            return sorted
        }
        if (value instanceof List) {
            return value.collect { deepSortJson(it) }
        }
        value
    }

    private static String typeName(Object value) {
        value == null ? 'null' : value.getClass().name
    }

    private static String repr(Object value) {
        value == null ? 'null' : value.toString()
    }
}
