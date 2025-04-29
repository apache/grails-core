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
package org.grails.web.servlet

import org.grails.buffer.StreamCharBuffer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession

import static org.grails.web.util.GrailsApplicationAttributes.FLASH_SCOPE
import static org.junit.jupiter.api.Assertions.*

class DefaultGrailsApplicationAttributesTests {

    def grailsApplicationAttributes
    def request

    @BeforeEach
    void setUp() {
        grailsApplicationAttributes = new DefaultGrailsApplicationAttributes(null)
        def controller = new Expando()
        controller.controllerUri = '/mycontroller'
        controller.controllerName = 'mycontroller'
        request = new MockHttpServletRequest()
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller)
    }

    @Test
    void testGetTemplateUriWithNestedRelativePath() {
        def templateUri = grailsApplicationAttributes.getTemplateUri('one/two/three', request)
        assertEquals '/mycontroller/one/two/_three.gsp', templateUri, 'wrong template uri'
    }

    @Test
    void testGetTemplateUriWithRelativePath() {
        def templateUri = grailsApplicationAttributes.getTemplateUri('bar', request)
        assertEquals '/mycontroller/_bar.gsp', templateUri, 'wrong template uri'
    }

    @Test
    void testGetTemplateUriWithStreamCharBufferRepresentingRelativePath() {
        def scb = new StreamCharBuffer()
        scb.writer.write('bar')
        def templateUri = grailsApplicationAttributes.getTemplateUri(scb, request)
        assertEquals '/mycontroller/_bar.gsp', templateUri, 'wrong template uri'
    }

    @Test
    void testGetTemplateUriWithAbsoluteNestedPath() {
        def templateUri = grailsApplicationAttributes.getTemplateUri('/uno/dos/tres', request)
        assertEquals '/uno/dos/_tres.gsp', templateUri, 'wrong template uri'
    }

    @Test
    void testGetTemplateUriWithStreamCharBufferRepresentingAbsoluteNestedPath() {
        def scb = new StreamCharBuffer()
        scb.writer.write('/uno/dos/tres')
        def templateUri = grailsApplicationAttributes.getTemplateUri(scb, request)
        assertEquals '/uno/dos/_tres.gsp', templateUri, 'wrong template uri'
    }

    @Test
    void testGetTemplateUriWithAbsolutePath() {
        def templateUri = grailsApplicationAttributes.getTemplateUri('/mytemplate', request)
        assertEquals '/_mytemplate.gsp', templateUri, 'wrong template uri'
    }

    @Test
    void testGetTemplateUriWithStreamCharBufferRepresentingAbsolutePath() {
        def scb = new StreamCharBuffer()
        scb.writer.write('/mytemplate')
        def templateUri = grailsApplicationAttributes.getTemplateUri(scb, request)
        assertEquals '/_mytemplate.gsp', templateUri, 'wrong template uri'
    }

    @Test
    void testGrailsFlashScope() {
        RequestContextHolder.setRequestAttributes new GrailsWebRequest(
                (MockHttpServletRequest) request, new MockHttpServletResponse(), new MockServletContext())

        // when
        request.session = new MockHttpSession()

        GrailsFlashScope flash = grailsApplicationAttributes.getFlashScope(request)
        HttpSession session = ((HttpServletRequest) request).getSession(false)

        // then
        assertNotNull(session)
        assertTrue(session.getAttribute(FLASH_SCOPE) instanceof GrailsFlashScope)
        assertTrue(((GrailsFlashScope)session.getAttribute(FLASH_SCOPE)).isEmpty())

        // when
        flash.put('foo', 'bar')

        // then
        assertNull(((HttpServletRequest) request).getAttribute(FLASH_SCOPE))
        assertEquals("bar", session.getAttribute(FLASH_SCOPE).get("foo"))
    }
}
