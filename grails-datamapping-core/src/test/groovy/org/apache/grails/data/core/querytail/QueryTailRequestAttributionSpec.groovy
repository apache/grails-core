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

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification

class QueryTailRequestAttributionSpec extends Specification {

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

    void "a statement in a web request is attributed to the controller and action serving it"() {
        given:
        bindRequest('GET', '/book/show/42', controller: 'book', action: 'show')

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
        bindRequest('GET', '/book/index', controller: 'book', action: 'index')

        when:
        List<String> first = (1..3).collect { attribution.attribute(new QueryTailRecord(sql: 'select 1')).requestId }
        bindRequest('POST', '/book/save', controller: 'book', action: 'save')
        String second = attribution.attribute(new QueryTailRecord(sql: 'insert into book (id) values (?)')).requestId

        then:
        first == ['r1', 'r1', 'r1']
        second == 'r2'
    }

    void "the page carries the controller namespace and copes with a missing action"() {
        given:
        bindRequest('GET', '/admin/book', controller: 'book', namespace: 'admin')

        expect:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).page == 'admin:book'
    }

    void "a request that has not reached a controller yet has no page"() {
        given:
        bindRequest('GET', '/health')

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.page == null
        record.requestId == 'r1'
        record.request == 'GET /health'
    }

    void "a request that is not a servlet request is attributed to its page but has no request line"() {
        given: 'request attributes of another kind, as a framework other than Spring MVC binds'
        Map<String, Object> store = [
                (QueryTailRequestAttribution.CONTROLLER_NAME_ATTRIBUTE): 'book',
                (QueryTailRequestAttribution.ACTION_NAME_ATTRIBUTE): 'list',
        ]
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

    void "without Spring Web on the classpath every statement is attributed to its thread"() {
        given: 'a request bound on the thread, which the attribution cannot see without Spring Web'
        bindRequest('GET', '/book/show/42', controller: 'book', action: 'show')

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
        bindRequest('GET', '/book/show/42', controller: 'book', action: 'show')

        when:
        QueryTailRecord record = attributionWithout('jakarta.servlet.').attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.requestId == 'r1'
        record.page == 'book/show'
        record.request == null
    }

    void "the values in the path of a request are not recorded: #uri"() {
        given:
        bindGrailsRequest('GET', uri, parameters)

        expect:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).request == recorded

        where:
        uri                                                                        | parameters                                                          || recorded
        '/password/reset/k9fQ2x'                                                   | [controller: 'password', action: 'reset', token: 'k9fQ2x']          || 'GET /password/reset/?'
        '/api/k9fQ2x/orders'                                                       | [controller: 'order', action: 'index', apiKey: 'k9fQ2x']            || 'GET /api/?/orders'
        '/files/reports/2026/q3.pdf'                                               | [controller: 'file', action: 'serve', path: 'reports/2026/q3.pdf']  || 'GET /files/?/?/?'
        '/book/show/42.json'                                                       | [controller: 'book', action: 'show', id: '42', format: 'json']      || 'GET /book/show/?'
        '/book/show/42'                                                            | [:]                                                                 || 'GET /book/show/?'
        '/user/3f9c1e2a-5b7d-4c8e-9f0a-1b2c3d4e5f60/orders'                        | [:]                                                                 || 'GET /user/?/orders'
        '/confirm/9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08' | [:]                                                                 || 'GET /confirm/?'
        '/user/j%C3%BCrgen/orders'                                                 | [controller: 'order', action: 'index', user: 'jürgen']              || 'GET /user/?/orders'
        '/files/reports%2F2026%2Fq3.pdf'                                           | [controller: 'file', action: 'serve', path: 'reports/2026/q3.pdf']  || 'GET /files/?'
        '/files/reports/2026%2Fq3.pdf'                                             | [controller: 'file', action: 'serve', path: 'reports/2026/q3.pdf']  || 'GET /files/?/?'
        '/v2/book/index'                                                           | [controller: 'book', action: 'index']                               || 'GET /v2/book/index'
        '/book/index'                                                              | [controller: 'book', action: 'index', max: '10']                    || 'GET /book/index'
    }

    void "a value of the query string that is in the path is not recorded either"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/search/grails')
        request.setParameter('q', 'grails')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        expect:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).request == 'GET /search/?'
    }

    void "a value the client percent-encoded in the path is not recorded either"() {
        given: 'the servlet container decodes the parameters but leaves the request URI as sent'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/search/secret%40example.com')
        request.setParameter('q', 'secret@example.com')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        expect:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).request == 'GET /search/?'
    }

    void "a path segment that is not valid percent-encoding is matched as sent"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/search/100%25%2')
        request.setParameter('q', '100%25%2')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        expect:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).request == 'GET /search/?'
    }

    void "the requests of a capture joined from another JVM are numbered with its prefix"() {
        when:
        bindRequest('GET', '/book/index', controller: 'book', action: 'index')

        then:
        new QueryTailRequestAttribution('4711-').attribute(new QueryTailRecord(sql: 'select 1')).requestId == '4711-r1'

        when: 'another request'
        bindRequest('GET', '/book/index', controller: 'book', action: 'index')

        then:
        new QueryTailRequestAttribution(null).attribute(new QueryTailRecord(sql: 'select 1')).requestId == 'r1'
    }

    /** A request as Grails binds it: its attributes answer {@code getParams()} with the parameters the URL mapping took. */
    private static void bindGrailsRequest(String method, String uri, Map<String, String> parameters) {
        RequestContextHolder.setRequestAttributes(new MappedRequestAttributes(new MockHttpServletRequest(method, uri), parameters))
    }

    private static void bindRequest(Map<String, String> attributes = [:], String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri)
        if (attributes.controller) {
            request.setAttribute(QueryTailRequestAttribution.CONTROLLER_NAME_ATTRIBUTE, attributes.controller)
        }
        if (attributes.action) {
            request.setAttribute(QueryTailRequestAttribution.ACTION_NAME_ATTRIBUTE, attributes.action)
        }
        if (attributes.namespace) {
            request.setAttribute(QueryTailRequestAttribution.CONTROLLER_NAMESPACE_ATTRIBUTE, attributes.namespace)
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
