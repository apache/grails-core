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

package org.grails.plugins.web.controllers

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse

import org.springframework.mock.web.MockHttpServletResponse

import spock.lang.Specification

/**
 * Drives {@link SecurityHeadersResponseWrapper} directly through the servlet response API, the
 * way a downstream filter or servlet does, to prove the before-commit callback fires on every
 * way the response can commit and only once.
 */
class SecurityHeadersResponseWrapperSpec extends Specification {

    int fired = 0

    private SecurityHeadersResponseWrapper wrap(HttpServletResponse response) {
        return new SecurityHeadersResponseWrapper(response, { -> fired++ } as Runnable)
    }

    void 'every redirect overload runs the callback once before delegating'() {
        given:
        HttpServletResponse delegate = Mock()
        def wrapper = wrap(delegate)

        when:
        wrapper.sendRedirect('/a', 301)
        wrapper.sendRedirect('/b', true)
        wrapper.sendRedirect('/c', 302, false)

        then:
        1 * delegate.sendRedirect('/a', 301)
        1 * delegate.sendRedirect('/b', true)
        1 * delegate.sendRedirect('/c', 302, false)
        fired == 1
    }

    void 'the callback runs again after a reset re-arms the wrapper'() {
        given:
        HttpServletResponse delegate = Mock()
        def wrapper = wrap(delegate)

        when:
        wrapper.flushBuffer()
        wrapper.beforeCommit()

        then:
        fired == 1

        when:
        wrapper.reset()
        wrapper.beforeCommit()

        then:
        fired == 2
    }

    void 'a header other than Content-Length does not count toward the declared length'() {
        given:
        def response = new MockHttpServletResponse()
        def wrapper = wrap(response)

        when:
        wrapper.setHeader('X-Other', '0')
        wrapper.addHeader('X-Other', '0')
        wrapper.setIntHeader('X-Other', 0)
        wrapper.addIntHeader('X-Other', 0)
        wrapper.setHeader('Content-Length', null)
        wrapper.outputStream.write(1)

        then:
        fired == 0
        response.getHeader('X-Other') == '0'
    }

    void 'a Content-Length declared through #description commits once that many bytes are written'() {
        given:
        def wrapper = wrap(new MockHttpServletResponse())
        declare.call(wrapper)

        when:
        wrapper.outputStream.write(1)

        then:
        fired == 0

        when:
        wrapper.outputStream.write(2)

        then:
        fired == 1

        where:
        description              | declare
        'addIntHeader'           | { SecurityHeadersResponseWrapper w -> w.addIntHeader('content-length', 2) }
        'setIntHeader'           | { SecurityHeadersResponseWrapper w -> w.setIntHeader('Content-Length', 2) }
        'setContentLengthLong'   | { SecurityHeadersResponseWrapper w -> w.setContentLengthLong(2L) }
        'a string header' | { SecurityHeadersResponseWrapper w -> w.addHeader('Content-Length', '2') }
    }

    void 'the output stream counts single bytes, byte arrays and array slices'() {
        given:
        def response = new MockHttpServletResponse()
        def wrapper = wrap(response)
        wrapper.setContentLength(6)

        when:
        wrapper.outputStream.write(1)
        wrapper.outputStream.write([2, 3] as byte[])

        then:
        fired == 0

        when:
        wrapper.outputStream.write([4, 5, 6, 7] as byte[], 1, 3)

        then:
        fired == 1
        response.contentAsByteArray == [1, 2, 3, 5, 6, 7] as byte[]
    }

    void 'the output stream is created once and flushing it commits the response'() {
        given:
        def wrapper = wrap(new MockHttpServletResponse())

        expect:
        wrapper.outputStream.is(wrapper.outputStream)

        when:
        wrapper.outputStream.flush()
        wrapper.outputStream.flush()

        then:
        fired == 1
    }

    void 'the output stream delegates readiness and write listeners'() {
        given:
        ServletOutputStream stream = Mock()
        HttpServletResponse delegate = Mock()
        delegate.getOutputStream() >> stream
        def wrapper = wrap(delegate)
        WriteListener listener = Mock()

        when:
        boolean ready = wrapper.outputStream.isReady()
        wrapper.outputStream.setWriteListener(listener)

        then:
        1 * stream.isReady() >> true
        1 * stream.setWriteListener(listener)
        ready
        fired == 0
    }

    void 'a response that reports no buffer never commits for being full'() {
        given:
        HttpServletResponse delegate = Mock()
        delegate.getOutputStream() >> Mock(ServletOutputStream)
        delegate.getBufferSize() >> 0
        def wrapper = wrap(delegate)

        when:
        wrapper.outputStream.write(new byte[10000])

        then:
        fired == 0
    }

    void 'a character written through the writer counts #expectedBytes bytes in #encoding'() {
        given:
        def response = new MockHttpServletResponse()
        response.characterEncoding = encoding
        def wrapper = wrap(response)
        wrapper.setContentLength(expectedBytes)

        when:
        wrapper.writer.write(character)

        then:
        fired == 1

        where:
        encoding     | character | expectedBytes
        'UTF-8'      | 0xE9      | 2
        'UTF-16'     | 0x61      | 4
        'ISO-8859-1' | 0x61      | 1
    }

    void 'characters written after the response committed are no longer counted'() {
        given:
        def wrapper = wrap(new MockHttpServletResponse())
        wrapper.setContentLength(1)
        wrapper.writer.write(0x61)

        when:
        wrapper.writer.write(0x62)

        then:
        fired == 1
    }

    void 'the writer is created once and flushing it commits the response'() {
        given:
        def wrapper = wrap(new MockHttpServletResponse())

        expect:
        wrapper.writer.is(wrapper.writer)

        when:
        wrapper.writer.flush()

        then:
        fired == 1
    }

    void 'the writer assumes UTF-8 when the response has #description'() {
        given:
        HttpServletResponse delegate = Mock()
        delegate.getWriter() >> new PrintWriter(new StringWriter())
        delegate.getCharacterEncoding() >> encoding
        def wrapper = wrap(delegate)
        wrapper.setContentLength(2)

        when:
        wrapper.writer.write(0xE9)

        then:
        fired == 1

        where:
        description                        | encoding
        'no character encoding'            | null
        'an unknown character encoding'    | 'no-such-charset'
        'a decode-only character encoding' | 'ISO-2022-CN'
    }
}
