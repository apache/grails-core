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
package org.grails.web.servlet;

import java.io.Writer;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.WebUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Delegates calls to a passed GrailsWebRequest instance.
 *
 * @author Graeme Rocher
 * @since 0.6
 */
public class WebRequestDelegatingRequestContext implements GrailsRequestContext {

    private GrailsWebRequest webRequest;

    public WebRequestDelegatingRequestContext() {
        webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes();
    }

    /**
     * Retrieves the webRequest object.
     * @return The webrequest object
     */
    public GrailsWebRequest getWebRequest() {
        return webRequest;
    }

    public HttpServletRequest getRequest() {
        return webRequest.getCurrentRequest();
    }

    public HttpServletResponse getResponse() {
        return webRequest.getCurrentResponse();
    }

    public HttpSession getSession() {
        return webRequest.getSession();
    }

    public ServletContext getServletContext() {
        return webRequest.getServletContext();
    }

    @SuppressWarnings("rawtypes")
    public Map getParams() {
        return webRequest.getParams();
    }

    public ApplicationContext getApplicationContext() {
        ServletContext servletContext = getServletContext();
        return WebApplicationContextUtils.getWebApplicationContext(servletContext);
    }

    public Writer getOut() {
        return webRequest.getOut();
    }

    public String getActionName() {
        return webRequest.getActionName();
    }

    public String getControllerName() {
        return webRequest.getControllerName();
    }

    public String getRequestURI() {
        HttpServletRequest request = getRequest();
        String uri = (String)request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE);
        if (uri == null) {
            uri = request.getRequestURI();
        }

        return uri;
    }
}
