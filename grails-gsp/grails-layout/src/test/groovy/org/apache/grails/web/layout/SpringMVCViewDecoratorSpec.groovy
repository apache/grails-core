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
package org.apache.grails.web.layout

import java.lang.reflect.UndeclaredThrowableException

import jakarta.servlet.http.HttpServletResponse

import com.opensymphony.module.sitemesh.RequestConstants
import com.opensymphony.sitemesh.Content
import com.opensymphony.sitemesh.webapp.SiteMeshWebAppContext
import groovy.text.Template
import spock.lang.Specification

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.servlet.View
import org.springframework.web.servlet.view.InternalResourceView

import org.grails.web.servlet.view.AbstractGrailsView
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils

/**
 * Tests rendering of a layout through {@link SpringMVCViewDecorator}.
 */
class SpringMVCViewDecoratorSpec extends Specification {

    MockHttpServletRequest request = new MockHttpServletRequest()
    MockHttpServletResponse response = new MockHttpServletResponse()
    MockServletContext servletContext = new MockServletContext()
    Content content = new GSPGrailsLayoutPage()

    void 'the decorator is named after the view url for url based views and the view string otherwise'() {
        given:
        def urlView = new InternalResourceView('/layouts/main.jsp')
        def plain = Stub(View) {
            toString() >> 'plain view'
        }

        expect:
        new SpringMVCViewDecorator('main', urlView).getPage() == '/layouts/main.jsp'
        new SpringMVCViewDecorator('main', urlView).getName() == 'main'
        new SpringMVCViewDecorator('other', plain).getPage() == 'plain view'
        new SpringMVCViewDecorator('main', urlView).getView().is(urlView)
    }

    void 'rendering exposes the page and a layout page to the layout view and cleans up afterwards'() {
        given:
        def pageSeen = null
        def layoutPageSeen = null
        def view = Stub(View) {
            render(_, _, _) >> { Map model, def req, HttpServletResponse resp ->
                pageSeen = req.getAttribute(RequestConstants.PAGE)
                layoutPageSeen = req.getAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE)
                resp.writer.write("layout:${model.name}")
            }
        }
        def decorator = new SpringMVCViewDecorator('main', view)

        when:
        decorator.render(content, [name: 'x'], request, response, servletContext)

        then:
        response.contentAsString == 'layout:x'
        pageSeen.is(content)
        layoutPageSeen instanceof GSPGrailsLayoutPage
        !layoutPageSeen.is(content)
        request.getAttribute(RequestConstants.PAGE) == null
        request.getAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE) == null
    }

    void 'rendering through the SiteMesh context renders with an empty model'() {
        given:
        def modelSeen = null
        def view = Stub(View) {
            render(_, _, _) >> { Map model, def req, def resp -> modelSeen = model }
        }
        def decorator = new SpringMVCViewDecorator('main', view)
        def context = new SiteMeshWebAppContext(request, response, servletContext)

        when:
        decorator.render(content, context)

        then:
        modelSeen == [:]
    }

    void 'a committed response is not rendered to and the request attributes are still cleared'() {
        given:
        def view = Mock(View)
        def decorator = new SpringMVCViewDecorator('main', view)
        response.setCommitted(true)

        when:
        decorator.render(content, [:], request, response, servletContext)

        then:
        0 * view.render(_, _, _)
        request.getAttribute(RequestConstants.PAGE) == null
    }

    void 'a failing plain view is wrapped with the layout name and request state is cleaned'() {
        given:
        def cause = new IOException('io')
        def view = Stub(View) {
            render(_, _, _) >> { throw cause }
        }
        def decorator = new SpringMVCViewDecorator('main', view)
        request.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, 'scope')
        request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, 'layout')

        when:
        decorator.render(content, [:], request, response, servletContext)

        then:
        def e = thrown(RuntimeException)
        e.message == 'Error applying layout : main'
        e.cause.is(cause)
        request.getAttribute(GrailsApplicationAttributes.PAGE_SCOPE) == null
        request.getAttribute(WebUtils.LAYOUT_ATTRIBUTE) == null
    }

    void 'a failing Grails view rethrows runtime exceptions unchanged and wraps checked exceptions'() {
        given:
        def view = Stub(AbstractGrailsView) {
            render(_, _, _) >> { throw failure }
            rethrowRenderException(_, _) >> { Throwable t, String message ->
                if (t instanceof RuntimeException) {
                    throw t
                }
                throw new UndeclaredThrowableException(t, message)
            }
        }
        def decorator = new SpringMVCViewDecorator('main', view)

        when:
        decorator.render(content, [:], request, response, servletContext)

        then:
        def e = thrown(expected)
        e.is(failure) || e.cause.is(failure)

        where:
        failure                        | expected
        new IllegalStateException('x') | IllegalStateException
        new IOException('y')           | UndeclaredThrowableException
    }

    void 'the template is delegated for Grails views and null otherwise'() {
        given:
        def template = Stub(Template)
        def grailsView = Stub(AbstractGrailsView) {
            getTemplate() >> template
        }

        expect:
        new SpringMVCViewDecorator('main', grailsView).getTemplate().is(template)
        new SpringMVCViewDecorator('main', Stub(View)).getTemplate() == null
    }
}
