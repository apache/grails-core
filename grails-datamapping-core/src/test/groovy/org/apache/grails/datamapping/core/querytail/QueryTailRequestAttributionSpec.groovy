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

import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch

import jakarta.servlet.RequestDispatcher

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification

/**
 * The attribution of statements to requests, which reads what a web framework resolved a request to through the
 * readers on the class path: the tests of this module find {@link QueryTailStubRequestReader}, as an application finds
 * the reader of Grails URL mapping, whose own reading of requests the specs of that module cover.
 */
class QueryTailRequestAttributionSpec extends Specification {

    static final String MATCHED_PATTERN = 'org.springframework.web.servlet.HandlerMapping.bestMatchingPattern'

    QueryTailRequestAttribution attribution = new QueryTailRequestAttribution()

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void "a statement outside a web request is attributed to its thread"() {
        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.requestId == null
        record.request == null
        record.page == "thread:${Thread.currentThread().name}"
        !record.webRequest
    }

    void "a statement in a web request is attributed to the page a reader on the class path resolved it to"() {
        given:
        bindRequest('GET', '/book/show/42', page: 'book/show', paths: ['/book/show/42': '/book/show/?'])

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.requestId == 'r1'
        record.page == 'book/show'
        record.request == 'GET /book/show/?'
        record.webRequest
    }

    void "every statement of a request shares its id and the next request gets the next id"() {
        given:
        bindRequest('GET', '/book/index')

        when:
        List<String> first = (1..3).collect { attribution.attribute(new QueryTailRecord(sql: 'select 1')).requestId }
        bindRequest('POST', '/book/save')
        String second = attribution.attribute(new QueryTailRecord(sql: 'insert into book (id) values (?)')).requestId

        then:
        first == ['r1', 'r1', 'r1']
        second == 'r2'
    }

    void "attributions of separate capture files number their requests independently"() {
        given:
        QueryTailRequestAttribution other = new QueryTailRequestAttribution('other-')
        bindRequest('GET', '/book/index')

        expect: 'each capture keeps its own id for the shared request'
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).requestId == 'r1'
        other.attribute(new QueryTailRecord(sql: 'select 1')).requestId == 'other-r1'
        attribution.attribute(new QueryTailRecord(sql: 'select 2')).requestId == 'r1'
        other.attribute(new QueryTailRecord(sql: 'select 2')).requestId == 'other-r1'

        when: 'only the other capture sees the next request'
        bindRequest('GET', '/book/index')

