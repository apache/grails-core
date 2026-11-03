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

import jakarta.servlet.DispatcherType

import com.opensymphony.module.sitemesh.RequestConstants
import com.opensymphony.sitemesh.Content
import groovy.text.Template
import spock.lang.Specification

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.View

import org.grails.web.servlet.WrappedResponseHolder
import org.grails.web.servlet.mvc.OutputAwareHttpServletResponse
import org.grails.web.servlet.view.AbstractGrailsView

/**
 * Tests the end to end decoration flow of {@link EmbeddedGrailsLayoutView} through the Spring
 * {@link View#render} contract.
 */
class EmbeddedGrailsLayoutViewSpec extends Specification {

    static final String HTML = '<html><head><title>T</title></head><body>B</body></html>'

    MockHttpServletRequest request = new MockHttpServletRequest()
    MockHttpServletResponse response = new MockHttpServletResponse()
    GroovyPageLayoutFinder finder = Mock(GroovyPageLayoutFinder)

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    private View htmlView(String body = HTML, String contentType = 'text/html;charset=UTF-8') {
        Stub(View) {
            render(_, _, _) >> { Map model, def req, def resp ->
                resp.setContentType(contentType)
                resp.writer.write(body)
                resp.writer.flush()
            }
        }
    }

    void 'the content type is the wildcard media type and the inner view is exposed'() {
        given:
        def inner = Stub(View)
        def view = new EmbeddedGrailsLayoutView(finder, inner)

        expect:
        view.contentType == '*/*'
        view.getInnerView().is(inner)
    }

    void 'the template is delegated to an inner Grails view and null for other views'() {
        given:
        def template = Stub(Template)
        def grailsView = Stub(AbstractGrailsView) {
            getTemplate() >> template
        }

        expect:
        new EmbeddedGrailsLayoutView(finder, grailsView).getTemplate().is(template)
        new EmbeddedGrailsLayoutView(finder, Stub(View)).getTemplate() == null
    }

    void 'rendering applies the layout found for the buffered content'() {
        given:
        def pageSeenByLayout = null
        def layoutView = Stub(View) {
            render(_, _, _) >> { Map model, def req, def resp ->
                pageSeenByLayout = req.getAttribute(RequestConstants.PAGE)
                resp.writer.write('DECORATED')
            }
        }
        def decorator = new SpringMVCViewDecorator('main', layoutView)
        def view = new EmbeddedGrailsLayoutView(finder, htmlView())

        when:
        view.render([:], request, response)

        then:
        1 * finder.findLayout(request, _ as Content) >> decorator
        response.contentAsString == 'DECORATED'
        pageSeenByLayout != null
        request.getAttribute(RequestConstants.PAGE) == null
        request.getAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE) == null
    }

    void 'rendering writes the original content when no layout applies'() {
        given:
        def view = new EmbeddedGrailsLayoutView(finder, htmlView())

        when:
        view.render([:], request, response)

        then:
        1 * finder.findLayout(request, _ as Content) >> null
        response.contentAsString == HTML
    }

    void 'rendering does not look for a layout on include dispatches'() {
        given:
        request.setDispatcherType(DispatcherType.INCLUDE)
        def view = new EmbeddedGrailsLayoutView(finder, htmlView())

        when:
        view.render([:], request, response)

        then:
        0 * finder.findLayout(_, _)
        response.contentAsString == HTML
    }

    void 'rendering looks for a layout on #type dispatches'() {
        given:
        request.setDispatcherType(type)
        def view = new EmbeddedGrailsLayoutView(finder, htmlView())

        when:
        view.render([:], request, response)

        then:
        1 * finder.findLayout(request, _ as Content) >> null
        response.contentAsString == HTML

        where:
        type << [DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR, DispatcherType.ASYNC]
    }

    void 'content that is not html is not decorated and nothing is written by the layout view'() {
        given:
        def view = new EmbeddedGrailsLayoutView(finder, htmlView('{}', 'application/json'))

        when:
        view.render([:], request, response)

        then:
        0 * finder.findLayout(_, _)
        response.contentAsString == '{}'
    }

    void 'a committed response whose writer is unavailable is left untouched'() {
        given:
        def inner = Mock(View)
        def committed = new MockHttpServletResponse()
        committed.setCommitted(true)
        def outputAware = new OutputAwareHttpServletResponse(committed)
        outputAware.writerAvailable = false
        def view = new EmbeddedGrailsLayoutView(finder, inner)

        when:
        view.render([:], request, outputAware)

        then:
        0 * inner.render(_, _, _)
        0 * finder.findLayout(_, _)
    }

    void 'the meta http-equiv content type replaces a bare text/html response content type'() {
        given:
        def page = '<html><head><meta http-equiv="Content-Type" content="text/html; charset=utf-8"></head><body>B</body></html>'
        def view = new EmbeddedGrailsLayoutView(finder, htmlView(page, 'text/html'))

        when:
        view.render([:], request, response)

        then:
        1 * finder.findLayout(request, _ as Content) >> null
        response.contentType == 'text/html; charset=utf-8'
    }

    void 'the meta http-equiv content type is ignored when the response already has a charset'() {
        given:
        def page = '<html><head><meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"></head><body>B</body></html>'
        def view = new EmbeddedGrailsLayoutView(finder, htmlView(page))

        when:
        view.render([:], request, response)

        then:
        1 * finder.findLayout(request, _ as Content) >> null
        response.contentType == 'text/html;charset=UTF-8'
    }

    void 'previously bound request attributes and wrapped responses are restored after rendering'() {
        given:
        def oldPage = Stub(com.opensymphony.module.sitemesh.Page)
        def oldGspPage = new GSPGrailsLayoutPage()
        request.setAttribute(RequestConstants.PAGE, oldPage)
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, oldGspPage)
        def previous = new MockHttpServletResponse()
        WrappedResponseHolder.setWrappedResponse(previous)
        def view = new EmbeddedGrailsLayoutView(finder, htmlView())
        finder.findLayout(_, _) >> null

        when:
        view.render([:], request, response)

        then:
        request.getAttribute(RequestConstants.PAGE).is(oldPage)
        request.getAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE).is(oldGspPage)
        WrappedResponseHolder.getWrappedResponse().is(previous)

        cleanup:
        WrappedResponseHolder.setWrappedResponse(null)
    }

    void 'an error thrown by the inner view propagates and the wrapped response is released'() {
        given:
        def inner = Stub(View) {
            render(_, _, _) >> { throw new IllegalStateException('inner failed') }
        }
        def view = new EmbeddedGrailsLayoutView(finder, inner)

        when:
        view.render([:], request, response)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'inner failed'
        WrappedResponseHolder.getWrappedResponse() == null
    }
}
