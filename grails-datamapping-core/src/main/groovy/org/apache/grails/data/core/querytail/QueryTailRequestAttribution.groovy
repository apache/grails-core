/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.data.core.querytail

import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

import groovy.transform.CompileStatic

import org.springframework.util.ClassUtils
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Attributes a captured statement to the web request on the current thread. Grails keeps the
 * request, and the controller and action it resolved, on the thread that serves it, so a statement
 * run by an interceptor, the action or the view all land on the same page. The first statement of
 * a request gives it an id that every later statement of that request repeats, which is how the
 * {@code query-tail} command counts statements per request. Work on any other thread is attributed to
 * the thread by name.
 *
 * <p>The request is recorded as its method and path, with the values in the path replaced by
 * {@code ?}: the values of the parameters the URL mapping took from it and those of its query, found
 * whether or not the client percent-encoded them, and any segment that is a number, a UUID or a long
 * hexadecimal token, so that an id, a token or a key carried in a path never reaches the capture
 * file.</p>
 *
 * <p>Spring Web is optional: without it on the classpath every statement is attributed to its
 * thread.</p>
 *
 * @since 8.1
 */
@CompileStatic
class QueryTailRequestAttribution {

    /** The request attribute holding the id this attribution gave a request. */
    public static final String REQUEST_ID_ATTRIBUTE = 'org.apache.grails.data.core.querytail.REQUEST_ID'

    /** The request attribute Grails sets the controller name in. */
    public static final String CONTROLLER_NAME_ATTRIBUTE = 'org.grails.CONTROLLER_NAME_ATTRIBUTE'

    /** The request attribute Grails sets the controller namespace in. */
    public static final String CONTROLLER_NAMESPACE_ATTRIBUTE = 'org.grails.CONTROLLER_NAMESPACE_ATTRIBUTE'

    /** The request attribute Grails sets the action name in. */
    public static final String ACTION_NAME_ATTRIBUTE = 'org.grails.ACTION_NAME_ATTRIBUTE'

    private static final boolean SPRING_WEB_PRESENT = ClassUtils.isPresent(
            'org.springframework.web.context.request.RequestContextHolder', QueryTailRequestAttribution.classLoader)

    private final AtomicLong requestCounter = new AtomicLong()
    private final String requestIdPrefix

    /**
     * An attribution whose request ids are {@code r1}, {@code r2} and so on.
     */
    QueryTailRequestAttribution() {
        this('')
    }

    /**
     * @param requestIdPrefix what every request id starts with, which tells the requests of one JVM
     * from those of another writing the same capture
     */
    QueryTailRequestAttribution(String requestIdPrefix) {
        this.requestIdPrefix = requestIdPrefix ?: ''
    }

    /**
     * Fills in the request id, page and request of a record from the current thread.
     *
     * @param record the record of a statement that just ran on this thread
     * @return the same record
     */
    QueryTailRecord attribute(QueryTailRecord record) {
        if (SPRING_WEB_PRESENT && WebRequestLookup.attribute(record, requestCounter, requestIdPrefix)) {
            return record
        }
        record.requestId = null
        record.request = null
        record.page = QueryTailRecord.THREAD_PAGE_PREFIX + Thread.currentThread().name
        record
    }

    /**
     * Keeps every reference to Spring Web, in a signature as well as in a body, in a class that is only
     * loaded once Spring Web is known to be present. The outer class can then be loaded, and reflected
     * on, without Spring Web on the classpath.
     */
    @CompileStatic
    private static class WebRequestLookup {

        private static final String REQUEST_ID_PREFIX = 'r'

        private static final String PLACEHOLDER = '?'

        /** The parameters a URL mapping sets that name code rather than carry a value. */
        private static final Set<String> MAPPING_PARAMETERS = ['controller', 'action', 'namespace', 'plugin', 'format'] as Set<String>

        /** A segment that is a number, a UUID or a long hexadecimal token is a value whatever the mapping says. */
        private static final Pattern VALUE_SHAPED = Pattern.compile(
                '(?i)[0-9]+|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[0-9a-f]{32,}')

