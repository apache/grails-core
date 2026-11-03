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

import org.sitemesh.DecoratorSelector
import org.sitemesh.SiteMeshContext
import org.sitemesh.content.ContentProcessor
import spock.lang.Specification

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver

/**
 * Tests that {@link GrailsSiteMeshView} manages the captured page request attribute around a render.
 */
class GrailsSiteMeshViewSpec extends Specification {

    MockHttpServletRequest request = new MockHttpServletRequest()
    MockHttpServletResponse response = new MockHttpServletResponse()
    MockServletContext servletContext = new MockServletContext()
    ViewResolver viewResolver = Stub(ViewResolver)
    DecoratorSelector<SiteMeshContext> selector = Stub(DecoratorSelector) {
        selectDecoratorPaths(_, _) >> ([] as String[])
    }
    Object seenDuringRender

    View inner = Stub(View) {
        render(_, _, _) >> { Map model, def req, def resp ->
            seenDuringRender = req.getAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE)
            resp.setContentType('text/html')
            resp.writer.write('<html><body>inner</body></html>')
            resp.writer.flush()
        }
    }

    private GrailsSiteMeshView view() {
        new GrailsSiteMeshView(inner, new CaptureAwareContentProcessor(), selector, servletContext, viewResolver)
    }

    void 'a fresh captured page is available while rendering and removed afterwards'() {
        when:
        view().render([:], request, response)

        then:
        seenDuringRender instanceof Sitemesh3CapturedPage
        request.getAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE) == null
        response.contentAsString == '<html><body>inner</body></html>'
    }

    void 'a previously bound captured page is restored after rendering'() {
        given:
        def previous = new Sitemesh3CapturedPage()
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, previous)

        when:
        view().render([:], request, response)

        then:
        !seenDuringRender.is(previous)
        request.getAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE).is(previous)
    }

    void 'decoration renders the selected layout through a Grails SiteMesh view context'() {
        given:
        def layoutSeen = null
        def layout = Stub(View) {
            render(_, _, _) >> { Map model, def req, def resp ->
                layoutSeen = req.getAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE)
                resp.writer.write('<html><body>decorated</body></html>')
                resp.writer.flush()
            }
        }
        def resolver = Stub(ViewResolver) {
            resolveViewName('/layouts/main', _) >> layout
        }
        def decorating = Stub(DecoratorSelector) {
            selectDecoratorPaths(_, _) >> (['/layouts/main'] as String[])
        }
        def decoratedView = new GrailsSiteMeshView(inner, new CaptureAwareContentProcessor(), decorating, servletContext, resolver)

        when:
        decoratedView.render([:], request, response)

        then:
        layoutSeen instanceof Sitemesh3CapturedPage
        response.contentAsString.contains('decorated')
    }
}
