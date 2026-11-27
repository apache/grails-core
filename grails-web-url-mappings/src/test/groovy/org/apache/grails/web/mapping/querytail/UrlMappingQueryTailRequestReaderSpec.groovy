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
package org.apache.grails.web.mapping.querytail

import java.nio.charset.StandardCharsets

import groovy.transform.AutoImplement

import jakarta.servlet.RequestDispatcher

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification

import grails.core.GrailsControllerClass
import grails.web.mapping.UrlMappingData
import grails.web.mapping.UrlMappingInfo
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datamapping.core.querytail.QueryTailRequestAttribution
import org.apache.grails.datastore.core.querytail.QueryTailRequestReader
import org.grails.core.DefaultGrailsControllerClass
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Attributes statements to the controller and action Grails resolved their requests to, through the query-tail
 * attribution, which finds the reader of URL mapping on the class path as an application finds it.
 */
class UrlMappingQueryTailRequestReaderSpec extends Specification {

    static final String MATCHED_PATTERN = 'org.springframework.web.servlet.HandlerMapping.bestMatchingPattern'

    QueryTailRequestAttribution attribution = new QueryTailRequestAttribution()

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void "the reader is found on the class path, as the query-tail capture of an application finds it"() {
        expect:
        ServiceLoader.load(QueryTailRequestReader).any { it instanceof UrlMappingQueryTailRequestReader }
    }

    void "a statement in a web request is attributed to the controller and action serving it"() {
        given:
        bindRequest('GET', '/book/show/42', controller: 'book', action: 'show', tokens: ['book', 'show', '(*)'])

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.requestId == 'r1'
        record.page == 'book/show'
        record.request == 'GET /book/show/?'
        record.webRequest
    }

