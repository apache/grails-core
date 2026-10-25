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
package org.grails.web.util

import jakarta.servlet.WriteListener
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class IncludeResponseWrapperSpec extends Specification {

    def "captures writer output without modifying the original response"() {
        given:
        def response = new MockHttpServletResponse()
        def wrapper = new IncludeResponseWrapper(response)

        expect:
        wrapper.getContent() == ''

        when:
        def writer = wrapper.getWriter()
        writer.write('included é')
        writer.flush()
        wrapper.flushBuffer()

        then:
        wrapper.getWriter().is(writer)
        wrapper.getContent().toString() == 'included é'
        response.contentAsString == ''
        !wrapper.committed

        when:
        wrapper.getOutputStream()

        then:
        def error = thrown(IllegalStateException)
        error.message == 'Method getWriter() already called'
    }

    def "captures all output stream write overloads and decodes UTF-8"() {
        given:
        def response = new MockHttpServletResponse()
        def wrapper = new IncludeResponseWrapper(response)

        when:
        def stream = wrapper.getOutputStream()
        stream.write('é'.getBytes('UTF-8'))
        stream.write('xabx'.getBytes('UTF-8'), 1, 2)
        stream.write(33)
        stream.setWriteListener(Stub(WriteListener))

        then:
        stream.ready
        wrapper.getOutputStream().is(stream)
        wrapper.getContent() == 'éab!'
        response.contentAsByteArray.length == 0

        when:
        wrapper.getWriter()

        then:
        def error = thrown(IllegalStateException)
        error.message == 'Method getOutputStream() already called'
    }

    def "uses an explicit encoding when reading stream content"() {
        given:
        def wrapper = new IncludeResponseWrapper(new MockHttpServletResponse())
        wrapper.getOutputStream().write('café'.getBytes('ISO-8859-1'))

        expect:
        wrapper.getContent('ISO-8859-1') == 'café'
    }

    def "reset discards writer content while retaining the writer"() {
        given:
        def wrapper = new IncludeResponseWrapper(new MockHttpServletResponse())
        def output = wrapper.getWriter()
        output.write('old')

        when:
        wrapper.resetBuffer()

        then:
        wrapper.getContent().toString() == ''

        when:
        output.write(65)

        then:
        wrapper.getContent().toString() == 'A'

        when:
        wrapper.reset()

        then:
        wrapper.getContent().toString() == ''

        wrapper.getWriter().is(output)
    }

    def "include metadata and errors do not change the original response"() {
        given:
        def response = new MockHttpServletResponse()
        response.setLocale(Locale.US)
        def wrapper = new IncludeResponseWrapper(response)

        when:
        wrapper.setContentType('text/plain')
        wrapper.setStatus(201)
        wrapper.setLocale(Locale.FRANCE)
        wrapper.setContentLength(100)

        then:
        wrapper.contentType == 'text/plain'
        wrapper.status == 201
        response.contentType == null
        response.status == 200
        response.locale == Locale.US
        response.getHeader('Content-Length') == null

        when:
        wrapper.sendError(404, 'missing')

        then:
        wrapper.status == 404
        !wrapper.committed
        response.status == 200

        when:
        wrapper.sendError(500)

        then:
        wrapper.status == 500
        !wrapper.committed
    }

    def "redirect commits the wrapper and forbids resets and errors"() {
        given:
        def response = new MockHttpServletResponse()
        def wrapper = new IncludeResponseWrapper(response)

        when:
        wrapper.sendRedirect('/login')

        then:
        wrapper.committed
        wrapper.redirectURL == '/login'
        response.redirectedUrl == '/login'

        when:
        operation.call(wrapper)

        then:
        def error = thrown(IllegalStateException)
        error.message == 'Response already committed'

        where:
        operation << [
            { IncludeResponseWrapper included -> included.resetBuffer() },
            { IncludeResponseWrapper included -> included.reset() },
            { IncludeResponseWrapper included -> included.sendError(500) },
            { IncludeResponseWrapper included -> included.sendError(500, 'failed') }
        ]
    }
}
