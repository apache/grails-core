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

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.spring.BeanBuilder
import grails.web.mime.MimeType
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.web.mime.MimeTypesConfiguration
import org.grails.support.MockApplicationContext
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import static org.junit.jupiter.api.Assertions.*

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class WebUtilsTests {

    def config

    @BeforeEach
    void setUp() {
        RequestContextHolder.resetRequestAttributes()
        config = new ConfigSlurper().parse("""
grails.mime.file.extensions=false
grails.mime.types = [ html: ['text/html','application/xhtml+xml'],
                      xml: ['text/xml', 'application/xml'],
                      text: 'text-plain',
                      js: 'text/javascript',
                      rss: 'application/rss+xml',
                      atom: 'application/atom+xml',
                      css: 'text/css',
                      csv: 'text/csv',
                      all: '*/*',
                      json: ['application/json','text/json'],
                      form: 'application/x-www-form-urlencoded',
                      multipartForm: 'multipart/form-data'
                    ]        """)
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    void testAreFileExtensionsEnabled() {
        def ga = new DefaultGrailsApplication(config:new PropertySourcesConfig().merge(config))
        bindMockRequest(ga)

        assertFalse WebUtils.areFileExtensionsEnabled()

        config = new ConfigSlurper().parse("""
grails.mime.file.extensions=true
       """)

        ga.config = new PropertySourcesConfig().merge(config)

        assertTrue WebUtils.areFileExtensionsEnabled()
    }

    private def bindMockRequest(DefaultGrailsApplication ga) {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, ga)

        final def mainContext = new GenericApplicationContext()
        mainContext.refresh()
        ga.setApplicationContext(mainContext)

        def bb = new BeanBuilder()
        bb.beans {
            grailsApplication = ga
            mimeConfiguration(MimeTypesConfiguration, ga, [])
        }
        final ApplicationContext context = bb.createApplicationContext()
        final MimeTypesConfiguration mimeTypesConfiguration = context.getBean(MimeTypesConfiguration)
        ctx.registerMockBean(MimeType.BEAN_NAME, mimeTypesConfiguration.mimeTypes())
        def servletContext = new MockServletContext()
        servletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, ctx)
        def webRequest = new GrailsWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse(), servletContext)
        RequestContextHolder.setRequestAttributes(webRequest)
    }

    @Test
    void testGetFormatFromURI() {
        def ga = new DefaultGrailsApplication(config:new PropertySourcesConfig().merge(config))
        bindMockRequest(ga)

        assertNull WebUtils.getFormatFromURI("/foo/bar/")
        assertNull WebUtils.getFormatFromURI("/foo/bar")
        assertNull WebUtils.getFormatFromURI("/foo/bar.")
        assertNull WebUtils.getFormatFromURI("/foo/bar.suff/bar")
        assertNull WebUtils.getFormatFromURI("/foo/bar.suff/bar.")
        assertEquals "xml", WebUtils.getFormatFromURI("/foo/bar.xml")
        assertEquals "xml", WebUtils.getFormatFromURI("/foo.xml")
        assertEquals "xml", WebUtils.getFormatFromURI("/foo/bar.suff/bar.xml")
    }

    @Test
    void testGetRequestURIForGrailsDispatchURI() {
        def request = new MockHttpServletRequest()
        request.contextPath = "/root"
        request.requestURI = "/root/example/index.dispatch"

        assertEquals "/example/index",WebUtils.getRequestURIForGrailsDispatchURI(request)

        request.requestURI = "/root/example/index"

        assertEquals "/example/index",WebUtils.getRequestURIForGrailsDispatchURI(request)
    }

    @Test
    void testRetrieveGrailsWebRequest() {
        // Validate the initial conditions.
        assertNull RequestContextHolder.getRequestAttributes()

        // An exception should be thrown if no web request is attached
        // to the thread.
        assertThrows(IllegalStateException) {
            WebUtils.retrieveGrailsWebRequest()
        }

        // Now check that the method returns the web request stored in
        // RequestContextHolder.
        def mockWebRequest = new GrailsWebRequest(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new MockServletContext())
        RequestContextHolder.setRequestAttributes(mockWebRequest)
        assertEquals mockWebRequest, WebUtils.retrieveGrailsWebRequest()
    }

    @Test
    void testStoreGrailsWebRequest() {
        // Validate the initial conditions.
        assertNull RequestContextHolder.getRequestAttributes()

        // Create a mock web request and pass it to the method under
        // test.
        def mockHttpRequest = new MockHttpServletRequest()
        def mockWebRequest = new GrailsWebRequest(
                mockHttpRequest,
                new MockHttpServletResponse(),
                new MockServletContext())

        WebUtils.storeGrailsWebRequest(mockWebRequest)

        // Check that both the RequestContextHolder and the HTTP request
        // attribute have been initialised.
        assertEquals mockWebRequest, RequestContextHolder.getRequestAttributes()
        assertEquals mockWebRequest, mockHttpRequest.getAttribute(GrailsApplicationAttributes.WEB_REQUEST)
    }

    @Test
    void clearGrailsWebRequest() {
        // Create a mock web request and store it on the thread.
        def mockHttpRequest = new MockHttpServletRequest()
        def mockWebRequest = new GrailsWebRequest(
                mockHttpRequest,
                new MockHttpServletResponse(),
                new MockServletContext())

        WebUtils.storeGrailsWebRequest(mockWebRequest)

        // Now call the test method and check that the web request has
        // been removed from the thread.
        WebUtils.clearGrailsWebRequest()
        assertNull RequestContextHolder.getRequestAttributes()
        assertNull mockHttpRequest.getAttribute(GrailsApplicationAttributes.WEB_REQUEST)

        // Finally check that we can call the method again without any
        // undesirable side-effects, like an exception.
        WebUtils.clearGrailsWebRequest()
        assertNull RequestContextHolder.getRequestAttributes()
        assertNull mockHttpRequest.getAttribute(GrailsApplicationAttributes.WEB_REQUEST)
    }
}