        private static final boolean SERVLET_PRESENT = ClassUtils.isPresent(
                'jakarta.servlet.http.HttpServletRequest', WebRequestLookup.classLoader)

        /** The {@code getParams()} of a Grails web request, which holds the parameters its URL mapping took, by class. */
        private static final ClassValue<Method> PARAMETERS = new ClassValue<Method>() {
            @Override
            protected Method computeValue(Class<?> type) {
                try {
                    return type.getMethod('getParams')
                }
                catch (NoSuchMethodException ignored) {
                    return null
                }
            }
        }

        /**
         * Fills in a record from the request on the current thread.
         *
         * @param record the record to fill in
         * @param requestCounter the counter the next request id is taken from
         * @param requestIdPrefix what every request id starts with
         * @return whether there is a request on the current thread
         */
        static boolean attribute(QueryTailRecord record, AtomicLong requestCounter, String requestIdPrefix) {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes()
            if (attributes == null) {
                return false
            }
            record.requestId = requestId(attributes, requestCounter, requestIdPrefix)
            record.page = page(attributes)
            record.request = SERVLET_PRESENT ? ServletRequestLookup.describe(attributes) : null
            true
        }

        /**
         * Builds the request line: the method and the path, with the values in the path, the parameters
         * the URL mapping took from it among them, replaced.
         *
         * @param method the HTTP method
         * @param path the request path
         * @param attributes the request, whose mapped parameters are read when it is a Grails web request
         * @param parameters the parameters of the servlet request, by name, each a {@code String[]}
         */
        static String describe(String method, String path, RequestAttributes attributes, Map<?, ?> parameters) {
            Set<String> values = new HashSet<>()
            addValues(values, mappedParameters(attributes))
            addValues(values, parameters)
            "${method} ${redactPath(path, values)}".toString()
        }

