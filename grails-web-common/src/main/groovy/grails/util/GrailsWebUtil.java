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
package grails.util;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import groovy.lang.GroovyObject;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.support.WebApplicationContextUtils;

import grails.core.ApplicationAttributes;
import grails.core.GrailsApplication;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.GrailsApplicationAttributes;

/**
 * Utility methods for clients using the web framework.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@SuppressWarnings("rawtypes")
public class GrailsWebUtil {

    public static final String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();
    private static final String CHARSET_ATTRIBUTE = ";charset=";
    private static final Pattern CHARSET_IN_CONTENT_TYPE_REGEXP = Pattern.compile(";\\s*charset\\s*=", Pattern.CASE_INSENSITIVE);

    private GrailsWebUtil() {
    }

    /**
     * Looks up a GrailsApplication instance from the ServletContext.
     *
     * @param servletContext The ServletContext
     * @return A GrailsApplication or null if there isn't one
     */
    public static GrailsApplication lookupApplication(ServletContext servletContext) {
        if (servletContext == null) {
            return null;
        }

        GrailsApplication grailsApplication = (GrailsApplication)servletContext.getAttribute(ApplicationAttributes.APPLICATION);
        if(grailsApplication != null) {
            return grailsApplication;
        }

        final WebApplicationContext context = WebApplicationContextUtils.getWebApplicationContext(servletContext);
        if (context == null || !context.containsBean(GrailsApplication.APPLICATION_ID)) {
            return null;
        }

        return context.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication.class);
    }

    /**
     * @return The currently bound GrailsApplication instance
     * @since 2.0
     */
    public static GrailsApplication currentApplication() {
        final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof GrailsWebRequest)) {
            return null;
        }

        return ((GrailsWebRequest)requestAttributes).getAttributes().getGrailsApplication();
    }

    /**
     * @return The currently bound GrailsApplication instance
     * @since 2.0
     */
    public static Map currentConfiguration() {
        GrailsApplication application = currentApplication();
        return application == null ? Collections.emptyMap() : application.getConfig();
    }

    /**
     * Retrieves the URI from the request from either the include attribute or the request.getRequestURI() method.
     *
     * @param request The HttpServletRequest instance
     * @return The String URI
     */
    public static String getUriFromRequest(HttpServletRequest request) {
        Object includeUri = request.getAttribute("jakarta.servlet.include.request_uri");
        return includeUri == null ? request.getRequestURI() : (String)includeUri;
    }

    /**
     * Obtains the currently executing controller from the given request if any.
     * @param request The request object
     * @return The controller or null
     */
    public static GroovyObject getControllerFromRequest(HttpServletRequest request) {
        return (GroovyObject)request.getAttribute(GrailsApplicationAttributes.CONTROLLER);
    }

    public static String getContentType(String name, String encoding) {
        if (name.indexOf(';') > -1 && CHARSET_IN_CONTENT_TYPE_REGEXP.matcher(name).find()) {
            return name;
        }
        if (GrailsStringUtils.isBlank(encoding)) encoding = DEFAULT_ENCODING;
        return name + CHARSET_ATTRIBUTE + encoding;
    }
}
