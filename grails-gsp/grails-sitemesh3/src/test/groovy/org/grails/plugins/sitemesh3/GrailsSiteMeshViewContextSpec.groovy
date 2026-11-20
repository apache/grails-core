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

import org.sitemesh.content.ContentProcessor
import org.sitemesh.webapp.contentfilter.ResponseMetaData

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver

import spock.lang.Specification

class GrailsSiteMeshViewContextSpec extends Specification {

    MockHttpServletRequest request = new MockHttpServletRequest()
    MockHttpServletResponse response = new MockHttpServletResponse()
    MockServletContext servletContext = new MockServletContext()
    ContentProcessor contentProcessor = Mock(ContentProcessor)
    ViewResolver viewResolver = Mock(ViewResolver)

    private GrailsSiteMeshViewContext newContext() {
        new GrailsSiteMeshViewContext(
                'text/html', request, response, servletContext,
                contentProcessor, new ResponseMetaData(), false,
                viewResolver, Locale.ENGLISH)
    }

    void "dispatch pushes a fresh Sitemesh3CapturedPage for the decorator render"() {
        given:
        Sitemesh3CapturedPage existing = new Sitemesh3CapturedPage()
        existing.markUsed()
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, existing)
        View view = Mock(View)
        viewResolver.resolveViewName('/layouts/foo', Locale.ENGLISH) >> view

        when:
        newContext().dispatch(request, response, '/layouts/foo')

        then:
        def captured = request.getAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE) as Sitemesh3CapturedPage
        captured != null
        captured.is(existing) == false
        captured.isUsed() == false
    }

    void "dispatch leaves the fresh captured page on the request for chained decoration"() {
        given:
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, new Sitemesh3CapturedPage())
        View view = Mock(View)
        viewResolver.resolveViewName('/layouts/foo', Locale.ENGLISH) >> view

        when:
        newContext().dispatch(request, response, '/layouts/foo')

        then: 'no restore — chained decoration reads this after dispatch'
        request.getAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE) instanceof Sitemesh3CapturedPage
    }

    void "dispatch resolves the layout path through the supplied ViewResolver"() {
        given:
        View view = Mock(View)
        viewResolver.resolveViewName('/layouts/custom', Locale.ENGLISH) >> view

        when:
        newContext().dispatch(request, response, '/layouts/custom')

        then:
        1 * view.render(_, request, response)
    }

    void "dispatch renders the layout view with an empty model by default"() {
        given:
        View view = Mock(View)
        viewResolver.resolveViewName('/layouts/custom', Locale.ENGLISH) >> view

        when:
        newContext().dispatch(request, response, '/layouts/custom')

        then:
        1 * view.render([:], request, response)
    }

    void "dispatch renders the layout view with the configured view model"() {
        given:
        View view = Mock(View)
        viewResolver.resolveViewName('/layouts/custom', Locale.ENGLISH) >> view
        GrailsSiteMeshViewContext context = newContext()
        context.setViewModel([greeting: 'hello'])

        when:
        context.dispatch(request, response, '/layouts/custom')

        then:
        1 * view.render([greeting: 'hello'], request, response)
    }

    void "a null view model falls back to an empty model"() {
        given:
        View view = Mock(View)
        viewResolver.resolveViewName('/layouts/custom', Locale.ENGLISH) >> view
        GrailsSiteMeshViewContext context = newContext()
        context.setViewModel(null)

        when:
        context.dispatch(request, response, '/layouts/custom')

        then:
        1 * view.render([:], request, response)
    }
}
