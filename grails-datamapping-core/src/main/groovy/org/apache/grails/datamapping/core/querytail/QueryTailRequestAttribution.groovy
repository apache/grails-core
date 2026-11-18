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
package org.apache.grails.datamapping.core.querytail

import java.util.concurrent.atomic.AtomicLong

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest

import org.springframework.util.ClassUtils
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

import org.apache.grails.datastore.core.querytail.QueryTailRequestReader
import org.apache.grails.datastore.core.querytail.QueryTailRequestTarget

/**
 * Attributes a captured statement to the web request on the current thread. The framework that serves a
 * request keeps it, and what it resolved it to, on the thread that serves it, so a statement run by an
 * interceptor, the action or the view all land on the same page. The first statement of a request gives
 * it an id that every later statement of that request repeats, which is how the {@code query-tail}
 * command counts statements per request. Each capture file keeps its own request ids, so a request
 * recorded to another file cannot consume or reuse one of its ids. Work on any other thread is
 * attributed to the thread by name.
 *
 * <p>What a request was resolved to is read by the {@linkplain QueryTailRequestReader readers} on the
 * class path, which are found with {@link ServiceLoader}, so that this class depends on no web
 * framework: Grails URL mapping provides one, which gives a request the page of the controller and
 * action Grails resolved it to and reads its path against the URL mapping it matched. The request is
 * recorded as its method and path, with the values in the path replaced by {@code ?}: as the reader that
 * resolved the request reads it, else, for a request a Spring MVC handler serves, as the pattern its
 * handler mapping matched, whose variables are replaced. A request included in another, as
 * {@code <g:include>} includes one, is recorded as the URI it was included by, rather than the path of
 * the request that includes it. When the path cannot be read so, as for a statement of a filter that
 * runs before the controller is chosen, for a request that never reaches one, or for a path that does
 * not follow what matched it, no segment's role is known and every segment is replaced, so that an id,
 * a token or a key carried in a path never reaches the capture file. A request is described once and
 * its line reused for its later statements, until its path, the pattern it matched or what a reader
 * resolved it to changes, as a forward or an include changes them.</p>
 *
 * <p>Work a request starts on another thread that carries the request with it, as Grails promises
 * and {@code @Async} methods do, counts for the request. A statement that runs once the request
 * has completed is attributed to its thread.</p>
 *
 * <p>An endpoint that runs whichever operation its client sends, as a GraphQL endpoint does, serves
 * every operation on the one page; it names the operation it runs with {@link #beginOperation}, and
 * the statements the request runs until the operation ends are counted under the operation as well as
 * under the page: {@code graphql/index (query BookList)}.</p>
 *
 * <p>Spring Web is optional: without it on the classpath every statement is attributed to its
 * thread.</p>
 *
 * @since 9.0
 */
@CompileStatic
class QueryTailRequestAttribution {

    /** The prefix of the request attribute holding the id each attribution gave a request. */
    public static final String REQUEST_ID_ATTRIBUTE = 'org.apache.grails.datamapping.core.querytail.REQUEST_ID'

    private static final boolean SPRING_WEB_PRESENT = ClassUtils.isPresent(
            'org.springframework.web.context.request.RequestContextHolder', QueryTailRequestAttribution.classLoader)

    /** How many readers that cannot be loaded are passed over before the rest are given up on. */
    private static final int MAX_READER_FAILURES = 16

    /** The request attribute the operation a request runs is kept in. */
    private static final String OPERATION_ATTRIBUTE = 'org.apache.grails.datamapping.core.querytail.OPERATION'

    /** Ends no operation, as one begun with no request on the thread has none to end. */
    private static final Runnable NO_OPERATION = { -> } as Runnable

