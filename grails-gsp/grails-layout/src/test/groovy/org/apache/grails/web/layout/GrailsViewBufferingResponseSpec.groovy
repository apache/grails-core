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

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class GrailsViewBufferingResponseSpec extends Specification {

    void 'view response buffers HTML written as characters or bytes and builds Content'() {
        given:
        def request = new MockHttpServletRequest()
        def target = new MockHttpServletResponse()
        target.setContentType('text/html;charset=UTF-8')
        def response = new GrailsViewBufferingResponse(request, target)
        def html = '<html><head><title>View</title></head><body>caf\u00e9</body></html>'

        when:
        if (usingStream) {
            response.getOutputStream().write(html.getBytes('UTF-8'))
        } else {
            response.getWriter().write(html)
        }
        def content = response.getContent()
        def body = new StringWriter()
        content.writeBody(body)

        then:
        response.getTargetResponse().is(target)
        response.isActive()
        response.isUsingStream() == usingStream
        content.title == 'View'
        body.toString() == 'caf\u00e9'
        target.contentAsByteArray.length == 0

        where:
        usingStream << [false, true]
    }

    void 'non HTML responses pass through without producing Content'() {
        given:
        def target = new MockHttpServletResponse()
        def response = new GrailsViewBufferingResponse(new MockHttpServletRequest(), target)

        when:
        response.setContentType('application/json')
        response.getWriter().write('{"value":1}')
        response.getWriter().flush()

        then:
        !response.isActive()
        response.getContent() == null
        target.contentAsString == '{"value":1}'
    }

    void 'empty HTML writer has no Content to decorate'() {
        given:
        def response = new GrailsViewBufferingResponse(new MockHttpServletRequest(), new MockHttpServletResponse())
        response.setContentType('text/html')
        response.getWriter().write('')

        expect:
        response.isActive()
        response.getContent() == null
    }

    void 'GSP capture is reused and response mutator deactivation stops buffering'() {
        given:
        def request = new MockHttpServletRequest()
        def page = new GSPGrailsLayoutPage()
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, page)
        def target = new MockHttpServletResponse()
        def response = new GrailsViewBufferingResponse(request, target)
        response.setContentType('text/html')
        page.addProperty('title', 'Captured')

        expect:
        response.getContent().is(page)

        when:
        response.deactivateResponseMutator()
        response.getWriter().write('passthrough')
        response.getWriter().flush()

        then:
        !response.isActive()
        !page.isUsed()
        response.getContent() == null
        target.contentAsString == 'passthrough'
    }

    void 'sendError after redirect does not replace the redirect status'() {
        given:
        def target = new MockHttpServletResponse()
        def response = new GrailsViewBufferingResponse(new MockHttpServletRequest(), target)

        when:
        response.sendRedirect('/next')
        response.sendError(500)
        response.sendError(503, 'Unavailable')

        then:
        target.status == 302
        target.redirectedUrl == '/next'
        target.errorMessage == null
    }

    void 'sendError is skipped once the target response is committed'() {
        given:
        def target = new MockHttpServletResponse()
        target.setCommitted(true)
        def response = new GrailsViewBufferingResponse(new MockHttpServletRequest(), target)

        when:
        response.sendError(500)
        response.sendError(503, 'Unavailable')

        then:
        target.status == 200
        target.errorMessage == null
    }

    void 'sendError is skipped because the buffering response always reports itself committed'() {
        given: 'the response says it is committed so that JSP views are included rather than forwarded'
        def target = new MockHttpServletResponse()
        def response = new GrailsViewBufferingResponse(new MockHttpServletRequest(), target)

        expect:
        response.committed
        !target.committed

        when:
        if (message == null) {
            response.sendError(404)
        } else {
            response.sendError(404, message)
        }

        then:
        target.status == 200
        target.errorMessage == null
        !target.committed

        where:
        message << [null, 'Missing']
    }
}