        then:
        other.attribute(new QueryTailRecord(sql: 'select 3')).requestId == 'other-r2'
    }

    void "a request that no reader resolved has no page"() {
        given:
        bindRequest('GET', '/health')

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.page == null
        record.requestId == 'r1'
        record.request == 'GET /?'
    }

    void "a statement run before a reader resolved the request records only the shape of the path"() {
        given: 'a filter queries first; nothing tells the token from the literal segments yet'
        bindRequest('GET', '/password/reset/k9fQ2x')

        when:
        QueryTailRecord before = attribution.attribute(new QueryTailRecord(sql: 'select 1'))
        bindRequest('GET', '/password/reset/k9fQ2x', page: 'password/reset', paths: ['/password/reset/k9fQ2x': '/password/reset/?'])
        QueryTailRecord after = attribution.attribute(new QueryTailRecord(sql: 'select 2'))

        then:
        before.request == 'GET /?/?/?'
        after.request == 'GET /password/reset/?'
    }

    void "a controller parameter the client sends is no sign that the request was resolved"() {
        given: 'a filter queries before the mapping, in a request whose query string names a controller'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/password/reset/k9fQ2x')
        request.setParameter('controller', 'x')
        RequestContextHolder.setRequestAttributes(new MappedRequestAttributes(request, [controller: 'x']))

        expect:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).request == 'GET /?/?/?'
    }

    void "a request no reader resolved is recorded by the pattern a Spring MVC handler matched, else by its shape: #served"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/reset/k9fQ2xSecret')
        if (pattern) {
            request.setAttribute(MATCHED_PATTERN, pattern)
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.page == null
        record.request == recorded
        !record.toLine().contains('k9fQ2xSecret')

        where:
        served                           | pattern          || recorded
        'a Spring MVC handler serves it' | '/reset/{token}' || 'GET /reset/?'
        'nothing serves it'              | null             || 'GET /?/?'
    }

    void "a request that is not a servlet request is attributed to its page but has no request line"() {
        given: 'request attributes of another kind, as a framework other than Spring MVC binds'
        Map<String, Object> store = [(QueryTailStubRequestReader.PAGE): 'book/list']
        RequestContextHolder.setRequestAttributes([
                getAttribute: { String name, int scope -> store[name] },
                setAttribute: { String name, Object value, int scope -> store[name] = value },
        ] as RequestAttributes)

        when:
        List<QueryTailRecord> records = (1..2).collect { attribution.attribute(new QueryTailRecord(sql: 'select 1')) }

        then: 'the id is kept on the request, as it is on a servlet request'
        records*.requestId == ['r1', 'r1']
        records*.page == ['book/list', 'book/list']
        records*.request == [null, null]
    }

    void "a reader that fails is passed over, and the statement is recorded as one no reader resolved"() {
        given:
        bindRequest('GET', '/book/show/42', page: QueryTailStubRequestReader.FAILING)

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.requestId == 'r1'
        record.page == null
        record.request == 'GET /?/?/?'
    }

    void "without Spring Web on the classpath every statement is attributed to its thread"() {
        given: 'a request bound on the thread, which the attribution cannot see without Spring Web'
        bindRequest('GET', '/book/show/42', page: 'book/show')

        when:
        QueryTailRecord record = attributionWithout('org.springframework.web.').attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.requestId == null
        record.request == null
        record.page == "thread:${Thread.currentThread().name}"
        !record.webRequest
    }

    void "without the servlet API on the classpath a statement is attributed to its request, which has no request line"() {
        given:
        bindRequest('GET', '/book/show/42', page: 'book/show')

        when:
        QueryTailRecord record = attributionWithout('jakarta.servlet.').attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.requestId == 'r1'
        record.page == 'book/show'
        record.request == null
    }

    void "a path the reader that resolved the request does not read records only its shape: #uri"() {
        given: 'a request a reader resolved, whose path it does not read, as one kept from before a forward'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', uri)
        request.contextPath = contextPath
        QueryTailStubRequestReader.resolve(request, 'book/show', paths)
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.page == 'book/show'
        record.request == recorded
        !record.toLine().contains('secret')

        where:
        uri                               | contextPath | paths                       || recorded
        '/password/reset/k9fQ2x-secret'   | ''          | [:]                         || 'GET /?/?/?'
        '/book/index;jsessionid=secret12' | ''          | [:]                         || 'GET /?/?'
        '/'                               | ''          | [:]                         || 'GET /'
        'secret'                          | ''          | ['secret': '/?']            || 'GET ?'
        '/shop/book/show/secret'          | '/shop'     | [:]                         || 'GET /?/?/?/?'
    }

    void "a path the reader reads is recorded below the context path of the application"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/shop/book/show/42')
        request.contextPath = '/shop'
        QueryTailStubRequestReader.resolve(request, 'book/show', ['/book/show/42': '/book/show/?'])
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        expect:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).request == 'GET /shop/book/show/?'
    }

    void "a form posted to a request is left for the application to read, #stage"() {
        given: 'a request whose parameters, read as a servlet container reads them, use up the form posted in its body'
        FormPostRequest request = new FormPostRequest('/search/grails', 'payload=example-value')
        request.queryString = 'q=grails'
        if (resolved) {
            QueryTailStubRequestReader.resolve(request, 'search/index', ['/search/grails': '/search/?'])
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then: 'the body is all there'
        new String(request.inputStream.readAllBytes(), StandardCharsets.UTF_8) == 'payload=example-value'
        record.request == recorded

        where:
        stage                                     | resolved || recorded
        'before a reader has resolved it'         | false    || 'POST /?/?'
        'once a reader has resolved it'           | true     || 'POST /search/?'
    }

    void "a request included in another is recorded as the URI it was included by, not the path including it"() {
        given: 'a page whose view includes another action, as <g:include> does'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', contextPath + '/blog/my-secret-slug')
        request.contextPath = contextPath
        Map<String, String> paths = ['/blog/my-secret-slug': '/blog/?', '/widgets/recent/5': '/widgets/recent/?']
        QueryTailStubRequestReader.resolve(request, 'post/show', paths)
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when: 'the page queries, then the included action, then the page again'
        QueryTailRecord page = attribution.attribute(new QueryTailRecord(sql: 'select 1'))
        request.setAttribute(RequestDispatcher.INCLUDE_REQUEST_URI, contextPath + '/widgets/recent/5')
        request.setAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH, contextPath)
        QueryTailStubRequestReader.resolve(request, 'widget/recent', paths)
        QueryTailRecord include = attribution.attribute(new QueryTailRecord(sql: 'select 2'))
        request.removeAttribute(RequestDispatcher.INCLUDE_REQUEST_URI)
        request.removeAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH)
        QueryTailStubRequestReader.resolve(request, 'post/show', paths)
        QueryTailRecord after = attribution.attribute(new QueryTailRecord(sql: 'select 3'))

        then: 'the include is its own page and line, in the same request, and the slug of the page is in neither'
        [page, include, after]*.page == ['post/show', 'widget/recent', 'post/show']
        [page, include, after]*.request == ["GET ${contextPath}/blog/?", "GET ${contextPath}/widgets/recent/?",
                                            "GET ${contextPath}/blog/?"]*.toString()
        [page, include, after]*.requestId.unique() == ['r1']
        [page, include, after].every { !it.toLine().contains('secret') }

        where:
        contextPath << ['', '/shop']
    }

    void "a request a Spring MVC handler serves is recorded as the pattern its handler mapping matched"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/shop/api/books/42/reviews/k9fQ2x')
        request.contextPath = '/shop'
        request.setAttribute(MATCHED_PATTERN, '/api/books/{id}/reviews/{token}')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then: 'the page is the request, which tells one endpoint from another'
        record.request == 'GET /shop/api/books/?/reviews/?'
        record.page == null
        QueryTailRecord.parse(record.toLine()) != null
    }

    void "a request is described once, until it moves on or a reader resolves it to another target"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/book/show/42')
        Map<String, String> paths = ['/book/show/42': '/book/show/?', '/book/edit/42': '/book/edit/?']
        QueryTailStubRequestReader.resolve(request, 'book/show', paths)
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when: 'the request runs three statements'
        List<String> lines = (1..3).collect { attribution.attribute(new QueryTailRecord(sql: 'select 1')).request }
        StubTarget target = (StubTarget) request.getAttribute(QueryTailStubRequestReader.name + '.TARGET')

        then: 'its path was read once'
        lines == ['GET /book/show/?'] * 3
        target.reads == 1

        when: 'it is forwarded to another action, which the reader resolves it to'
        request.requestURI = '/book/edit/42'
        QueryTailStubRequestReader.resolve(request, 'book/edit', paths)

        then:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).request == 'GET /book/edit/?'
        ((StubTarget) request.getAttribute(QueryTailStubRequestReader.name + '.TARGET')).reads == 1
    }

    void "the threads of one request number it once, whatever request attributes each sees it through"() {
        given: 'a request slow to answer whether it has an id, and two threads that see it through attributes of their own'
        SlowToAnswerRequest request = new SlowToAnswerRequest('/book/index')
        CountDownLatch start = new CountDownLatch(1)
        List<String> ids = [].asSynchronized()
        List<Thread> threads = (1..2).collect {
            Thread.start {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))
                start.await()
                ids << attribution.attribute(new QueryTailRecord(sql: 'select 1')).requestId
            }
        }

        when:
        start.countDown()
        threads*.join(10000)

        then:
        ids.size() == 2
        ids[0] == ids[1]
    }

    void "a statement run once its request has completed is attributed to its thread"() {
        given: 'request attributes a task kept after the request completed'
        ServletRequestAttributes attributes = new ServletRequestAttributes(new MockHttpServletRequest('GET', '/book/index'))
        attributes.requestCompleted()
        RequestContextHolder.setRequestAttributes(attributes)

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.requestId == null
        record.request == null
        record.page == "thread:${Thread.currentThread().name}"
    }

    void "the requests of a capture joined from another JVM are numbered with its prefix"() {
        when:
        bindRequest('GET', '/book/index')

        then:
        new QueryTailRequestAttribution('4711-').attribute(new QueryTailRecord(sql: 'select 1')).requestId == '4711-r1'

        when: 'another request'
        bindRequest('GET', '/book/index')

        then:
        new QueryTailRequestAttribution(null).attribute(new QueryTailRecord(sql: 'select 1')).requestId == 'r1'
    }

    void "the statements a request runs while it runs an operation count under the operation, after its page"() {
        given:
        bindRequest('POST', '/graphql', page: 'graphql/index', paths: ['/graphql': '/graphql'])

        when:
        QueryTailRecord before = attribution.attribute(new QueryTailRecord(sql: 'select 1'))
        Runnable end = QueryTailRequestAttribution.beginOperation('query BookList')
        QueryTailRecord during = attribution.attribute(new QueryTailRecord(sql: 'select 2'))
        end.run()
        QueryTailRecord after = attribution.attribute(new QueryTailRecord(sql: 'select 3'))

        then:
        before.page == 'graphql/index'
        during.page == 'graphql/index (query BookList)'
        during.request == 'POST /graphql'
        during.requestId == before.requestId
        after.page == 'graphql/index'
        after.requestId == before.requestId
    }

    void "an operation of a request no reader resolved is shown after its request line"() {
        given:
        bindRequest('POST', '/api/graphql')
        Runnable end = QueryTailRequestAttribution.beginOperation('query BookList')

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.page == 'POST /?/? (query BookList)'
        record.request == 'POST /?/?'

        cleanup:
        end?.run()
    }

    void "an operation of a request that has no request line and no page is the page"() {
        given: 'an attribution without the servlet API, and its own operations, which it reads the request with'
        bindRequest('POST', '/api/graphql')
        Object withoutServlet = attributionWithout('jakarta.servlet.')
        Runnable end = (Runnable) withoutServlet.class.getMethod('beginOperation', String).invoke(null, 'query BookList')

        when:
        QueryTailRecord record = withoutServlet.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.page == '(query BookList)'
        record.request == null

        cleanup:
        end?.run()
    }

    void "an operation begun while another runs is the one the request runs until it ends, when #first ends first"() {
        given:
        bindRequest('POST', '/graphql', page: 'graphql/index')
        Map<String, Runnable> ends = [outer: QueryTailRequestAttribution.beginOperation('query Outer')]
        ends.inner = QueryTailRequestAttribution.beginOperation('query Inner')

        when:
        String bothRunning = attribution.attribute(new QueryTailRecord(sql: 'select 1')).page
        ends[first].run()
        String oneRunning = attribution.attribute(new QueryTailRecord(sql: 'select 2')).page
        ends.values()*.run()
        String noneRunning = attribution.attribute(new QueryTailRecord(sql: 'select 3')).page

        then:
        bothRunning == 'graphql/index (query Inner)'
        oneRunning == afterFirst
        noneRunning == 'graphql/index'

        where:
        first   || afterFirst
        'inner' || 'graphql/index (query Outer)'
        'outer' || 'graphql/index (query Inner)'
    }

    void "an operation ends on whichever thread ends it, and ending it again ends no other"() {
        given:
        bindRequest('POST', '/graphql', page: 'graphql/index')
        Runnable first = QueryTailRequestAttribution.beginOperation('query First')

        when: 'the operation ends on another thread, as one whose data a fetcher returns later ends'
        Thread ending = Thread.start { first.run() }
        ending.join()
        String ended = attribution.attribute(new QueryTailRecord(sql: 'select 1')).page
        Runnable second = QueryTailRequestAttribution.beginOperation('query Second')
        first.run()
        String afterEndingAgain = attribution.attribute(new QueryTailRecord(sql: 'select 2')).page

        then:
        ended == 'graphql/index'
        afterEndingAgain == 'graphql/index (query Second)'

        cleanup:
        second?.run()
    }

    void "an operation begun outside a request, or once it has completed, ends without a trace: #where"() {
        given:
        bind()

        when:
        Runnable end = QueryTailRequestAttribution.beginOperation('query BookList')
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))
        end.run()

        then:
        record.page == "thread:${Thread.currentThread().name}"
        noExceptionThrown()

        where:
        where                       | bind
        'without a request'         | { -> }
        'once the request completed' | { ->
            ServletRequestAttributes attributes = new ServletRequestAttributes(new MockHttpServletRequest('POST', '/graphql'))
            attributes.requestCompleted()
            RequestContextHolder.setRequestAttributes(attributes)
        }
    }

    void "an operation that outlives its request ends without a trace"() {
        given:
        ServletRequestAttributes attributes = new ServletRequestAttributes(new MockHttpServletRequest('POST', '/graphql'))
        RequestContextHolder.setRequestAttributes(attributes)
        Runnable end = QueryTailRequestAttribution.beginOperation('query BookList')

        when:
        attributes.requestCompleted()
        end.run()

        then:
        noExceptionThrown()
    }

    void "without Spring Web on the classpath an operation begins nothing"() {
        given:
        bindRequest('POST', '/graphql', page: 'graphql/index')
        Object withoutSpringWeb = attributionWithout('org.springframework.web.')

        when:
        Runnable end = (Runnable) withoutSpringWeb.class.getMethod('beginOperation', String).invoke(null, 'query BookList')
        end.run()

        then:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).page == 'graphql/index'
    }

    void "an operation that names nothing begins nothing"() {
        given:
        bindRequest('POST', '/graphql', page: 'graphql/index')

        when:
        Runnable end = QueryTailRequestAttribution.beginOperation(null)

        then:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).page == 'graphql/index'

        cleanup:
        end?.run()
    }

    /**
     * Binds a request, which the reader on the class path resolves to the page given, if any, and whose paths it reads
     * as the paths given say.
     */
    private static void bindRequest(Map<String, Object> resolved = [:], String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri)
        if (resolved.page) {
            QueryTailStubRequestReader.resolve(request, resolved.page as String, (resolved.paths ?: [:]) as Map<String, String>)
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))
    }

    /**
     * An attribution loaded as an application without a library on its classpath loads it: by a class loader
     * that cannot see the library's package. The records it fills in are the usual class, loaded once.
     */
    private static Object attributionWithout(String hiddenPackage) {
        ClassLoader loader = new HidingClassLoader(QueryTailRequestAttribution.classLoader, hiddenPackage)
        loader.loadClass(QueryTailRequestAttribution.name).getConstructor().newInstance()
    }

    /**
     * Defines the attribution class and its nested classes itself, so that their lookups of the hidden
     * package come to this loader; every other class comes from the parent.
     */
    private static class HidingClassLoader extends ClassLoader {

        private final String hiddenPackage

        HidingClassLoader(ClassLoader parent, String hiddenPackage) {
            super(parent)
            this.hiddenPackage = hiddenPackage
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(hiddenPackage)) {
                throw new ClassNotFoundException(name)
            }
            if (name != QueryTailRequestAttribution.name && !name.startsWith(QueryTailRequestAttribution.name + '$')) {
                return super.loadClass(name, resolve)
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> defined = findLoadedClass(name)
                if (defined == null) {
                    byte[] bytes = parent.getResourceAsStream(name.replace('.', '/') + '.class').bytes
                    // the usual class's protection domain, so that the class has a code location: coverage
                    // tooling instruments a class without one only when asked to
                    defined = defineClass(name, bytes, 0, bytes.length, QueryTailRequestAttribution.protectionDomain)
                }
                if (resolve) {
                    resolveClass(defined)
                }
                return defined
            }
        }
    }
}

