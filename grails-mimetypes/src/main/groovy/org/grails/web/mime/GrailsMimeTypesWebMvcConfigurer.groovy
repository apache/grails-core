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

import grails.web.mime.MimeType

import org.springframework.http.MediaType
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Installs Grails format negotiation as the Spring MVC content negotiation strategy.
 */
@CompileStatic
class GrailsMimeTypesWebMvcConfigurer implements WebMvcConfigurer {

    private final GrailsContentNegotiationStrategy contentNegotiationStrategy

    GrailsMimeTypesWebMvcConfigurer(GrailsContentNegotiationStrategy contentNegotiationStrategy) {
        this.contentNegotiationStrategy = contentNegotiationStrategy
    }

    /**
     * Exposes the configured strategy to Grails' own format resolution. The strategy is deliberately
     * not a bean of its own: Spring Security adopts any {@link org.springframework.web.accept.ContentNegotiationStrategy}
     * bean it finds, so it is reached through this configurer instead.
     */
    GrailsContentNegotiationStrategy getContentNegotiationStrategy() {
        return contentNegotiationStrategy
    }

    @Override
    void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        // Contribute the configured format aliases and nothing else. Replacing Spring's strategy
        // list would hand Grails' parser authority over every Spring MVC endpoint: it drops media
        // types absent from grails.mime.types and falls back to the defaults, which include */*, so
        // a request for an unknown type would be answered instead of rejected with 406. It would
        // also disable spring.mvc.contentnegotiation.* and apply the 'format' request parameter to
        // endpoints that never asked for it. Grails' own format resolution does not go through the
        // Spring manager; it uses this configurer's strategy directly.
        Map<String, MediaType> aliases = [:]
        for (MimeType mimeType in contentNegotiationStrategy.configuredMimeTypes) {
            String extension = mimeType.extension
            if (!extension || extension == MimeType.ALL.extension) {
                continue
            }
            MediaType mediaType = SpringMediaTypeAdapter.toMediaType(mimeType)
            if (mediaType != null && !mediaType.isWildcardType() && !mediaType.isWildcardSubtype()) {
                aliases.putIfAbsent(extension, new MediaType(mediaType.type, mediaType.subtype))
            }
        }
        if (aliases) {
            configurer.mediaTypes(aliases)
        }
    }
}
