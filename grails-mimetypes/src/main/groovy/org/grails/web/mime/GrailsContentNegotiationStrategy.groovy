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

import grails.config.Config
import grails.config.Settings
import grails.web.http.HttpHeaders
import grails.web.mime.MimeType

/**
 * Resolves the MIME types of a request's {@code Accept} header under the {@code grails.mime} rules.
 * It is deliberately not a Spring {@code ContentNegotiationStrategy}: Spring MVC keeps its own
 * negotiation, and Spring Security adopts any such bean it finds.
 */
@CompileStatic
class GrailsContentNegotiationStrategy {

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

    /**
     * @return the MIME types this strategy resolves against, as configured by grails.mime.types
     */
    MimeType[] getConfiguredMimeTypes() {
        return mimeTypes
    }

    /**
     * @return the MIME types of the {@code Accept} header under the {@code grails.mime} rules, without
     * any {@code format} applied. This is what {@code response.mimeTypes}, and so {@code withFormat}
     * when the format has no block of its own, resolves.
     */
    MimeType[] resolveAcceptedMimeTypes(HttpServletRequest request) {
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
        boolean xhr = request.getHeader('X-Requested-With') == 'XMLHttpRequest'
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