    private final AtomicLong requestCounter
    private final String requestIdPrefix
    private final String requestIdAttribute = REQUEST_ID_ATTRIBUTE + '.' + UUID.randomUUID()

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
        this(requestIdPrefix, new AtomicLong())
    }

    /**
     * @param requestIdPrefix what every request id starts with
     * @param requestCounter the counter the requests are numbered by, which attributions that give
     * their ids the same prefix share, so that none of them repeats an id another gave
     */
    @PackageScope
    QueryTailRequestAttribution(String requestIdPrefix, AtomicLong requestCounter) {
        this.requestIdPrefix = requestIdPrefix ?: ''
        this.requestCounter = requestCounter
    }

    /**
     * Fills in the request id, page and request of a record from the current thread.
     *
     * @param record the record of a statement that just ran on this thread
     * @return the same record
     */
    QueryTailRecord attribute(QueryTailRecord record) {
        if (SPRING_WEB_PRESENT) {
            try {
                if (WebRequestLookup.attribute(record, requestCounter, requestIdPrefix, requestIdAttribute)) {
                    return record
                }
            }
            catch (IllegalStateException ignored) {
                // the request has completed, as a task it started can outlive it, and is no longer to be read
            }
        }
        record.requestId = null
        record.request = null
        record.page = QueryTailRecord.THREAD_PAGE_PREFIX + Thread.currentThread().name
        record
    }

    /**
     * Counts the statements the request on the current thread runs, from now until the operation ends, under the
     * operation as well as under the page of the request: the page of each is the page of the request with the
     * operation after it, in parentheses, as {@code graphql/index (query BookList)}, so that an endpoint that runs
     * whichever operation its client sends, as a GraphQL endpoint does, shows each operation as a page of its own. A
     * request no reader resolved is shown by its request line with the operation after it. An operation begun while
     * another runs is the one the request runs until it ends, and the other runs on after it, whichever of them ends
     * first.
     *
     * @param operation names what the request runs; it is recorded as it is given, so it names no value
     * @return ends the operation, on whichever thread it ends, and does nothing once it has, or when there was no
     * request on the thread to begin it in
     */
    static Runnable beginOperation(String operation) {
        if (!SPRING_WEB_PRESENT || operation == null) {
            return NO_OPERATION
        }
        try {
            return WebRequestLookup.beginOperation(operation)
        }
        catch (IllegalStateException ignored) {
            // the request has completed, and runs no operation
            return NO_OPERATION
        }
    }

    /**
     * @return the readers on the class path of this class, in the order they are found; one that cannot be
     * loaded, as one of a framework only partly on the class path, reads nothing
     */
    private static List<QueryTailRequestReader> loadReaders() {
        List<QueryTailRequestReader> readers = []
        Iterator<QueryTailRequestReader> found = ServiceLoader.load(QueryTailRequestReader,
                QueryTailRequestAttribution.classLoader).iterator()
        // the iterator moves on past a reader that cannot be loaded as best it can, so only a few are passed over
        int failures = 0
        while (failures < MAX_READER_FAILURES) {
            try {
                if (!found.hasNext()) {
                    break
                }
                readers.add(found.next())
            }
            catch (ServiceConfigurationError | LinkageError ignored) {
                failures++
            }
        }
        List.copyOf(readers)
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

        private static final boolean SERVLET_PRESENT = ClassUtils.isPresent(
                'jakarta.servlet.http.HttpServletRequest', WebRequestLookup.classLoader)

        /** The request attribute a Spring MVC handler mapping keeps the pattern it matched a request with in. */
        private static final String MATCHED_PATTERN_ATTRIBUTE = 'org.springframework.web.servlet.HandlerMapping.bestMatchingPattern'

        /** The request attribute the line a request was described with is kept in, with what it was described from. */
        private static final String REQUEST_LINE_ATTRIBUTE = 'org.apache.grails.datamapping.core.querytail.REQUEST_LINE'

        /** The readers of what a web framework resolved a request to, found once. */
        private static final List<QueryTailRequestReader> READERS = loadReaders()

        /**
         * Fills in a record from the request on the current thread.
         *
         * @param record the record to fill in
         * @param requestCounter the counter the next request id is taken from
         * @param requestIdPrefix what every request id starts with
         * @param requestIdAttribute the request attribute this capture keeps its ids in
         * @return whether there is a request on the current thread
         */
        static boolean attribute(QueryTailRecord record, AtomicLong requestCounter, String requestIdPrefix, String requestIdAttribute) {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes()
            if (attributes == null) {
                return false
            }
            record.requestId = requestId(attributes, requestCounter, requestIdPrefix, requestIdAttribute)
            QueryTailRequestTarget target = targetOf(attributes)
            record.request = SERVLET_PRESENT ? ServletRequestLookup.describe(attributes, target) : null
            record.page = withOperation(target?.page, record.request, attributes)
            true
        }

        /**
         * Begins an operation of the request on the current thread.
         *
         * @param operation names what the request runs
         * @return ends it
         */
        static Runnable beginOperation(String operation) {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes()
            if (attributes == null) {
                return NO_OPERATION
            }
            synchronized (RunningOperation) {
                Object running = attributes.getAttribute(OPERATION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
                RunningOperation begun = new RunningOperation(operation, attributes,
                        running instanceof RunningOperation ? (RunningOperation) running : null)
                attributes.setAttribute(OPERATION_ATTRIBUTE, begun, RequestAttributes.SCOPE_REQUEST)
                return begun
            }
        }

        /**
         * @return the page of a statement: the page of the request, or its request line when no reader resolved it,
         * with the operation the request runs after it, if it runs one
         */
        private static String withOperation(String page, String request, RequestAttributes attributes) {
            Object operation = attributes.getAttribute(OPERATION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
            if (!(operation instanceof RunningOperation)) {
                return page
            }
            String shown = page ?: request
            String name = ((RunningOperation) operation).name
            shown ? "${shown} (${name})".toString() : "(${name})".toString()
        }

        /**
         * @return what the first reader that resolved the request resolved it to, or null when none did
         */
        private static QueryTailRequestTarget targetOf(RequestAttributes attributes) {
            if (READERS.isEmpty()) {
                return null
            }
            QueryTailRequestReader.Attributes request = new ReaderAttributes(attributes)
            for (QueryTailRequestReader reader : READERS) {
                QueryTailRequestTarget target
                try {
                    target = reader.read(request)
                }
                catch (RuntimeException ignored) {
                    // a reader that fails resolves nothing, and the statement is recorded all the same
                    continue
                }
                if (target != null) {
                    return target
                }
            }
            null
        }

        /**
         * Gives the request line of a request: the one described for an earlier statement of the request,
         * when the request has not moved on since, else one described now.
         *
         * @param method the HTTP method
         * @param path the request path, as the client sent it, with the context path
         * @param contextPath the context path of the application, which the path starts with
         * @param attributes the request
         * @param target what a reader resolved the request to, or null when none did
         */
        static String describe(String method, String path, String contextPath, RequestAttributes attributes, QueryTailRequestTarget target) {
            Object pattern = attributes.getAttribute(MATCHED_PATTERN_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
            RequestLine described = new RequestLine(method, path, target, pattern)
            Object earlier = attributes.getAttribute(REQUEST_LINE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
            if (earlier instanceof RequestLine && ((RequestLine) earlier).sameAs(described)) {
                return ((RequestLine) earlier).line
            }
            described.line = "${method} ${describePath(path, contextPath, described)}".toString()
            attributes.setAttribute(REQUEST_LINE_ATTRIBUTE, described, RequestAttributes.SCOPE_REQUEST)
            described.line
        }

        /**
         * @return the path of a request with its values replaced: as the reader that resolved it reads it,
         * or read against the pattern a Spring MVC handler mapping matched, else only its shape
         */
        private static String describePath(String path, String contextPath, RequestLine request) {
            if (request.target != null) {
                String read = readByTarget(path, contextPath, request.target)
                // a path the target does not read, as one kept from before a forward, tells no value from a literal
                return read != null ? read : withholdPath(path)
            }
            if (request.pattern != null) {
                return contextPath + patternPath(request.pattern.toString())
            }
            withholdPath(path)
        }

        /**
         * @return the path as the target a reader resolved the request to reads it below the context path, or
         * null when it does not read it
         */
        private static String readByTarget(String path, String contextPath, QueryTailRequestTarget target) {
            String prefix = contextPath && path.startsWith(contextPath) ? contextPath : ''
            String within = path.substring(prefix.length())
            if (!within.startsWith('/')) {
                return null
            }
            String read
            try {
                read = target.describePath(within)
            }
            catch (RuntimeException ignored) {
                return null
            }
            read == null ? null : prefix + read
        }

        /**
         * @return the pattern a Spring MVC handler mapping matched, with every segment that holds a
         * variable or a wildcard replaced
         */
        private static String patternPath(String pattern) {
            String[] segments = pattern.split('/', -1)
            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i]
                if (segment.contains('{') || segment.contains('*') || segment.contains('?')) {
                    segments[i] = PLACEHOLDER
                }
            }
            segments.join('/')
        }

        /**
         * Replaces every segment of a path: before the framework has resolved the request, or for a path
         * that does not follow what matched it, nothing tells a literal segment from a value, so only the
         * shape of the path is recorded.
         */
        private static String withholdPath(String path) {
            String[] segments = path.split('/', -1)
            StringBuilder out = new StringBuilder(path.length())
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) {
                    out.append('/')
                }
                out.append(segments[i].isEmpty() ? segments[i] : PLACEHOLDER)
            }
            out.toString()
        }

        private static String requestId(RequestAttributes attributes, AtomicLong requestCounter, String requestIdPrefix, String requestIdAttribute) {
            // the request itself, which a task the request starts sees through attributes of its own
            synchronized (SERVLET_PRESENT ? ServletRequestLookup.mutex(attributes) : attributes) {
                Object existing = attributes.getAttribute(requestIdAttribute, RequestAttributes.SCOPE_REQUEST)
                if (existing != null) {
                    return existing.toString()
                }
                String id = requestIdPrefix + REQUEST_ID_PREFIX + requestCounter.incrementAndGet()
                attributes.setAttribute(requestIdAttribute, id, RequestAttributes.SCOPE_REQUEST)
                return id
            }
        }
    }

    /**
     * An operation a request runs, which ends it once run. The operation it interrupted, if any, is the one the request
     * runs again once it ends, unless that one ended first, in which case the request runs the one before it.
     */
    @CompileStatic
    private static final class RunningOperation implements Runnable {

        final String name

        private final RequestAttributes attributes

        private final RunningOperation interrupted

        private boolean ended

        RunningOperation(String name, RequestAttributes attributes, RunningOperation interrupted) {
            this.name = name
            this.attributes = attributes
            this.interrupted = interrupted
        }

        @Override
        void run() {
            synchronized (RunningOperation) {
                if (ended) {
                    return
                }
                ended = true
                try {
                    // an operation begun after this one, and still running, is still the one the request runs
                    if (attributes.getAttribute(OPERATION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST).is(this)) {
                        RunningOperation resumed = interrupted
                        while (resumed != null && resumed.ended) {
                            resumed = resumed.interrupted
                        }
                        if (resumed == null) {
                            attributes.removeAttribute(OPERATION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
                        }
                        else {
                            attributes.setAttribute(OPERATION_ATTRIBUTE, resumed, RequestAttributes.SCOPE_REQUEST)
                        }
                    }
                }
                catch (IllegalStateException ignored) {
                    // the request has completed, and its attributes with it
                }
            }
        }
    }

    /**
     * The request scope of the request attributes on the current thread, as a reader reads them.
     */
    @CompileStatic
    private static final class ReaderAttributes implements QueryTailRequestReader.Attributes {

        private final RequestAttributes attributes

        ReaderAttributes(RequestAttributes attributes) {
            this.attributes = attributes
        }

        @Override
        Object getAttribute(String name) {
            attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST)
        }

        @Override
        void setAttribute(String name, Object value) {
            attributes.setAttribute(name, value, RequestAttributes.SCOPE_REQUEST)
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
        static String describe(RequestAttributes attributes, QueryTailRequestTarget target) {
            if (!(attributes instanceof ServletRequestAttributes)) {
                return null
            }
            HttpServletRequest request = ((ServletRequestAttributes) attributes).request
            String contextPath = request.contextPath ?: ''
            // an included request keeps the path of the request including it; it serves the URI it was included by
            Object included = request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI)
            if (included instanceof String) {
                Object includedContextPath = request.getAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH)
                return WebRequestLookup.describe(request.method, (String) included,
                        includedContextPath instanceof String ? (String) includedContextPath : contextPath, attributes, target)
            }
            WebRequestLookup.describe(request.method, request.requestURI, contextPath, attributes, target)
        }

        /**
         * @return what the request of the attributes is numbered under: the servlet request, which every
         * thread the request runs on sees the same, else the attributes
         */
        static Object mutex(RequestAttributes attributes) {
            attributes instanceof ServletRequestAttributes ? ((ServletRequestAttributes) attributes).request : attributes
        }
    }

    /**
     * The line a request was described with, and what it was described from, so that a later statement
     * of the request reuses it until the request moves on, as a forward or an include moves it, or a
     * reader resolves it to another target.
     */
    @CompileStatic
    private static final class RequestLine {

        final String method
        final String path
        final QueryTailRequestTarget target
        final Object pattern
        String line

        RequestLine(String method, String path, QueryTailRequestTarget target, Object pattern) {
            this.method = method
            this.path = path
            this.target = target
            this.pattern = pattern
        }

        boolean sameAs(RequestLine other) {
            method == other.method && path == other.path && target.is(other.target) && pattern == other.pattern
        }
    }
}