        private static String requestId(RequestAttributes attributes, AtomicLong requestCounter, String requestIdPrefix) {
            synchronized (attributes) {
                Object existing = attributes.getAttribute(REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
                if (existing != null) {
                    return existing.toString()
                }
                String id = requestIdPrefix + REQUEST_ID_PREFIX + requestCounter.incrementAndGet()
                attributes.setAttribute(REQUEST_ID_ATTRIBUTE, id, RequestAttributes.SCOPE_REQUEST)
                return id
            }
        }

        private static String page(RequestAttributes attributes) {
            Object controller = attributes.getAttribute(CONTROLLER_NAME_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
            if (controller == null) {
                return null
            }
            Object namespace = attributes.getAttribute(CONTROLLER_NAMESPACE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
            Object action = attributes.getAttribute(ACTION_NAME_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
            StringBuilder page = new StringBuilder()
            if (namespace != null) {
                page.append(namespace).append(':')
            }
            page.append(controller)
            if (action != null) {
                page.append('/').append(action)
            }
            page.toString()
        }

        /**
         * @return the parameters of a Grails web request, which include those its URL mapping took from
         * the path, or null for a request that is not one
         */
        private static Map<?, ?> mappedParameters(RequestAttributes attributes) {
            Method parameters = PARAMETERS.get(attributes.getClass())
            if (parameters == null) {
                return null
            }
            try {
                Object value = parameters.invoke(attributes)
                return value instanceof Map ? (Map<?, ?>) value : null
            }
            catch (ReflectiveOperationException | RuntimeException ignored) {
                return null
            }
        }

        private static void addValues(Set<String> values, Map<?, ?> parameters) {
            if (parameters == null) {
                return
            }
            for (Map.Entry<?, ?> entry in parameters.entrySet()) {
                if (MAPPING_PARAMETERS.contains(String.valueOf(entry.key))) {
                    continue
                }
                Object value = entry.value
                if (value instanceof CharSequence) {
                    values.add(value.toString())
                }
                else if (value instanceof Object[]) {
                    for (Object element in (Object[]) value) {
                        if (element instanceof CharSequence) {
                            values.add(element.toString())
                        }
                    }
                }
            }
        }

        /**
         * Replaces the values in a path: a segment that is one of the values, with or without an
         * extension, a run of segments that spells a value holding slashes, and any segment shaped
         * like a value. The segments are matched as the client sent them and percent-decoded, since
         * the parameters are decoded while the request URI is not; the path is written as sent.
         */
        private static String redactPath(String path, Set<String> values) {
            String[] segments = path.split('/', -1)
            String[] decoded = new String[segments.length]
            boolean[] redacted = new boolean[segments.length]
            for (int i = 0; i < segments.length; i++) {
                decoded[i] = decode(segments[i])
                redacted[i] = isValue(segments[i], values) || isValue(decoded[i], values)
            }
            for (String value in values) {
                if (value.contains('/')) {
                    String[] run = value.split('/', -1)
                    markRun(segments, redacted, run)
                    markDecodedRun(decoded, redacted, run)
                }
            }
            StringBuilder out = new StringBuilder(path.length())
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) {
                    out.append('/')
                }
                out.append(redacted[i] ? PLACEHOLDER : segments[i])
            }
            out.toString()
        }

        /**
         * @return the segment with its percent-encoding decoded, or as it is when it has none or is not
         * valid percent-encoding; a plus sign is kept, since it is literal in a path
         */
        private static String decode(String segment) {
            if (segment.indexOf('%') < 0) {
                return segment
            }
            try {
                return URLDecoder.decode(segment.replace('+', '%2B'), StandardCharsets.UTF_8)
            }
            catch (IllegalArgumentException ignored) {
                return segment
            }
        }

        private static boolean isValue(String segment, Set<String> values) {
            if (segment.isEmpty()) {
                return false
            }
            int dot = segment.lastIndexOf('.')
            String bare = dot > 0 ? segment.substring(0, dot) : segment
            values.contains(segment) || values.contains(bare) || VALUE_SHAPED.matcher(bare).matches()
        }

        /**
         * Marks the segments whose decoded forms spell the run; a decoded segment may itself hold
         * slashes, which the client encoded, so the decoded forms are split again before matching
         * and a match marks the segments the matched parts came from.
         */
        private static void markDecodedRun(String[] decoded, boolean[] redacted, String[] run) {
            List<String> parts = []
            List<Integer> owners = []
            for (int i = 0; i < decoded.length; i++) {
                for (String part in decoded[i].split('/', -1)) {
                    parts.add(part)
                    owners.add(i)
                }
            }
            boolean[] partRedacted = new boolean[parts.size()]
            markRun(parts as String[], partRedacted, run)
            for (int i = 0; i < partRedacted.length; i++) {
                if (partRedacted[i]) {
                    redacted[owners.get(i)] = true
                }
            }
        }

        private static void markRun(String[] segments, boolean[] redacted, String[] run) {
            if (run.length == 0 || run.length > segments.length) {
                return
            }
            for (int start = 0; start + run.length <= segments.length; start++) {
                boolean matches = true
                for (int i = 0; i < run.length && matches; i++) {
                    matches = segments[start + i] == run[i]
                }
                if (matches) {
                    for (int i = 0; i < run.length; i++) {
                        redacted[start + i] = true
                    }
                }
            }
        }
    }

    /**
     * Keeps every reference to the servlet API in a class that is only loaded once the servlet API is
     * known to be present, as {@link WebRequestLookup} does for Spring Web.
     */
    @CompileStatic
    private static class ServletRequestLookup {

        /**
         * @return the request line of the servlet request on the current thread, or null when the request
         * is not a servlet request
         */
        static String describe(RequestAttributes attributes) {
            if (!(attributes instanceof ServletRequestAttributes)) {
                return null
            }
            def request = ((ServletRequestAttributes) attributes).request
            WebRequestLookup.describe((String) request.method, (String) request.requestURI, attributes,
                    (Map<?, ?>) request.parameterMap)
        }
    }
}
