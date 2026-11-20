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

import jakarta.servlet.http.HttpServletRequest

import org.springframework.http.MediaType
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.accept.ContentNegotiationStrategy
import org.springframework.web.accept.HeaderContentNegotiationStrategy
import org.springframework.web.accept.MediaTypeFileExtensionResolver
import org.springframework.web.context.request.NativeWebRequest

/**
 * Spring MVC's content negotiation manager with {@code grails.mime.negotiation.spring}: its strategies, after the one
 * that resolves the format a request a URL mapping handles names, and with the {@code Accept} header read by one that
 * ignores it for such a request where the {@code grails.mime} settings say so, and without Spring MVC's parameter
 * negotiation of the {@code format} parameter, which the first strategy reads for it. Any other request is negotiated as
 * by Spring MVC's manager, whose formats and file extensions it answers with.
 *
 * @since 9.0
 */
@CompileStatic
class GrailsContentNegotiationManager extends ContentNegotiationManager {

    private final ContentNegotiationManager springManager

    /**
     * @param springManager Spring MVC's manager
     * @param grailsStrategy supplies the strategy that resolves Grails formats, which Spring MVC's manager is created
     *        before
     */
    GrailsContentNegotiationManager(ContentNegotiationManager springManager,
                                    Supplier<GrailsContentNegotiationStrategy> grailsStrategy) {
        super(strategies(springManager, grailsStrategy))
        this.springManager = springManager
    }

    /** @return Spring MVC's manager, whose strategies this one applies to the requests no URL mapping handles */
    ContentNegotiationManager getSpringManager() {
        return springManager
    }

    /**
     * Resolves the media types of a request as Spring MVC's manager does, with its strategies in their order, but for a
     * request a URL mapping handles without Spring MVC's parameter negotiation of the {@code format} parameter, which the
     * first strategy has read: it would read a format Spring MVC does not know, such as {@code all}, again, and find the
     * request not acceptable.
     */
    @Override
    List<MediaType> resolveMediaTypes(NativeWebRequest request) throws HttpMediaTypeNotAcceptableException {
        HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest)
        if (servletRequest == null || !GrailsContentNegotiationStrategy.handlesRequest(servletRequest)) {
            return super.resolveMediaTypes(request)
        }
        for (ContentNegotiationStrategy strategy in strategies) {
            if (GrailsContentNegotiationStrategy.readsFormatParameter(strategy)) {
                continue
            }
            List<MediaType> mediaTypes = strategy.resolveMediaTypes(request)
            if (mediaTypes != MEDIA_TYPE_ALL_LIST) {
                return mediaTypes
            }
        }
        return MEDIA_TYPE_ALL_LIST
    }

    @Override
    void addFileExtensionResolvers(MediaTypeFileExtensionResolver... resolvers) {
        springManager.addFileExtensionResolvers(resolvers)
    }

    @Override
    List<String> resolveFileExtensions(MediaType mediaType) {
        return springManager.resolveFileExtensions(mediaType)
    }

    @Override
    List<String> getAllFileExtensions() {
        return springManager.allFileExtensions
    }

    @Override
    Map<String, MediaType> getMediaTypeMappings() {
        return springManager.mediaTypeMappings
    }

    private static List<ContentNegotiationStrategy> strategies(ContentNegotiationManager springManager,
                                                               Supplier<GrailsContentNegotiationStrategy> grailsStrategy) {
        List<ContentNegotiationStrategy> strategies = [new GrailsFormatContentNegotiationStrategy(grailsStrategy)] as List<ContentNegotiationStrategy>
        for (ContentNegotiationStrategy strategy in springManager.strategies) {
            strategies << (strategy instanceof HeaderContentNegotiationStrategy ?
                    new GrailsAcceptHeaderContentNegotiationStrategy((HeaderContentNegotiationStrategy) strategy, grailsStrategy) :
                    strategy)
        }
        return strategies
    }
}
