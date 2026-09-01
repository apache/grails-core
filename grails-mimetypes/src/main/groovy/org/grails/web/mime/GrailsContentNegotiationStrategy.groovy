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

import java.util.regex.Pattern

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import org.springframework.http.MediaType
import org.springframework.web.accept.ContentNegotiationStrategy
import org.springframework.web.context.request.NativeWebRequest

import grails.config.Config
import grails.config.Settings
import grails.web.http.HttpHeaders
import grails.web.mime.MimeType
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Adapts Grails' configured format aliases and compatibility rules to Spring MVC content negotiation.
 */
@CompileStatic
class GrailsContentNegotiationStrategy implements ContentNegotiationStrategy {

    private final MimeType[] mimeTypes
    private final boolean useAcceptHeader
    private final boolean useAcceptHeaderXhr
    private final Pattern disableForUserAgents

    GrailsContentNegotiationStrategy(MimeType[] mimeTypes, Config config) {
        this.mimeTypes = mimeTypes
        this.useAcceptHeader = config.getProperty(Settings.MIME_USE_ACCEPT_HEADER, Boolean, true)
        this.useAcceptHeaderXhr = !config.getProperty(
                Settings.MIME_DISABLE_ACCEPT_HEADER_FOR_USER_AGENTS_XHR,
                Boolean,
                false
        )
        this.disableForUserAgents = createDisabledUserAgentPattern(config)
    }

    @Override
    List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest)
        if (request == null) {
            return [MediaType.ALL]
        }
        return resolveMimeTypes(request).collect { MimeType mimeType ->
            SpringMediaTypeAdapter.toMediaType(mimeType)
        }
    }

    /**
     * @return the MIME types this strategy resolves against, as configured by grails.mime.types
     */
    MimeType[] getConfiguredMimeTypes() {
        return mimeTypes
    }

    MimeType[] resolveMimeTypes(HttpServletRequest request) {
        String formatOverride = request.getParameter('format')
        if (!formatOverride) {
            formatOverride = request.getAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT) as String
        }
        if (formatOverride) {
            MimeType mimeType = mimeTypes.find { MimeType candidate -> candidate.extension == formatOverride }
            return [mimeType ?: mimeTypes[0]] as MimeType[]
        }

        String acceptHeader = resolveAcceptHeader(request)
        MimeType[] resolved = new DefaultAcceptHeaderParser(mimeTypes).parse(acceptHeader)
        if (!acceptHeader) {
            MimeType allMimeType = resolved.find { MimeType mimeType -> mimeType.extension == 'all' }
            if (allMimeType != null) {
                return [allMimeType] as MimeType[]
            }
        }
        return resolved
    }

    private String resolveAcceptHeader(HttpServletRequest request) {
        if (!useAcceptHeader) {
            return null
        }

        String userAgent = request.getHeader(HttpHeaders.USER_AGENT)
        boolean xhr = request.getHeader('X-Requested-With')?.equalsIgnoreCase('XMLHttpRequest')
        boolean disabledForUserAgent = !(useAcceptHeaderXhr && xhr) &&
                disableForUserAgents != null &&
                userAgent != null &&
                disableForUserAgents.matcher(userAgent).find()
        return disabledForUserAgent ? null : request.getHeader(HttpHeaders.ACCEPT)
    }

    private static Pattern createDisabledUserAgentPattern(Config config) {
        Object configured = config.getProperty(Settings.MIME_DISABLE_ACCEPT_HEADER_FOR_USER_AGENTS, Object)
        if (configured instanceof Pattern) {
            return (Pattern) configured
        }
        if (configured instanceof Collection && configured) {
            String userAgents = ((Collection) configured).join('(?i)|')
            return Pattern.compile("(${userAgents})")
        }
        return null
    }
}
