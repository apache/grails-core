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
package grails.util

import org.grails.web.util.GrailsApplicationAttributes
import groovy.transform.CompileStatic

import jakarta.servlet.ServletContext

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.mvc.ParameterCreationListener
import org.springframework.context.ApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder

/**
 *
 * @author Jeff Brown
 * @since 3.0
 *
 */
@CompileStatic
class GrailsWebMockUtil {
    
    /**
     * Binds a Mock implementation of a GrailsWebRequest object to the current thread. The mock version uses
     * instances of the Spring MockHttpServletRequest, MockHttpServletResponse and MockServletContext classes.
     *
     * @param ctx The WebApplicationContext to use
     *
     * @see org.springframework.mock.web.MockHttpServletRequest
     * @see org.springframework.mock.web.MockHttpServletResponse
     * @see org.springframework.mock.web.MockServletContext
     *
     * @return The GrailsWebRequest instance
     */
    static GrailsWebRequest bindMockWebRequest(WebApplicationContext ctx) {
        def servletContext = ctx.getServletContext()
        if(servletContext == null) {

        }
        bindMockWebRequest(ctx, new MockHttpServletRequest(servletContext), new MockHttpServletResponse())
    }

    /**
     * Binds a Mock implementation of a GrailsWebRequest object to the current thread. The mock version uses
     * instances of the Spring MockHttpServletRequest, MockHttpServletResponse and MockServletContext classes.
     *
     * @param ctx The WebApplicationContext to use
     * @param request The request
     * @param response The response
     *
     * @see org.springframework.mock.web.MockHttpServletRequest
     * @see org.springframework.mock.web.MockHttpServletResponse
     * @see org.springframework.mock.web.MockServletContext
     *
     * @return The GrailsWebRequest instance
     */
    static GrailsWebRequest bindMockWebRequest(ApplicationContext ctx, MockHttpServletRequest request, MockHttpServletResponse response) {
        ServletContext servletContext = ctx instanceof WebApplicationContext && ((WebApplicationContext)ctx).getServletContext() != null ? ((WebApplicationContext)ctx).getServletContext() : request.getServletContext()
        GrailsWebRequest webRequest = new GrailsWebRequest(request, response, servletContext, ctx)
        request.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, webRequest)
        for (ParameterCreationListener listener: ctx.getBeansOfType(ParameterCreationListener).values()) {
            webRequest.addParameterListener(listener)
        }
        RequestContextHolder.setRequestAttributes(webRequest)
        webRequest
    }

    /**
     * Binds a Mock implementation of a GrailsWebRequest object to the current thread. The mock version uses
     * instances of the Spring MockHttpServletRequest, MockHttpServletResponse and MockServletContext classes.
     *
     * @see org.springframework.mock.web.MockHttpServletRequest
     * @see org.springframework.mock.web.MockHttpServletResponse
     * @see org.springframework.mock.web.MockServletContext
     *
     * @return The GrailsWebRequest instance
     */
    static GrailsWebRequest bindMockWebRequest() {
        ServletContext servletContext = new MockServletContext()
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext)
        MockHttpServletResponse response = new MockHttpServletResponse()
        bindMockWebRequest(servletContext, request, response)
    }

    static GrailsWebRequest bindMockWebRequest(ServletContext servletContext, MockHttpServletRequest request, MockHttpServletResponse response) {
        GrailsWebRequest webRequest = new GrailsWebRequest(request,
                                                response, servletContext);
        request.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, webRequest)
        RequestContextHolder.setRequestAttributes(webRequest)
        webRequest
    }

}
