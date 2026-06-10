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
package org.grails.plugins.sitemesh3

import java.util.Locale

import jakarta.servlet.ServletContext

import org.sitemesh.DecoratorSelector
import org.sitemesh.SiteMeshContext
import org.sitemesh.content.ContentProcessor

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.view.InternalResourceView
import org.springframework.web.servlet.view.RedirectView

import spock.lang.Specification

class GrailsSiteMeshViewResolverSpec extends Specification {

    ViewResolver inner = Mock(ViewResolver)
    ContentProcessor contentProcessor = Mock(ContentProcessor)
    DecoratorSelector<SiteMeshContext> decoratorSelector = Mock(DecoratorSelector)
    ServletContext servletContext = Mock(ServletContext)

    GrailsSiteMeshViewResolver resolver() {
        new GrailsSiteMeshViewResolver(inner, contentProcessor, decoratorSelector, servletContext)
    }

    void "resolveViewName wraps the inner view in a GrailsSiteMeshView"() {
        given:
        View innerView = Mock(View)
        inner.resolveViewName('/foo/bar', Locale.ENGLISH) >> innerView

        when:
        View result = resolver().resolveViewName('/foo/bar', Locale.ENGLISH)

        then:
        result instanceof GrailsSiteMeshView
    }

    void "layout paths are passed through without wrapping"() {
        given:
        View innerView = Mock(View)
        inner.resolveViewName('/layouts/main', Locale.ENGLISH) >> innerView

        when:
        View result = resolver().resolveViewName('/layouts/main', Locale.ENGLISH)

        then:
        result.is(innerView)
    }

    void "redirect views are passed through without wrapping"() {
        given:
        RedirectView redirect = new RedirectView('/target')
        inner.resolveViewName('/goto', Locale.ENGLISH) >> redirect

        when:
        View result = resolver().resolveViewName('/goto', Locale.ENGLISH)

        then:
        result.is(redirect)
    }

    void "JSP inner views are rendered via include so the response survives decoration"() {
        given: 'a JSP view resolved through the SiteMesh resolver'
        InternalResourceView jspView = new InternalResourceView('/WEB-INF/grails-app/views/demo/hello.jsp')
        inner.resolveViewName('/demo/hello', Locale.ENGLISH) >> jspView
        MockHttpServletRequest request = new MockHttpServletRequest()
        MockHttpServletResponse response = new MockHttpServletResponse()

        when:
        View result = resolver().resolveViewName('/demo/hello', Locale.ENGLISH)
        result.render([:], request, response)

        then: 'the JSP is dispatched with include semantics, not a forward'
        response.includedUrl == '/WEB-INF/grails-app/views/demo/hello.jsp'
        response.forwardedUrl == null
    }

    void "null inner view yields null"() {
        given:
        inner.resolveViewName('/missing', Locale.ENGLISH) >> null

        expect:
        resolver().resolveViewName('/missing', Locale.ENGLISH) == null
    }
}
