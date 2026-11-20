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
package org.grails.web.mapping

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Issue

import grails.util.GrailsWebMockUtil
import grails.web.mapping.AbstractUrlMappingsSpec
import grails.web.mapping.UrlMappingInfo

/**
 * How a matched mapping resolves its names and creates URLs depending on the request attributes bound to the
 * thread. Grails binds a {@code GrailsWebRequest}. A {@code DispatcherServlet} other than the Grails one - the
 * one MockMvc runs, for example - binds a plain {@link ServletRequestAttributes} over it, and the
 * {@code GrailsWebRequest} is then found on the request, where {@code GrailsWebRequestFilter} stored it. Without
 * that filter there is no {@code GrailsWebRequest} at all.
 */
@Issue('https://github.com/apache/grails-core/issues/16129')
class UrlMappingInfoRequestAttributesSpec extends AbstractUrlMappingsSpec {

    private static final String GRAILS = 'a GrailsWebRequest'
    private static final String PLAIN_OVER_GRAILS = 'plain attributes over a stored GrailsWebRequest'
    private static final String PLAIN = 'plain attributes'
    private static final String NOTHING = 'nothing'

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'names a mapping states resolve with #attributes bound'() {
        given:
        bind(attributes, request('GET', '/book/show/42'))
        def mappings = getUrlMappingsHolder {
            "/book/show/$id"(controller: 'book', action: 'show')
            '/about'(view: '/about')
            '/old'(uri: '/new')
        }

        when:
        UrlMappingInfo controllerInfo = mappings.match('/book/show/42')
        UrlMappingInfo viewInfo = mappings.match('/about')
        UrlMappingInfo uriInfo = mappings.match('/old')

        then:
        with(controllerInfo) {
            namespace == null
            controllerName == 'book'
            actionName == 'show'
            viewName == null
            id == '42'
        }
        viewInfo.viewName == '/about'
        uriInfo.URI == '/new'

        where:
        attributes << [GRAILS, PLAIN_OVER_GRAILS, PLAIN, NOTHING]
    }

    void 'a name closure reads the GrailsWebRequest with #attributes bound'() {
        given:
        def request = request('GET', '/book')
        request.addParameter('section', 'gallery')
        bind(attributes, request)
        def mappings = getUrlMappingsHolder {
            '/book'(controller: 'book', action: { params.section })
        }

        expect:
        mappings.match('/book').actionName == 'gallery'

        where:
        attributes << [GRAILS, PLAIN_OVER_GRAILS]
    }

    void 'a name closure is not called when the bound attributes have no GrailsWebRequest'() {
        given:
        bind(PLAIN, request('GET', '/book'))
        def mappings = getUrlMappingsHolder {
            '/book'(controller: 'book', action: { throw new IllegalStateException('called') }, id: { throw new IllegalStateException('called') })
        }

        when:
        UrlMappingInfo info = mappings.match('/book')

        then:
        info.actionName == null
        info.id == null
    }

    void 'a name closure is called without a delegate when no request attributes are bound'() {
        given:
        def mappings = getUrlMappingsHolder {
            '/book'(controller: 'book', action: { 'show' })
        }

        expect:
        mappings.match('/book').actionName == 'show'
    }

    void 'a map of HTTP methods selects the name for the request method with #attributes bound'() {
        given:
        bind(attributes, request('POST', '/book'))
        def mappings = getUrlMappingsHolder {
            '/book'(controller: 'book', action: [GET: 'show', POST: 'save'])
        }

        expect:
        mappings.match('/book').actionName == expectedAction

        where:
        attributes        || expectedAction
        GRAILS            || 'save'
        PLAIN_OVER_GRAILS || 'save'
        PLAIN             || 'save'
        NOTHING           || null
    }

    void 'a mapping creates its URL with the context path of the GrailsWebRequest with #attributes bound'() {
        given:
        def request = request('GET', '/book/show')
        request.contextPath = '/app'
        bind(attributes, request)
        def mappings = getUrlMappingsHolder {
            '/book/show'(controller: 'book', action: 'show')
        }

        expect:
        mappings.getReverseMapping('book', 'show', [:]).createURL([:], 'utf-8') == expectedUrl

        where:
        attributes        || expectedUrl
        GRAILS            || '/app/book/show'
        PLAIN_OVER_GRAILS || '/app/book/show'
        PLAIN             || '/book/show'
        NOTHING           || '/book/show'
    }

    void 'the default URL creator uses the context path of the GrailsWebRequest with #attributes bound'() {
        given:
        def request = request('GET', '/book/show')
        request.contextPath = '/app'
        bind(attributes, request)
        def urlCreator = new DefaultUrlCreator('book', 'show')

        expect:
        urlCreator.createURL([:], 'utf-8') == expectedUrl
        urlCreator.createURL('book', 'show', [:], 'utf-8') == expectedUrl

        where:
        attributes        || expectedUrl
        GRAILS            || '/app/book/show'
        PLAIN_OVER_GRAILS || '/app/book/show'
        PLAIN             || '/book/show'
        NOTHING           || '/book/show'
    }

    private static MockHttpServletRequest request(String method, String uri) {
        new MockHttpServletRequest(new MockServletContext(), method, uri)
    }

    private static void bind(String attributes, MockHttpServletRequest request) {
        def response = new MockHttpServletResponse()
        switch (attributes) {
            case GRAILS:
                GrailsWebMockUtil.bindMockWebRequest(request.servletContext, request, response)
                break
            case PLAIN_OVER_GRAILS:
                GrailsWebMockUtil.bindMockWebRequest(request.servletContext, request, response)
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response))
                break
            case PLAIN:
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response))
                break
            default:
                RequestContextHolder.resetRequestAttributes()
        }
    }
}
