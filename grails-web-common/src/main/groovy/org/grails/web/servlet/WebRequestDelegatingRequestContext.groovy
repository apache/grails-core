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

import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.WebApplicationContextUtils

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils

/**
 * Delegates calls to a passed GrailsWebRequest instance.
 *
 * @author Graeme Rocher
 * @since 0.6
 */
@CompileStatic
class WebRequestDelegatingRequestContext implements GrailsRequestContext {

    private GrailsWebRequest webRequest

    WebRequestDelegatingRequestContext() {
        webRequest = (GrailsWebRequest) RequestContextHolder.currentRequestAttributes()
    }

    /**
     * Retrieves the webRequest object.
     * @return The webrequest object
     */
    GrailsWebRequest getWebRequest() {
        return webRequest
    }

    HttpServletRequest getRequest() {
        return webRequest.getCurrentRequest()
    }

    HttpServletResponse getResponse() {
        return webRequest.getCurrentResponse()
    }

    HttpSession getSession() {
        return webRequest.getSession()
    }

    ServletContext getServletContext() {
        return webRequest.getServletContext()
    }

    @SuppressWarnings('rawtypes')
    Map getParams() {
        return webRequest.getParams()
    }

    ApplicationContext getApplicationContext() {
        ServletContext servletContext = getServletContext()
        return WebApplicationContextUtils.getWebApplicationContext(servletContext)
    }

    Writer getOut() {
        return webRequest.getOut()
    }

    String getActionName() {
        return webRequest.getActionName()
    }

    String getControllerName() {
        return webRequest.getControllerName()
    }

    String getRequestURI() {
        HttpServletRequest request = getRequest()
        String uri = (String) request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE)
        if (uri == null) {
            uri = request.getRequestURI()
        }

        return uri
    }

}
