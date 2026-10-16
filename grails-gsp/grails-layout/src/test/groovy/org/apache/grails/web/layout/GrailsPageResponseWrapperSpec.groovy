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

import jakarta.servlet.http.HttpServletResponse

import com.opensymphony.module.sitemesh.Page
import com.opensymphony.module.sitemesh.PageParser
import com.opensymphony.module.sitemesh.PageParserSelector
import spock.lang.Specification

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Tests the buffering and routing behaviour of {@link GrailsPageResponseWrapper} through the
 * {@code HttpServletResponse} API that the wrapped view code calls.
 */
class GrailsPageResponseWrapperSpec extends Specification {

    MockHttpServletRequest request = new MockHttpServletRequest()
    MockHttpServletResponse response = new MockHttpServletResponse()
    PageParser pageParser = Mock(PageParser)
    PageParserSelector selector = Stub(PageParserSelector) {
        shouldParsePage(_ as String) >> { String type -> type.contains('html') }
        getPageParser(_ as String) >> pageParser
    }

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    private GrailsPageResponseWrapper wrapper() {
        new GrailsPageResponseWrapper(request, response, selector)
    }

    void 'a response without a content type is not buffered'() {
        when:
        def wrapper = wrapper()
        wrapper.writer.write('plain')
        wrapper.writer.flush()

        then:
        !wrapper.grailsLayoutActive
        !wrapper.usingStream
        wrapper.contents == null
        wrapper.page == null
        response.contentAsString == 'plain'
    }

    void 'a parseable content type captures writer output instead of writing to the response'() {
        given:
        def wrapper = wrapper()

        when:
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.writer.write('<html>hi</html>')
        wrapper.writer.flush()
        wrapper.writer.close()

        then:
        wrapper.grailsLayoutActive
        !wrapper.usingStream
        wrapper.contents == '<html>hi</html>'.toCharArray()
        response.contentAsString == ''
        response.contentType == 'text/html;charset=UTF-8'
    }

    void 'a parseable content type present on the response at construction activates buffering'() {
        given:
        response.setContentType('text/html;charset=UTF-8')

        when:
        def wrapper = wrapper()

        then:
        wrapper.grailsLayoutActive
    }

    void 'an empty char buffer yields null contents'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')

        when:
        wrapper.writer.write('')
        wrapper.writer.flush()

