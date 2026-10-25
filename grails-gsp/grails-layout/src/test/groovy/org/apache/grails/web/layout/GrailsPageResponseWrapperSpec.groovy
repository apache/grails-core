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

import com.opensymphony.module.sitemesh.PageParserSelector
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class GrailsPageResponseWrapperSpec extends Specification {

    MockHttpServletRequest request = new MockHttpServletRequest()
    MockHttpServletResponse response = new MockHttpServletResponse()
    PageParserSelector parserSelector = Stub(PageParserSelector) {
        shouldParsePage('text/html') >> true
        getPageParser('text/html') >> new GrailsHTMLPageParser()
    }

    void 'HTML written through the writer is buffered and parsed without committing the response'() {
        given:
        def wrapper = new GrailsPageResponseWrapper(request, response, parserSelector)
        def html = '<html><head><title>Layout</title></head><body>Hello</body></html>'
        wrapper.setContentType('text/html;charset=UTF-8')

        when:
        wrapper.getWriter().write(html)
        wrapper.getWriter().flush()
        wrapper.flushBuffer()
        wrapper.setContentLength(html.length())
        wrapper.setHeader('Content-Length', '200')
        wrapper.addHeader('content-length', '300')
        wrapper.setHeader('X-Layout', 'buffered')
        wrapper.addHeader('X-Layout', 'parsed')

        then:
        wrapper.isGrailsLayoutActive()
        !wrapper.isUsingStream()
        new String(wrapper.getContents()) == html
        wrapper.getPage().title == 'Layout'
        wrapper.getPage().body == 'Hello'
        response.contentAsString == ''
        !response.committed
        response.getHeader('Content-Length') == null
        response.getHeaders('X-Layout') == ['buffered', 'parsed']
    }

    void 'HTML output stream uses the declared encoding'() {
        given:
        def wrapper = new GrailsPageResponseWrapper(request, response, parserSelector)
        def html = '<html><body>caf\u00e9</body></html>'
        wrapper.setHeader('Content-Type', 'text/html;charset=UTF-8')

        when:
        wrapper.getOutputStream().write(html.getBytes('UTF-8'))
        def page = wrapper.getPage()

        then:
        wrapper.isUsingStream()
        page.page == html
        page.body == 'caf\u00e9'
        response.contentAsByteArray.length == 0
    }

    void 'buffering disallows switching between writer and output stream'() {
        given:
        def wrapper = new GrailsPageResponseWrapper(request, response, parserSelector)
        wrapper.setContentType('text/html')

        when:
        if (streamFirst) {
            wrapper.getOutputStream().write(65)
            wrapper.getWriter().write('B')
        } else {
            wrapper.getWriter().write('A')
            wrapper.getOutputStream().write(66)
        }

        then:
        thrown(IllegalStateException)

        where:
        streamFirst << [true, false]
    }

    void 'unparseable content is passed through with content length and flush'() {
        given:
        def wrapper = new GrailsPageResponseWrapper(request, response, parserSelector)

        when:
        wrapper.addHeader('Content-Type', 'text/plain')
        wrapper.setContentLength(5)
        wrapper.setHeader('Content-Length', '5')
        wrapper.getWriter().write('plain')
        wrapper.flushBuffer()

        then:
        !wrapper.isGrailsLayoutActive()
        wrapper.getPage() == null
        wrapper.getContents() == null
        response.contentAsString == 'plain'
        response.getHeader('Content-Length') == '5'
        response.committed
    }

    void 'not modified status deactivates buffering and routes subsequent output to the target'() {
        given:
        def wrapper = new GrailsPageResponseWrapper(request, response, parserSelector)
        wrapper.setContentType('text/html')
        wrapper.getWriter().write('discarded')

        when:
        wrapper.setStatus(304)
        wrapper.getWriter().write('target')
        wrapper.getWriter().flush()

        then:
        response.status == 304
        !wrapper.isGrailsLayoutActive()
        response.contentAsString == 'target'
    }

    void 'error status clears buffered content before writing the error page'() {
        given:
        def wrapper = new GrailsPageResponseWrapper(request, response, parserSelector)
        wrapper.setContentType('text/html')
        wrapper.getWriter().write('discarded')

        when:
        wrapper.setStatus(500)
        wrapper.getWriter().write('<html><body>Error</body></html>')

        then:
        response.status == 500
        wrapper.getPage().body == 'Error'
    }

    void 'redirect and sendError discard buffered data and delegate status to the target'() {
        given:
        def wrapper = new GrailsPageResponseWrapper(request, response, parserSelector)
        wrapper.setContentType('text/html')
        wrapper.getWriter().write('discarded')

        when:
        switch (operation) {
            case 'redirect':
                wrapper.sendRedirect('/next')
                break
            case 'error':
                wrapper.sendError(404)
                break
            case 'message':
                wrapper.sendError(503, 'Unavailable')
                break
        }

        then:
        response.status == status
        response.committed
        response.redirectedUrl == location
        response.errorMessage == message
        wrapper.getContents() == null

        where:
        operation  | status | location | message
        'redirect' | 302    | '/next'  | null
        'error'    | 404    | null     | null
        'message'  | 503    | null     | 'Unavailable'
    }

    void 'captured GSP page is returned without reparsing and reset when layout is deactivated'() {
        given:
        def page = new GSPGrailsLayoutPage()
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, page)
        response.setContentType('text/html')
        def wrapper = new GrailsPageResponseWrapper(request, response, parserSelector)
        page.addProperty('title', 'Captured')

        expect:
        wrapper.isGspGrailsLayoutActive()
        wrapper.getPage().is(page)

        when:
        wrapper.deactivateGrailsLayout()

        then:
        !page.isUsed()
        !wrapper.isGspGrailsLayoutActive()
        wrapper.getPage() == null
    }

    void 'include and forward dispatch metadata is delegated through the wrapper'() {
        given:
        def wrapper = new GrailsPageResponseWrapper(request, response, parserSelector)

        when:
        request.getRequestDispatcher('/included').include(request, wrapper)
        request.getRequestDispatcher('/forwarded').forward(request, wrapper)
        wrapper.setStatus(201)

        then:
        response.includedUrls == ['/included']
        response.forwardedUrl == '/forwarded'
        wrapper.status == 201
        response.status == 201
    }
}
