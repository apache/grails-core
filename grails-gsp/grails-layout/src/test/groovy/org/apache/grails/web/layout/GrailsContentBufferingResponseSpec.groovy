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

import com.opensymphony.sitemesh.Content
import com.opensymphony.sitemesh.ContentProcessor
import com.opensymphony.sitemesh.SiteMeshContext
import com.opensymphony.sitemesh.webapp.SiteMeshWebAppContext
import spock.lang.Specification

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Tests {@link GrailsContentBufferingResponse}, {@link GrailsViewBufferingResponse} and the
 * {@link GrailsLayoutView} factory method that creates a buffering response.
 */
class GrailsContentBufferingResponseSpec extends Specification {

    MockHttpServletRequest request = new MockHttpServletRequest()
    MockHttpServletResponse response = new MockHttpServletResponse()
    Content built = Stub(Content)
    ContentProcessor processor = Mock(ContentProcessor)

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    private GrailsContentBufferingResponse buffering() {
        new GrailsContentBufferingResponse(response, processor, new SiteMeshWebAppContext(request, response, request.servletContext))
    }

    void 'the buffering response always reports being committed and exposes the target response'() {
        when:
        def buffering = buffering()

        then:
        buffering.isCommitted()
        buffering.getTargetResponse().is(response)
        !buffering.isActive()
        !buffering.isUsingStream()
    }

    void 'a content type already on the response is applied to the SiteMesh context'() {
        given:
        response.setContentType('text/html;charset=UTF-8')

        when:
        def buffering = buffering()

        then: 'html handling is enabled so output is captured'
        1 * processor.handles('text/html') >> true
        buffering.isActive()
    }

    void 'setting the content type on the wrapper activates buffering when the processor handles it'() {
        given:
        def buffering = buffering()

        when:
        buffering.setContentType('text/html;charset=UTF-8')

        then:
        1 * processor.handles('text/html') >> true
        buffering.isActive()

        when:
        buffering.setContentType('image/png')

        then:
        1 * processor.handles('image/png') >> false
        !buffering.isActive()
    }

    void 'deactivating the layout and the response mutator stop buffering'() {
        given:
        processor.handles(_ as String) >> true
        def buffering = buffering()
        buffering.setContentType('text/html;charset=UTF-8')

        when:
        buffering.deactivateGrailsLayout()

        then:
        !buffering.isActive()

        when:
        buffering.setContentType('text/html;charset=UTF-8')
        buffering.deactivateResponseMutator()

        then:
        !buffering.isActive()
    }

    void 'getContent returns null when buffering is not active'() {
        expect:
        buffering().getContent() == null
    }

    void 'getContent builds content from the buffered output with the content processor'() {
        given:
        processor.handles(_ as String) >> true
        def buffering = buffering()
        buffering.setContentType('text/html;charset=UTF-8')
        buffering.writer.write('<html>x</html>')
        buffering.writer.flush()

        when:
        def content = buffering.getContent()

        then:
        1 * processor.build('<html>x</html>'.toCharArray(), { SiteMeshContext ctx -> ctx instanceof SiteMeshWebAppContext && ctx.contentType == 'text/html;charset=UTF-8' }) >> built
        content.is(built)
    }

    void 'getContent returns the GSP layout page when the inner view populated it'() {
        given:
        processor.handles(_ as String) >> true
        def gspPage = new GSPGrailsLayoutPage()
        gspPage.addProperty('meta.layout', 'main')
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspPage)
        def buffering = buffering()
        buffering.setContentType('text/html;charset=UTF-8')

        expect:
        buffering.getContent().is(gspPage)
        buffering.isActive()
    }

    void 'getContent returns null when nothing was buffered'() {
        given:
        processor.handles(_ as String) >> true
        def buffering = buffering()
        buffering.setContentType('text/html;charset=UTF-8')

        expect:
        buffering.getContent() == null
    }

    void 'sendError is swallowed because the buffering response is always committed and restores the web request'() {
        given:
        def webRequest = new GrailsWebRequest(request, response, new MockServletContext())
        RequestContextHolder.setRequestAttributes(webRequest)
        def buffering = buffering()

        when:
        buffering.sendError(500)
        buffering.sendError(500, 'boom')

        then:
        response.status == 200
        GrailsWebRequest.lookup().is(webRequest)
    }

    void 'sendError works without a bound web request'() {
        when:
        def buffering = buffering()
        buffering.sendError(404)
        buffering.sendError(404, 'missing')

        then:
        response.status == 200
        GrailsWebRequest.lookup() == null
    }

    void 'sendRedirect is delegated to the wrapped response'() {
        given:
        def buffering = buffering()

        when:
        buffering.sendRedirect('/next')

        then:
        response.redirectedUrl == '/next'
    }

    void 'the view buffering response parses html output into content with the head, body and title'() {
        given:
        response.setContentType('text/html;charset=UTF-8')
        def buffering = new GrailsViewBufferingResponse(request, response)

        when:
        buffering.writer.write('<html><head><title>Hi</title><meta name="a" content="b"></head><body>text</body></html>')
        buffering.writer.flush()
        def content = buffering.getContent()
        def body = new StringWriter()
        content.writeBody(body)

        then:
        buffering.isActive()
        content.title == 'Hi'
        body.toString() == 'text'
        content.getProperty('meta.a') == 'b'
    }

    void 'the view buffering response ignores content types other than html'() {
        given:
        def buffering = new GrailsViewBufferingResponse(request, response)

        when:
        buffering.setContentType('application/json')
        buffering.writer.write('{}')
        buffering.writer.flush()

        then:
        !buffering.isActive()
        buffering.getContent() == null
        response.contentAsString == '{}'
    }

    void 'the view buffering response treats a missing content type as unhandled'() {
        given:
        def buffering = new GrailsViewBufferingResponse(request, response)

        expect:
        !buffering.isActive()
        buffering.getContent() == null
    }

    void 'a layout view buffers with the configured content processor'() {
        given:
        processor.handles(_ as String) >> true
        response.setContentType('text/html;charset=UTF-8')
        def webRequest = new GrailsWebRequest(request, response, new MockServletContext())
        def layoutView = new GrailsLayoutView(null, null, processor)

        when:
        def buffering = layoutView.createContentBufferingResponse([:], webRequest, request, response)

        then:
        buffering.isActive()
        buffering.getTargetResponse().is(response)
    }
}
