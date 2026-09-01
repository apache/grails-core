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
package org.grails.web.mime

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.context.ApplicationContext
import org.springframework.web.context.support.WebApplicationContextUtils

import grails.core.GrailsApplication
import grails.web.mime.MimeType
import grails.web.mime.MimeUtility
import org.grails.plugins.web.api.MimeTypesApiSupport
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

/**
 *
 * Extends the {@link HttpServletResponse} object with new methods for handling {@link MimeType} instances
 *
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class HttpServletResponseExtension {

    static MimeTypesApiSupport apiSupport = new MimeTypesApiSupport()

    @CompileStatic
    static MimeType[] getMimeTypes() {
        ApplicationContext context = GrailsWebRequest.lookup()?.applicationContext
        MimeUtility mimeUtility = context?.getBeanProvider(MimeUtility)?.getIfAvailable()
        return mimeUtility?.knownMimeTypes as MimeType[] ?: MimeType.createDefaults()
    }

    /**
     * Obtains the format to use for the response using either the file extension or the ACCEPT header
     *
     * @param response The response
     * @return The request format
     */
    @CompileStatic
    static String getFormat(HttpServletResponse response) {

        final webRequest = GrailsWebRequest.lookup()
        HttpServletRequest request = webRequest.getCurrentRequest()
        def result = request.getAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT)
        if (!result) {
            final mimeType = getMimeType(response)
            if (mimeType) {
                result = mimeType.extension
                request.setAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT, result)
            }
        }
        return result
    }

    /**
     * Obtains the MimeType for the response using either the file extension or the ACCEPT header
     *
     * @param response The response
     * @return The MimeType
     */
    @CompileStatic
    static MimeType getMimeType(HttpServletResponse response) {
        final webRequest = GrailsWebRequest.lookup()
        return getMimeTypeForRequest(webRequest)
    }

    private static MimeType getMimeTypeForRequest(GrailsWebRequest webRequest) {
        HttpServletRequest request = webRequest.getCurrentRequest()
        MimeType result = (MimeType) request.getAttribute(GrailsApplicationAttributes.RESPONSE_MIME_TYPE)
        if (!result) {
            def formatOverride = webRequest?.params?.format
            if (!formatOverride) {
                formatOverride = request.getAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT)
            }
            if (formatOverride) {
                def allMimes = getMimeTypes()
                MimeType mime = allMimes?.find { MimeType it -> it.extension == formatOverride }
                result = mime ? mime : allMimes?.find { it }

                // Save the evaluated format as a request attribute.
                // This is a blatant hack because we should to this
                // on the first call. Unfortunately, doing so breaks
                // integration tests:
                //   - Test uses "c.params.format = ..."
                //   - "c.params" creates parameter map
                //   - which triggers the parameter parsing listeners
                //   - which call "request.format"
                //   - which initialises the CONTENT_FORMAT attribute
                //   - *before* the "format" parameter is added to the map
                //   - so the saved format is wrong
                request.setAttribute(GrailsApplicationAttributes.RESPONSE_MIME_TYPE, result)
            } else {
                result = getMimeTypesInternal(request)[0]
            }

        }
        return result
    }

    /**
     * Gets the configured mime types for the response
     *
     * @param response The response
     * @return The configured mime types
     */
    static MimeType[] getMimeTypes(HttpServletResponse response) {
        return getMimeTypesInternal(GrailsWebRequest.lookup().currentRequest)
    }

    /**
     * Gets the configured mime types for the response
     *
     * @param response The response
     * @return The configured mime types
     */
    static MimeType[] getMimeTypesFormatAware(HttpServletResponse response) {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup()
        HttpServletRequest request = webRequest.getCurrentRequest()
        MimeType[] result = (MimeType[]) request.getAttribute(GrailsApplicationAttributes.RESPONSE_MIME_TYPES)
        if (!result) {
            def formatOverride = webRequest?.params?.format
            if (!formatOverride) {
                formatOverride = request.getAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT)
            }
            if (formatOverride) {
                def allMimes = getMimeTypes()
                MimeType mime = allMimes.find { MimeType it -> it.extension == formatOverride }
                result = [ mime ? mime : getMimeTypes()[0] ] as MimeType[]

                // Save the evaluated format as a request attribute.
                // This is a blatant hack because we should to this
                // on the first call. Unfortunately, doing so breaks
                // integration tests:
                //   - Test uses "c.params.format = ..."
                //   - "c.params" creates parameter map
                //   - which triggers the parameter parsing listeners
                //   - which call "request.format"
                //   - which initialises the CONTENT_FORMAT attribute
                //   - *before* the "format" parameter is added to the map
                //   - so the saved format is wrong
                request.setAttribute(GrailsApplicationAttributes.RESPONSE_MIME_TYPES, result)
            } else {
                result = getMimeTypesInternal(request)
            }

        }
        return result
    }

    /**
     * Allows for the response.withFormat { } syntax
     *
     * @param response The response
     * @param callable A closure
     * @return The result of the closure call
     */
    static Object withFormat(HttpServletResponse response, Closure callable) {
        apiSupport.withFormat(response, callable)
    }

    @CompileDynamic
    private static MimeType[] getMimeTypesInternal(HttpServletRequest request) {
        MimeType[] result = (MimeType[]) request.getAttribute(GrailsApplicationAttributes.RESPONSE_FORMATS)
        if (!result) {

            def applicationContext = GrailsWebRequest.lookup()?.applicationContext
            if (applicationContext == null && request.servletContext != null) {
                applicationContext = WebApplicationContextUtils.getWebApplicationContext(request.servletContext)
            }
            // The strategy is not a bean of its own so that Spring Security does not adopt it, so
            // reach it through the configurer that holds it rather than by its own type.
            GrailsContentNegotiationStrategy strategy = applicationContext?.getBeanProvider(
                    GrailsMimeTypesWebMvcConfigurer
            )?.getIfAvailable()?.contentNegotiationStrategy
            if (strategy == null) {
                GrailsApplication application = applicationContext?.getBeanProvider(GrailsApplication)?.getIfAvailable()
                if (application != null) {
                    strategy = new GrailsContentNegotiationStrategy(getMimeTypes(), application.config)
                }
            }
            if (strategy != null) {
                result = strategy.resolveMimeTypes(request)
                request.setAttribute(GrailsApplicationAttributes.RESPONSE_FORMATS, result)
                return result
            }

            def parser = new DefaultAcceptHeaderParser(getMimeTypes())
            String header = request.getHeader(grails.web.http.HttpHeaders.ACCEPT)
            result = parser.parse(header)

            // GRAILS-8341 - If no header the parser would have returned all configured mime types.  Since no format
            // was specified in the request we look for the 'all' format and return that if found.  If 'all' is
            // not found the fallback behavior is to return all configured mime types from the parser.
            if (!header) {
                for (mime in result) {
                    if (mime.extension == 'all') {
                        result = [mime] as MimeType[]
                        break
                    }
                }
            }

            request.setAttribute(GrailsApplicationAttributes.RESPONSE_FORMATS, result)
        }
        return result
    }
}
