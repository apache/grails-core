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

import java.util.function.Supplier

import groovy.transform.CompileStatic

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

import grails.web.mime.MimeType

/**
 * Registers the configured Grails data formats with Spring MVC as format aliases, which Spring
 * negotiates with only when parameter negotiation is enabled, and which count among the safe
 * extensions of its reflected-file-download check, and holds the strategy Grails' own format
 * resolution uses.
 *
 * @since 9.0
 */
@CompileStatic
@Order(Ordered.HIGHEST_PRECEDENCE)
class GrailsMimeTypesWebMvcConfigurer implements WebMvcConfigurer {

    private static final Set<String> DATA_FORMATS = Set.of('json', 'xml', 'hal', 'atom', 'rss', 'csv', 'text')

    private final Supplier<GrailsContentNegotiationStrategy> strategySupplier
    private final Supplier<MimeType[]> dataFormatMimeTypes
    private volatile GrailsContentNegotiationStrategy contentNegotiationStrategy

    GrailsMimeTypesWebMvcConfigurer(GrailsContentNegotiationStrategy contentNegotiationStrategy) {
        this.contentNegotiationStrategy = contentNegotiationStrategy
        this.strategySupplier = null
        this.dataFormatMimeTypes = () -> contentNegotiationStrategy.configuredMimeTypes
    }

    /**
     * @param strategySupplier builds the strategy on first use. Spring MVC collects its configurers
     * while its own infrastructure beans are created, so a configurer that needed the MIME types
     * when it was created would pull in every bean that supplies them, and fail on a cycle.
     * @param dataFormatMimeTypes the MIME types whose data formats are registered with Spring MVC, which it asks for
     * while its content negotiation manager is created, so that they are not those of the strategy, whose MIME types
     * can come from beans that need that manager
     */
    GrailsMimeTypesWebMvcConfigurer(Supplier<GrailsContentNegotiationStrategy> strategySupplier,
            Supplier<MimeType[]> dataFormatMimeTypes) {
        this.strategySupplier = strategySupplier
        this.dataFormatMimeTypes = dataFormatMimeTypes
    }

    /**
     * Exposes the configured strategy to Grails' own format resolution. It is built on first use,
     * here rather than as a bean of its own, for the reason given for the constructor's strategy supplier.
     */
    GrailsContentNegotiationStrategy getContentNegotiationStrategy() {
        GrailsContentNegotiationStrategy strategy = contentNegotiationStrategy
        if (strategy == null) {
            synchronized (this) {
                strategy = contentNegotiationStrategy
                if (strategy == null) {
                    strategy = strategySupplier.get()
                    contentNegotiationStrategy = strategy
                }
            }
        }
        return strategy
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
        for (MimeType mimeType in dataFormatMimeTypes.get()) {
            String extension = mimeType.extension
            if (!DATA_FORMATS.contains(extension ?: '')) {
                continue
            }
            MediaType mediaType
            try {
                mediaType = SpringMediaTypeAdapter.toMediaType(mimeType)
            } catch (IllegalArgumentException ignored) {
                continue
            }
            if (mediaType != null && !mediaType.isWildcardType() && !mediaType.isWildcardSubtype()) {
                MediaType existing = aliases.get(extension)
                if (existing == null || (existing.type != 'application' && mediaType.type == 'application')) {
                    aliases.put(extension, new MediaType(mediaType.type, mediaType.subtype))
                }
            }
        }
        if (aliases) {
            configurer.mediaTypes(aliases)
        }
    }
}
