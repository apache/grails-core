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
import org.springframework.web.accept.ContentNegotiationStrategy
import org.springframework.web.context.request.NativeWebRequest

import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * The strategy of Spring MVC's content negotiation manager that resolves the format a request a URL mapping handles
 * names, with its {@code format} parameter or a URL extension, to the media types {@code grails.mime.types} gives it,
 * ahead of Spring MVC's own strategies, so that what Spring MVC writes for it is in the format Grails resolves for it. It
 * leaves any other request, such as one a Spring MVC endpoint handles, to them, and so a request whose format names no
 * type, as the {@code all} format does. A format no type of {@code grails.mime.types} stands for is the type Spring MVC's
 * manager maps it to, as {@code spring.mvc.contentnegotiation.media-types} maps one, and a request that names a format
 * neither maps is not acceptable.
 *
 * @since 9.0
 */
@CompileStatic
class GrailsFormatContentNegotiationStrategy implements ContentNegotiationStrategy {

    private final Supplier<GrailsContentNegotiationStrategy> grailsStrategy

    /**
     * @param grailsStrategy supplies the strategy that resolves Grails formats, which Spring MVC's manager is created
     *        before
     */
    GrailsFormatContentNegotiationStrategy(Supplier<GrailsContentNegotiationStrategy> grailsStrategy) {
        this.grailsStrategy = grailsStrategy
    }

    @Override
    List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) throws HttpMediaTypeNotAcceptableException {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest)
        if (request == null || !GrailsContentNegotiationStrategy.handlesRequest(request)) {
            return MEDIA_TYPE_ALL_LIST
        }
        GrailsWebRequest grailsRequest = GrailsWebRequest.lookup(request)
        GrailsContentNegotiationStrategy strategy = grailsStrategy.get()
        Object format = grailsRequest != null ? strategy.requestedFormat(grailsRequest) : null
        if (format == null) {
            return MEDIA_TYPE_ALL_LIST
        }
        // a format grails.mime.types configures keeps its types; any other is the type Spring MVC's manager maps it to
        List<MediaType> mediaTypes = strategy.mediaTypesOf(format.toString()) ?: strategy.springMediaTypesOf(format.toString())
        if (mediaTypes == null) {
            throw new HttpMediaTypeNotAcceptableException(strategy.configuredMediaTypes)
        }
        return mediaTypes
    }
}