        then:
        wrapper.contents == null
    }

    void 'getPage parses the buffered contents with the selected parser'() {
        given:
        def parsed = Stub(Page)
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.writer.write('<html/>')
        wrapper.writer.flush()

        when:
        def page = wrapper.page

        then:
        1 * pageParser.parse('<html/>'.toCharArray()) >> parsed
        page.is(parsed)
    }

    void 'getPage returns null when no parser is available for the content type'() {
        given:
        def noParserSelector = Stub(PageParserSelector) {
            shouldParsePage(_ as String) >> true
            getPageParser(_ as String) >> null
        }
        def wrapper = new GrailsPageResponseWrapper(request, response, noParserSelector)
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.writer.write('<html/>')
        wrapper.writer.flush()

        expect:
        wrapper.page == null
    }

    void 'getPage returns the GSP layout page when one was used by the request'() {
        given:
        def gspPage = new GSPGrailsLayoutPage()
        gspPage.addProperty('title', 'x')
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspPage)
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')

        expect:
        wrapper.page.is(gspPage)
        wrapper.gspGrailsLayoutActive
    }

    void 'an unused GSP layout page is not reported as active and getPage falls back to parsing'() {
        given:
        def gspPage = new GSPGrailsLayoutPage()
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspPage)
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.writer.write('<html/>')
        wrapper.writer.flush()

        when:
        wrapper.page

        then:
        !wrapper.gspGrailsLayoutActive
        1 * pageParser.parse(_ as char[])
    }

    void 'activating buffering twice keeps the first buffer'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.writer.write('first')
        wrapper.writer.flush()

        when:
        wrapper.activateGrailsLayout('text/html', 'UTF-8')

        then:
        wrapper.contents == 'first'.toCharArray()
    }

    void 'the buffer is created lazily and registers itself on the GSP layout page'() {
        given:
        def gspPage = new GSPGrailsLayoutPage()
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspPage)
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')

        when:
        wrapper.writer.write('abc')
        wrapper.writer.flush()

        then:
        gspPage.getPageBuffer() != null
        gspPage.getPage() == 'abc'
    }

    void 'switching to a non parseable content type routes output to the response and resets the GSP page'() {
        given:
        def gspPage = new GSPGrailsLayoutPage()
        gspPage.addProperty('a', 'b')
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspPage)
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')

        when:
        wrapper.setContentType('application/json')
        wrapper.writer.write('{}')
        wrapper.writer.flush()

        then:
        !wrapper.grailsLayoutActive
        wrapper.contents == null
        !gspPage.used
        response.contentAsString == '{}'
    }

    void 'getOutputStream buffers bytes and decodes them with the content type encoding'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')

        when:
        def out = wrapper.outputStream
        out.write('<a>'.bytes)
        out.write(0x62)
        out.write('<c>'.bytes, 1, 1)
        out.flush()

        then:
        wrapper.usingStream
        wrapper.contents == '<a>bc'.toCharArray()
        response.contentAsByteArray.length == 0
    }

    void 'the buffered output stream reports ready'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        def out = wrapper.outputStream
        out.write(1)

        expect:
        wrapper.usingStream
        out.ready
    }

    void 'the output stream goes straight to the response when not parseable'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('application/octet-stream')

        when:
        wrapper.outputStream.write('raw'.bytes)

        then:
        !wrapper.usingStream
        response.contentAsString == 'raw'
    }

    void 'using the writer after the output stream is rejected'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.outputStream.write(1)

        when:
        wrapper.writer.write('x')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'response.getWriter() called after response.getOutputStream()'
    }

    void 'using the output stream after the writer is rejected'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.writer.write('x')

        when:
        wrapper.outputStream.write(1)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'response.getOutputStream() called after response.getWriter()'
    }

    void 'content length is only passed to the response when the page is not parseable'() {
        given:
        def wrapper = wrapper()

        when:
        wrapper.setContentLength(10)

        then:
        response.contentLength == 10

        when:
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.setContentLength(99)

        then:
        response.contentLength == 10
    }

    void 'flushBuffer is suppressed while buffering and delegated otherwise'() {
        given:
        def wrapper = wrapper()

        when:
        wrapper.flushBuffer()

        then:
        response.committed

        when:
        def response2 = new MockHttpServletResponse()
        def buffering = new GrailsPageResponseWrapper(request, response2, selector)
        buffering.setContentType('text/html;charset=UTF-8')
        buffering.flushBuffer()

        then:
        !response2.committed
    }

    void 'headers are filtered while buffering and content-type headers are redirected to setContentType'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')

        when:
        wrapper.setHeader('Content-Length', '12')
        wrapper.addHeader('content-length', '12')
        wrapper.setHeader('X-One', '1')
        wrapper.addHeader('X-Two', '2')

        then:
        !response.containsHeader('Content-Length')
        response.getHeader('X-One') == '1'
        response.getHeader('X-Two') == '2'

        when:
        wrapper.setHeader('Content-Type', 'application/json')

        then:
        !wrapper.grailsLayoutActive
        response.contentType.startsWith('application/json')

        when:
        wrapper.addHeader('Content-Type', 'text/html;charset=UTF-8')

        then:
        wrapper.grailsLayoutActive

        when:
        def plain = new GrailsPageResponseWrapper(request, new MockHttpServletResponse(), selector)
        plain.setHeader('Content-Length', '5')
        plain.addHeader('Content-Length', '5')

        then:
        plain.response.getHeader('Content-Length') == '5'
    }

    void 'a 304 status stops buffering'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')

        when:
        wrapper.setStatus(HttpServletResponse.SC_NOT_MODIFIED)

        then:
        !wrapper.grailsLayoutActive
        response.status == 304
    }

    void 'an error status clears buffered content'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.writer.write('partial')
        wrapper.writer.flush()

        when:
        wrapper.setStatus(500)

        then:
        wrapper.contents == null
        response.status == 500
    }

    void 'a success status leaves buffered content alone'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.writer.write('keep')
        wrapper.writer.flush()

        when:
        wrapper.setStatus(200)

        then:
        wrapper.contents == 'keep'.toCharArray()
    }

    void 'clearing a byte buffer and resetting the GSP page happens on error status'() {
        given:
        def gspPage = new GSPGrailsLayoutPage()
        gspPage.addProperty('a', 'b')
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspPage)
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.outputStream.write('bytes'.bytes)

        when:
        wrapper.setStatus(404)

        then:
        wrapper.contents.length == 0
        !gspPage.used
    }

    void 'sendError clears the buffer, delegates and restores the bound GrailsWebRequest'() {
        given:
        def webRequest = new GrailsWebRequest(request, response, new MockServletContext())
        RequestContextHolder.setRequestAttributes(webRequest)
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.writer.write('partial')
        wrapper.writer.flush()

        when:
        wrapper.sendError(404)

        then:
        wrapper.contents == null
        response.status == 404
        GrailsWebRequest.lookup().is(webRequest)
    }

    void 'sendError with a message delegates the message'() {
        given:
        def webRequest = new GrailsWebRequest(request, response, new MockServletContext())
        RequestContextHolder.setRequestAttributes(webRequest)
        def wrapper = wrapper()

        when:
        wrapper.sendError(403, 'forbidden')

        then:
        response.status == 403
        response.errorMessage == 'forbidden'
        GrailsWebRequest.lookup().is(webRequest)
    }

    void 'sendError works when no GrailsWebRequest is bound'() {
        given:
        def other = new MockHttpServletResponse()

        when:
        wrapper().sendError(500)
        new GrailsPageResponseWrapper(request, other, selector).sendError(502, 'x')

        then:
        response.status == 500
        other.status == 502
        GrailsWebRequest.lookup() == null
    }

    void 'sendRedirect clears the buffer and redirects'() {
        given:
        def wrapper = wrapper()
        wrapper.setContentType('text/html;charset=UTF-8')
        wrapper.writer.write('partial')
        wrapper.writer.flush()

        when:
        wrapper.sendRedirect('/elsewhere')

        then:
        wrapper.contents == null
        response.redirectedUrl == '/elsewhere'
    }

    void 'reset and resetBuffer are ignored while buffering and delegated otherwise'() {
        given:
        def wrapper = wrapper()
        response.setStatus(201)
        response.writer.write('written')

        when:
        wrapper.reset()
        wrapper.resetBuffer()

        then:
        response.status == 200
        response.contentAsString == ''

        when:
        def buffering = new GrailsPageResponseWrapper(request, response, selector)
        buffering.setContentType('text/html;charset=UTF-8')
        response.setStatus(201)
        response.writer.write('kept')
        buffering.reset()
        buffering.resetBuffer()

        then:
        response.status == 201
        response.contentAsString == 'kept'
    }
}