    void "the page carries the controller namespace, and the default action of a request that names none yet"() {
        given:
        bindRequest('GET', '/admin/book', controller: 'book', namespace: 'admin')

        expect:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).page == 'admin:book/index'
    }

    void "a statement run before the URL mapping has chosen a controller records only the shape of the path"() {
        given: 'a filter queries first; nothing tells the token from the literal segments yet'
        bindRequest('GET', '/password/reset/k9fQ2x')

        when:
        QueryTailRecord before = attribution.attribute(new QueryTailRecord(sql: 'select 1'))
        bindRequest('GET', '/password/reset/k9fQ2x', controller: 'password', action: 'reset', tokens: ['password', 'reset', '(*)'])
        QueryTailRecord after = attribution.attribute(new QueryTailRecord(sql: 'select 2'))

        then:
        before.request == 'GET /?/?/?'
        after.request == 'GET /password/reset/?'
    }

    void "the names a URL mapping that resolved no controller leaves on a request give it no page: #served"() {
        given: 'the default mapping named a controller and action from the path, which no controller has'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/reset/k9fQ2xSecret')
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'reset')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'k9fQ2xSecret')
        if (pattern) {
            request.setAttribute(MATCHED_PATTERN, pattern)
        }
        if (resolved) {
            request.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, new QueryTailControllerClass(resolved))
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.page == null
        record.request == recorded
        !record.toLine().contains('k9fQ2xSecret')

        where:
        served                                              | pattern          | resolved || recorded
        'a Spring MVC handler serves it'                    | '/reset/{token}' | null     || 'GET /reset/?'
        'nothing serves it'                                 | null             | null     || 'GET /?/?'
        'a controller forwarded it to a Spring MVC handler' | '/reset/{token}' | 'book'   || 'GET /reset/?'
    }

    void "a request whose URL mapping Grails has let go of keeps the page of the controller it resolved: #requested"() {
        given: 'a request that has completed, which leaves the controller class Grails resolved but not the mapping'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', "/${requested}/show/42")
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, requested)
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'show')
        request.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, new QueryTailControllerClass('bookStore', ['show']))
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then: 'the path, which no mapping reads any more, is withheld'
        record.page == "${requested}/show"
        record.request == 'GET /?/?/?'

        where: 'the controller as each URL converter spells it'
        requested << ['bookStore', 'book-store']
    }

    void "a request that is not a servlet request is attributed to its page but has no request line"() {
        given: 'request attributes of another kind, as a framework other than Spring MVC binds'
        Map<String, Object> store = [
                (GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE): 'book',
                (GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE): 'list',
                (GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS): new QueryTailControllerClass('book', ['list']),
        ]
        RequestContextHolder.setRequestAttributes([
                getAttribute: { String name, int scope -> store[name] },
                setAttribute: { String name, Object value, int scope -> store[name] = value },
        ] as RequestAttributes)

        when:
        List<QueryTailRecord> records = (1..2).collect { attribution.attribute(new QueryTailRecord(sql: 'select 1')) }

        then:
        records*.requestId == ['r1', 'r1']
        records*.page == ['book/list', 'book/list']
        records*.request == [null, null]
    }

    void "a path that cannot be read against the URL mapping Grails matched records only its shape: #uri"() {
        given: 'a request Grails resolved to a controller, whose path its mapping does not spell, as one kept from before a forward'
        bindRequest('GET', uri, controller: 'book', action: 'show', tokens: tokens)

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.request == recorded
        !record.toLine().contains('secret')

        where:
        uri                                                 | tokens                    || recorded
        '/password/reset/k9fQ2x-secret'                     | null                      || 'GET /?/?/?'
        '/book/show/42.json'                                | null                      || 'GET /?/?/?'
        '/images/thumb-customer-secret/private-secret.jpg'  | null                      || 'GET /?/?/?'
        '/search/secret%40example.com'                      | null                      || 'GET /?/?'
        '/book/index;jsessionid=abc123secretXYZ'            | null                      || 'GET /?/?'
        '/book/show/secret-word'                            | ['author', '(*)']         || 'GET /?/?/?'
        '/blog/my-secret-slug'                              | ['widgets', 'recent']     || 'GET /?/?'
        '/book/show/42/secret'                              | ['book', 'show', '(*)']   || 'GET /?/?/?/?'
        '/'                                                 | ['book', 'show']          || 'GET /'
        'secret'                                            | ['(*)']                   || 'GET ?'
    }

    void "a form posted to a request is left for the application to read once the URL mapping has chosen a controller"() {
        given: 'a request whose parameters, read as a servlet container reads them, use up the form posted in its body'
        FormPostRequest request = new FormPostRequest('/search/grails', 'payload=example-value')
        request.queryString = 'q=grails'
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'search')
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(['search', '(*)'], controllerClass('search')))
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then: 'the body is all there'
        new String(request.inputStream.readAllBytes(), StandardCharsets.UTF_8) == 'payload=example-value'
        record.request == 'POST /search/?'
    }

    void "a path read against the URL mapping Grails matched keeps what the mapping spells out: #uri"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', uri)
        request.contextPath = contextPath
        request.queryString = query
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'book')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'show')
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(tokens, controllerClass('book', 'show')))
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        expect:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).request == recorded

        where:
        uri                                 | contextPath | query    | tokens                           || recorded
        '/book/show/42'                     | ''          | null     | ['book', 'show', '(*)']          || 'GET /book/show/?'
        '/book/show/plain-word'             | ''          | null     | ['(*)', '(*)?', '(*)?']          || 'GET /book/show/?'
        '/book/show/42'                     | ''          | 'q=book' | ['(*)', '(*)?', '(*)?']          || 'GET /book/show/?'
        '/book'                             | ''          | null     | ['(*)', '(*)?', '(*)?']          || 'GET /book'
        '/book/show/'                       | ''          | null     | ['(*)', '(*)?', '(*)?']          || 'GET /book/show/'
        '/book/show.json'                   | ''          | null     | ['book', 'show']                 || 'GET /book/show.?'
        '/book/show.eyJhbGciOi'             | ''          | null     | ['(*)', '(*)?', '(*)?']          || 'GET /book/show.?'
        '/book.eyJhbGciOi'                  | ''          | null     | ['(*)', '(*)?', '(*)?']          || 'GET /book.?'
        '/book/show.eyJhbG;jsessionid=1'    | ''          | null     | ['book', 'show']                 || 'GET /book/show.?;?'
        '/reset/k9fQ2x_confirm'             | ''          | null     | ['reset', '(*)_confirm']         || 'GET /reset/?'
        '/images/album/private/secret.jpg'  | ''          | null     | ['images', '(**).jpg']           || 'GET /images/?/?/?'
        '/book/show/42;jsessionid=A1B2'     | ''          | null     | ['book', 'show', '(*)']          || 'GET /book/show/?;?'
        '/shop/book/show/42'                | '/shop'     | null     | ['book', 'show', '(*)']          || 'GET /shop/book/show/?'
        '/book/show/100%25%2'               | ''          | null     | ['book', 'show', '(*)']          || 'GET /book/show/?'
        '/book/show/k9fQ2x_confirm'         | ''          | null     | ['(*)', '(*)?', '(*)?']          || 'GET /book/show/?'
        '/book/%73how/42'                   | ''          | null     | ['book', 'show', '(*)']          || 'GET /book/%73how/?'
        '/'                                 | ''          | null     | ['']                             || 'GET /'
    }

    void "a value carried in a path the URL mapping matched never reaches the record: #uri"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', uri)
        request.queryString = query
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, controller)
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, action)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(tokens, controllerClass(controller, action)))
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        String line = attribution.attribute(new QueryTailRecord(sql: 'select 1')).toLine()

        then:
        QueryTailRecord.parse(line).request == recorded
        !line.contains('secret')
        !line.contains('jsessionid')

        where: 'one the client percent-encoded, a session id the container rewrote the path with, and multi-segment values'
        uri                                                  | query                    | controller | action  | tokens                || recorded
        '/search/secret%40example.com'                       | 'q=secret%40example.com' | 'search'   | 'index' | ['search', '(*)']     || 'GET /search/?'
        '/book/index;jsessionid=abc123secretXYZ'             | null                     | 'book'     | 'index' | ['book', 'index']     || 'GET /book/index;?'
        '/images/customer-secret/private-secret.jpg'         | null                     | 'image'    | 'show'  | ['images', '(**).jpg'] || 'GET /images/?/?'
        '/images/thumb-customer-secret/private-secret.jpg'   | null                     | 'image'    | 'show'  | ['images', '(**).jpg'] || 'GET /images/?/?'
        '/images/thumb-%63ustomer-secret/private-secret.jpg' | null                     | 'image'    | 'show'  | ['images', '(**).jpg'] || 'GET /images/?/?'
        '/images/thumb-customer-secret%2Fprivate-secret.jpg' | null                     | 'image'    | 'show'  | ['images', '(**).jpg'] || 'GET /images/?'
    }

    void "every statement of a request to a controller's default action is on one page, its before interceptor's too"() {
        given: 'a request to /queryTailShelf, whose mapping names no action, as a before interceptor sees it'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/queryTailShelf')
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'queryTailShelf')
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(['(*)', '(*)?', '(*)?'],
                new DefaultGrailsControllerClass(QueryTailShelfController)))
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when: 'the interceptor queries, Grails sets the default action as it calls it, and the action queries'
        QueryTailRecord interceptor = attribution.attribute(new QueryTailRecord(sql: 'select 1'))
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'browse')
        QueryTailRecord action = attribution.attribute(new QueryTailRecord(sql: 'select 2'))

        then: 'one request, on one page'
        [interceptor, action]*.page == ['queryTailShelf/browse'] * 2
        interceptor.requestId == action.requestId
        [interceptor, action]*.request == ['GET /queryTailShelf'] * 2
    }

    void "a mapping that matched another controller than the request names gives it no page"() {
        given: 'the mapping of another controller, as one kept from before a forward could be'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/author')
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'author')
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(['(*)', '(*)?', '(*)?'],
                new DefaultGrailsControllerClass(QueryTailShelfController)))
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.page == null
        record.request == 'GET /?'
    }

    void "names that are not those of the controller class Grails resolved the request to give it no page: #named"() {
        given: 'names a mapping that resolved no controller read from the path, beside a controller class Grails resolved or looked up'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/reset/k9fQ2xSecret')
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, controller)
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, action)
        if (namespace) {
            request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, namespace)
        }
        if (mapped) {
            request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(['(*)', '(*)?'], mapped))
        }
        if (resolved) {
            request.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, resolved)
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        QueryTailRecord record = attribution.attribute(new QueryTailRecord(sql: 'select 1'))

        then:
        record.page == null
        record.request == 'GET /?/?'
        !record.toLine().contains('k9fQ2xSecret')

        where:
        named                                                       | controller | action         | namespace      | mapped                                | resolved
        'a status mapped to another controller serves it'           | 'reset'    | 'k9fQ2xSecret' | null           | controllerClass('errors', 'notFound') | null
        'a controller class looked up by its name lacks the action' | 'book'     | 'k9fQ2xSecret' | null           | null                                  | controllerClass('book', 'show')
        'the matched controller lacks the action'                   | 'book'     | 'k9fQ2xSecret' | null           | controllerClass('book', 'show')       | null
        'the controller is declared in no namespace'                | 'book'     | 'show'         | 'k9fQ2xSecret' | controllerClass('book', 'show')       | null
        'the controller is declared in another namespace'           | 'book'     | 'show'         | 'k9fQ2xSecret' | null                                  | controllerClass('book', 'show', 'admin')
    }

    void "a controller, action and namespace a URL converter spells otherwise are those of the controller class: #requested"() {
        given: 'a request to the default action of a namespaced controller, whose mapping names no action'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', "/${space}/${requested}")
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, requested)
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, space)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(['(*)', '(*)'],
                new QueryTailControllerClass('bookStore', [action], 'adminTools', action)))
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when: 'a before interceptor queries, Grails names the default action as it calls it, and the action queries'
        QueryTailRecord interceptor = attribution.attribute(new QueryTailRecord(sql: 'select 1'))
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, action)
        QueryTailRecord called = attribution.attribute(new QueryTailRecord(sql: 'select 2'))

        then: 'one page'
        [interceptor, called]*.page == ["${space}:${requested}/${action}".toString()] * 2

        where: 'the actions of a controller class, as a request names them, are spelled by the URL converter too'
        requested    | space         | action
        'bookStore'  | 'adminTools'  | 'listBooks'
        'book-store' | 'admin-tools' | 'list-books'
    }

    void "a request is resolved to its controller once, until Grails changes what it was resolved from"() {
        given:
        QueryTailControllerClass controllerClass = controllerClass('book', 'show')
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/book')
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'book')
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(['(*)'], controllerClass))
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when: 'a request runs three statements'
        List<String> pages = (1..3).collect { attribution.attribute(new QueryTailRecord(sql: 'select 1')).page }

        then: 'its controller class was asked its name once'
        pages == ['book/index'] * 3
        controllerClass.reads == 1

        when: 'Grails names the action it calls'
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'show')

        then:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).page == 'book/show'
        controllerClass.reads == 2
    }

    void "a request included in another is recorded as the URI it was included by, not the path including it"() {
        given: 'a page whose view includes another action, which its own URL mapping matched, as <g:include> does'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', contextPath + '/blog/my-secret-slug')
        request.contextPath = contextPath
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'post')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'show')
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(['blog', '(*)'], controllerClass('post', 'show')))
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when: 'the page queries, then the included action, then the page again'
        QueryTailRecord page = attribution.attribute(new QueryTailRecord(sql: 'select 1'))
        request.setAttribute(RequestDispatcher.INCLUDE_REQUEST_URI, contextPath + included)
        request.setAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH, contextPath)
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'widget')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'recent')
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(tokens, controllerClass('widget', 'recent')))
        QueryTailRecord include = attribution.attribute(new QueryTailRecord(sql: 'select 2'))
        request.removeAttribute(RequestDispatcher.INCLUDE_REQUEST_URI)
        request.removeAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH)
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'post')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'show')
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(['blog', '(*)'], controllerClass('post', 'show')))
        QueryTailRecord after = attribution.attribute(new QueryTailRecord(sql: 'select 3'))

        then: 'the include is its own page and line, in the same request, and the slug of the page is in neither'
        [page, include, after]*.page == ['post/show', 'widget/recent', 'post/show']
        [page, include, after]*.request == ["GET ${contextPath}/blog/?", "GET ${contextPath}${recorded}", "GET ${contextPath}/blog/?"]*.toString()
        [page, include, after]*.requestId.unique() == ['r1']
        [page, include, after].every { !it.toLine().contains('secret') }

        where:
        contextPath | included             | tokens                       || recorded
        ''          | '/widgets/recent'    | ['widgets', 'recent']        || '/widgets/recent'
        ''          | '/widgets/recent/5'  | ['widgets', 'recent', '(*)'] || '/widgets/recent/?'
        '/shop'     | '/widget/recent'     | ['(*)', '(*)?', '(*)?']      || '/widget/recent'
    }

    void "a request is described once, until it moves on as a forward moves it"() {
        given:
        QueryTailMatchedMapping mapping = new QueryTailMatchedMapping(['book', 'show', '(*)'])
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/book/show/42')
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'book')
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new GrailsControllerUrlMappingInfo(controllerClass('book'), mapping))
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when: 'the request runs three statements'
        List<String> lines = (1..3).collect { attribution.attribute(new QueryTailRecord(sql: 'select 1')).request }

        then: 'its mapping was read once'
        lines == ['GET /book/show/?'] * 3
        mapping.reads == 1

        when: 'it is forwarded to another action'
        QueryTailMatchedMapping forwarded = new QueryTailMatchedMapping(['book', 'edit', '(*)'])
        request.requestURI = '/book/edit/42'
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new GrailsControllerUrlMappingInfo(controllerClass('book'), forwarded))

        then:
        attribution.attribute(new QueryTailRecord(sql: 'select 1')).request == 'GET /book/edit/?'
        forwarded.reads == 1
    }

    /**
     * Binds a request the way the URL mapping leaves it: the controller and action as attributes, beside the mapping
     * it matched them with, which names the class of that controller, which has that action.
     */
    private static void bindRequest(Map<String, Object> attributes = [:], String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri)
        if (attributes.controller) {
            request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, attributes.controller)
            request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, matched(attributes.tokens as List<String>,
                    controllerClass(attributes.controller as String, attributes.action as String, attributes.namespace as String)))
        }
        if (attributes.action) {
            request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, attributes.action)
        }
        if (attributes.namespace) {
            request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, attributes.namespace)
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))
    }

    /**
     * @return the URL mapping Grails matched a request to a controller with, as it keeps it on the request: its data
     * names the tokens of the mapping, if any, and it names the class of the controller it resolved
     */
    private static UrlMappingInfo matched(List<String> tokens, GrailsControllerClass controllerClass) {
        new GrailsControllerUrlMappingInfo(controllerClass, new QueryTailMatchedMapping(tokens))
    }

    /**
     * @return the class of a controller, which has the action, if one is named, and is declared in the namespace
     */
    static QueryTailControllerClass controllerClass(String controller, String action = null, String namespace = null) {
        new QueryTailControllerClass(controller, action ? [action] : [], namespace)
    }
}

