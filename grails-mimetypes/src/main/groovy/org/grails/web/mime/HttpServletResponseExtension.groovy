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

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.context.ApplicationContext

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
        HttpServletRequest request = webRequest.getRequest()
        def result = request.getAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT)
        if (!result) {
            final mimeType = getMimeType(response)
            if (mimeType) {
                result = mimeType.extension
                // A wildcard such as text/*, which grails.mime.negotiation.spring resolves to a type of the all format,
                // is not saved as the format: withFormat and respond answer it with the formats compatible with it, and
                // the all format the saved format stands for would answer with any
                if (!(mimeType.extension == 'all' && mimeType.name != MimeType.ALL.name &&
                        GrailsContentNegotiationStrategy.forRequest(request).springNegotiation)) {
                    request.setAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT, result)
                }
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
        HttpServletRequest request = webRequest.getRequest()
        MimeType result = (MimeType) request.getAttribute(GrailsApplicationAttributes.RESPONSE_MIME_TYPE)
        if (!result) {
            GrailsContentNegotiationStrategy strategy = GrailsContentNegotiationStrategy.forRequest(request)
            def formatOverride = strategy.requestedFormat(webRequest)
            if (!formatOverride) {
                formatOverride = request.getAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT)
            }
            if (formatOverride) {
                // the configured type of the format, or, for a format that is not configured, the first configured
                // type, or, with grails.mime.negotiation.spring, none
                result = strategy.resolveFormat(formatOverride)

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
                // none where the request accepts no configured type, as with grails.mime.negotiation.spring
                MimeType[] accepted = getMimeTypesInternal(request)
                result = accepted ? accepted[0] : null
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
        return getMimeTypesInternal(GrailsWebRequest.lookup().request)
    }

    /**
     * Gets the configured mime types for the response
     *
     * @param response The response
     * @return The configured mime types
     */
    static MimeType[] getMimeTypesFormatAware(HttpServletResponse response) {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup()
        HttpServletRequest request = webRequest.getRequest()
        MimeType[] result = (MimeType[]) request.getAttribute(GrailsApplicationAttributes.RESPONSE_MIME_TYPES)
        if (result == null) {
            GrailsContentNegotiationStrategy strategy = GrailsContentNegotiationStrategy.forRequest(request)
            def formatOverride = strategy.requestedFormat(webRequest)
            if (!formatOverride) {
                formatOverride = request.getAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT)
            }
            if (formatOverride) {
                // with grails.mime.negotiation.spring, none for a format that is not configured, which the request
                // accepts no configured type in
                MimeType mime = strategy.resolveFormat(formatOverride)
                result = mime != null ? [mime] as MimeType[] : new MimeType[0]

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

    private static MimeType[] getMimeTypesInternal(HttpServletRequest request) {
        MimeType[] result = (MimeType[]) request.getAttribute(GrailsApplicationAttributes.RESPONSE_FORMATS)
        if (result == null) {
            result = GrailsContentNegotiationStrategy.forRequest(request).resolveAcceptedMimeTypes(request)
            request.setAttribute(GrailsApplicationAttributes.RESPONSE_FORMATS, result)
        }
        return result
    }
}
