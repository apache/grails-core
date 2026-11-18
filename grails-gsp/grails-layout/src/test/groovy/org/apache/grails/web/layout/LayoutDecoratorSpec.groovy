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

import com.opensymphony.module.sitemesh.RequestConstants
import com.opensymphony.sitemesh.webapp.SiteMeshWebAppContext
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.View
import spock.lang.Specification

class LayoutDecoratorSpec extends Specification {

    void 'no decorator writes the original page unchanged'() {
        given:
        def request = new MockHttpServletRequest()
        def response = new MockHttpServletResponse()
        def context = new SiteMeshWebAppContext(request, response, request.servletContext)
        def html = '<html><head><title>Original</title></head><body>Original body</body></html>'
        def content = new GrailsHTMLPageParser().parseContent(html.toCharArray())
        def decorator = new GrailsNoDecorator()

        when:
        decorator.render(content, context)

        then:
        response.contentAsString == html
        decorator.getName() == null
        decorator.getPage() == null
        decorator.getURIPath() == null
        decorator.getRole() == null
        decorator.getInitParameter('missing') == null
        decorator.getInitParameterNames() == null
    }

    void 'view decorator makes page and model available during rendering and removes capture attributes afterwards'() {
        given:
        def request = new MockHttpServletRequest()
        def response = new MockHttpServletResponse()
        def content = new GrailsHTMLPageParser().parseContent('<html><body>Body</body></html>'.toCharArray())
        def model = [greeting: 'Hello']
        def view = Mock(View)
        def decorator = new SpringMVCViewDecorator('main', view)
        def pageDuringRender = null
        def layoutPageDuringRender = null

        when:
        decorator.render(content, model, request, response, request.servletContext)

        then:
        1 * view.render(model, request, response) >> { Map values, servletRequest, servletResponse ->
            pageDuringRender = servletRequest.getAttribute(RequestConstants.PAGE)
            layoutPageDuringRender = servletRequest.getAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE)
            servletResponse.getWriter().write(values.greeting + ': Body')
        }
        pageDuringRender.body == 'Body'
        layoutPageDuringRender instanceof GSPGrailsLayoutPage
        response.contentAsString == 'Hello: Body'
        response.committed
        request.getAttribute(RequestConstants.PAGE) == null
        request.getAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE) == null
        decorator.getView().is(view)
        decorator.getName() == 'main'
        decorator.getTemplate() == null
    }

    void 'committed response skips layout rendering'() {
        given:
        def request = new MockHttpServletRequest()
        def response = new MockHttpServletResponse()
        response.setCommitted(true)
        def view = Mock(View)
        def decorator = new SpringMVCViewDecorator('main', view)
        def context = new SiteMeshWebAppContext(request, response, request.servletContext)

        when:
        decorator.render(new GSPGrailsLayoutPage(), context)

        then:
        0 * view.render(_, _, _)
        request.getAttribute(RequestConstants.PAGE) == null
        request.getAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE) == null
    }

    void 'render failure is wrapped with layout name and clears page scope and layout selection'() {
        given:
        def request = new MockHttpServletRequest()
        def response = new MockHttpServletResponse()
        request.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, 'scope')
        request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, 'main')
        def cause = new IllegalStateException('Cannot render')
        def view = Mock(View)
        def decorator = new SpringMVCViewDecorator('main', view)

        when:
        decorator.render(new GSPGrailsLayoutPage(), [:], request, response, request.servletContext)

        then:
        1 * view.render(_, request, response) >> { throw cause }
        def error = thrown(RuntimeException)
        error.message == 'Error applying layout : main'
        error.cause.is(cause)
        request.getAttribute(GrailsApplicationAttributes.PAGE_SCOPE) == null
        request.getAttribute(WebUtils.LAYOUT_ATTRIBUTE) == null
    }
}