/**
 * The class of a controller Grails resolved a request to, which names the controller by its logical name, its
 * actions as a request names them, the namespace it is declared in and its default action, and counts how often it
 * was asked its name.
 */
@AutoImplement
class QueryTailControllerClass implements GrailsControllerClass {

    private final String name
    private final Set<String> actions
    private final String namespace
    private final String defaultAction
    int reads

    QueryTailControllerClass(String name, Collection<String> actions = [], String namespace = null, String defaultAction = 'index') {
        this.name = name
        this.actions = actions as Set<String>
        this.namespace = namespace
        this.defaultAction = defaultAction
    }

    @Override
    String getLogicalPropertyName() {
        reads++
        name
    }

    @Override
    Set<String> getActions() {
        actions
    }

    @Override
    String getNamespace() {
        namespace
    }

    @Override
    String getDefaultAction() {
        defaultAction
    }

    /** The metaclass of the class itself, which a Grails class declares and would otherwise be implemented as none. */
    @Override
    MetaClass getMetaClass() {
        GroovySystem.metaClassRegistry.getMetaClass(QueryTailControllerClass)
    }
}

/**
 * The URL mapping information Grails matched a request with, whose data names the tokens of the mapping, and which
 * counts how often it was asked for its data.
 */
@AutoImplement
class QueryTailMatchedMapping implements UrlMappingInfo {

    private final String[] tokens
    int reads

    QueryTailMatchedMapping(List<String> tokens) {
        this.tokens = tokens as String[]
    }

    @Override
    UrlMappingData getUrlData() {
        reads++
        new QueryTailMappingData(tokens)
    }
}

@AutoImplement
class QueryTailMappingData implements UrlMappingData {

    private final String[] tokens

    QueryTailMappingData(String[] tokens) {
        this.tokens = tokens
    }

    @Override
    String[] getTokens() {
        tokens
    }
}

/**
 * A controller whose default action is not {@code index}.
 */
class QueryTailShelfController {

    static defaultAction = 'browse'

    def browse() {
    }
}

/**
 * A form posted to a request, whose parameters, as a servlet container reads them, are parsed from the body and use
 * it up, so that the application can no longer read it.
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
