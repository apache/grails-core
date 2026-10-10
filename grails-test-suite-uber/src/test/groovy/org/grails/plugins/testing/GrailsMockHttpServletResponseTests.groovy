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
package org.grails.plugins.testing

import java.nio.charset.StandardCharsets

import org.grails.plugins.testing.GrailsMockHttpServletResponse
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.junit.jupiter.api.Test
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

import static org.junit.jupiter.api.Assertions.assertArrayEquals
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse

/**
 * Test case for {@link org.grails.plugins.testing.GrailsMockHttpServletResponse}.
 */
class GrailsMockHttpServletResponseTests {
    @Test
    void testResetDoesNotSelectAWriterBeforeBinaryOutput() {
        withBoundWebRequest { response, webRequest ->
            response.reset()

            byte[] data = [0, 1, 127, -1] as byte[]
            response.outputStream.write(data)

            assertArrayEquals data, response.contentAsByteArray
        }
    }

    @Test
    void testResetAllowsBinaryOutputAfterText() {
        withBoundWebRequest { response, webRequest ->
            webRequest.out.write('previous text')
            response.flushBuffer()

            response.reset()
            byte[] data = [0, 1, 127, -1] as byte[]
            response.outputStream.write(data)

            assertArrayEquals data, response.contentAsByteArray
        }
    }

    @Test
    void testResetAllowsTextOutputAfterBinary() {
        withBoundWebRequest { response, webRequest ->
            response.outputStream.write([0, 1, 127, -1] as byte[])
            response.flushBuffer()

            response.reset()
            webRequest.out.write('new text')
            webRequest.out.flush()

            assertEquals 'new text', response.contentAsString
        }
    }

    @Test
    void testResetUsesCharacterEncodingChosenAfterReset() {
        withBoundWebRequest { response, webRequest ->
            webRequest.out.write('previous text')
            response.flushBuffer()

            response.reset()
            response.characterEncoding = 'UTF-8'
            String text = 'café €'
            webRequest.out.write(text)
            webRequest.out.flush()

            assertArrayEquals text.getBytes(StandardCharsets.UTF_8), response.contentAsByteArray
        }
    }

    @Test
    void testResetReplacesACachedWebRequestWriterLazily() {
        withBoundWebRequest { response, webRequest ->
            StringWriter previousWriter = new StringWriter()
            previousWriter.write('previous text')
            webRequest.out = previousWriter

            response.reset()
            webRequest.out.write('new text')
            webRequest.out.flush()

            assertEquals 'new text', response.contentAsString
            assertEquals 'previous text', previousWriter.toString()
        }
    }

    private void withBoundWebRequest(Closure assertions) {
        RequestAttributes previous = RequestContextHolder.getRequestAttributes()
        def request = new GrailsMockHttpServletRequest()
        def response = new GrailsMockHttpServletResponse()
        def webRequest = new GrailsWebRequest(request, response, request.servletContext)
        RequestContextHolder.setRequestAttributes(webRequest)
        try {
            assertions.call(response, webRequest)
        }
        finally {
            RequestContextHolder.setRequestAttributes(previous)
        }
    }

    /**
     * Tests that the left-shift operator appends the given text to the
     * response output.
     */
    @Test
    void testLeftShift() {
        def testResponse = new GrailsMockHttpServletResponse()
        assertEquals "", testResponse.contentAsString

        testResponse << "Some string or other"
        assertEquals "Some string or other", testResponse.contentAsString

        testResponse << "\nand another line"
        assertEquals "Some string or other\nand another line", testResponse.contentAsString
    }

    /**
     * The body is the controller's own output, so a DOCTYPE it renders is accepted. The DTD the
     * declaration names is never retrieved.
     */
    @Test
    void testXmlAcceptsDoctypeInRenderedOutput() {
        def testResponse = new GrailsMockHttpServletResponse()
        testResponse << '''<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html><body><p>hello</p></body></html>'''

        assertEquals 'hello', testResponse.xml.body.p.text()
    }

    @Test
    void testXmlDoesNotResolveExternalEntities() {
        File secret = File.createTempFile('mock-response-secret', '.txt')
        try {
            secret.text = 'top-secret-token'
            def testResponse = new GrailsMockHttpServletResponse()
            testResponse << """<!DOCTYPE root [
<!ENTITY ext SYSTEM '${secret.toURI().toASCIIString()}'>
]>
<root>&ext;</root>"""

            assertFalse testResponse.xml.text().contains('top-secret-token')
        }
        finally {
            secret.delete()
        }
    }
}