class MappedRequestAttributes extends ServletRequestAttributes {

    private final Map<String, String> parameters

    MappedRequestAttributes(MockHttpServletRequest request, Map<String, String> parameters) {
        super(request)
        this.parameters = parameters
    }

    Map<String, String> getParams() {
        parameters
    }
}

/**
 * A form posted to a request, whose parameters, as a servlet container reads them, are parsed from the
 * body and use it up, so that the application can no longer read it.
 */
class FormPostRequest extends MockHttpServletRequest {

    FormPostRequest(String uri, String form) {
        super('POST', uri)
        contentType = 'application/x-www-form-urlencoded'
        content = form.getBytes(StandardCharsets.UTF_8)
    }

    @Override
    String getParameter(String name) {
        parseForm()
        super.getParameter(name)
    }

    @Override
    Map<String, String[]> getParameterMap() {
        parseForm()
        super.getParameterMap()
    }

    @Override
    Enumeration<String> getParameterNames() {
        parseForm()
        super.getParameterNames()
    }

    @Override
    String[] getParameterValues(String name) {
        parseForm()
        super.getParameterValues(name)
    }

    private void parseForm() {
        content = new byte[0]
    }
}

/**
 * A request that takes its time to answer whether it holds an attribute, so that two threads asking at
 * once both ask before either sets it, unless they take turns.
 */
class SlowToAnswerRequest extends MockHttpServletRequest {

    SlowToAnswerRequest(String uri) {
        super('GET', uri)
    }

    @Override
    Object getAttribute(String name) {
        Object value = super.getAttribute(name)
        if (name.startsWith(QueryTailRequestAttribution.REQUEST_ID_ATTRIBUTE)) {
            Thread.sleep(200)
        }
        value
    }
}
