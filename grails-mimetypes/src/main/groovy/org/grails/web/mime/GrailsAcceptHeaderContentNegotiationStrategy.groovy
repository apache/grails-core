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
import org.springframework.web.accept.HeaderContentNegotiationStrategy
import org.springframework.web.context.request.NativeWebRequest

/**
 * The strategy of Spring MVC's content negotiation manager that reads the {@code Accept} header in place of Spring MVC's
 * own, and ignores it for a request a URL mapping handles where {@code grails.mime.use.accept.header} or
 * {@code grails.mime.disable.accept.header.*} say so, and otherwise takes a type with the {@code +xml} suffix ahead of the
 * XML type of equal quality, as Grails does, so that what Spring MVC writes for it is in the format Grails resolves for
 * it. Any other request, such as one a Spring MVC endpoint handles, is negotiated by the strategy it
 * replaces. It is a {@link HeaderContentNegotiationStrategy}, so that it is found as Spring MVC's is.
 *
 * @since 9.0
 */
@CompileStatic
class GrailsAcceptHeaderContentNegotiationStrategy extends HeaderContentNegotiationStrategy {

    private final HeaderContentNegotiationStrategy springStrategy

    private final Supplier<GrailsContentNegotiationStrategy> grailsStrategy

    /**
     * @param springStrategy the strategy of Spring MVC's manager that it replaces
     * @param grailsStrategy supplies the strategy that resolves Grails formats, which Spring MVC's manager is created
     *        before
     */
    GrailsAcceptHeaderContentNegotiationStrategy(HeaderContentNegotiationStrategy springStrategy,
                                                Supplier<GrailsContentNegotiationStrategy> grailsStrategy) {
        this.springStrategy = springStrategy
        this.grailsStrategy = grailsStrategy
    }

    /** @return the strategy of Spring MVC's manager it replaces */
    HeaderContentNegotiationStrategy getSpringStrategy() {
        return springStrategy
    }

    @Override
    List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) throws HttpMediaTypeNotAcceptableException {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest)
        boolean handled = request != null && GrailsContentNegotiationStrategy.handlesRequest(request)
        if (handled && grailsStrategy.get().ignoresAcceptHeader(request)) {
            return MEDIA_TYPE_ALL_LIST
        }
        List<MediaType> mediaTypes = springStrategy.resolveMediaTypes(webRequest)
        // in the order Grails reads them, so that what Spring MVC writes is in the format Grails resolves
        return handled ? grailsStrategy.get().preferringSuffixedXml(mediaTypes) : mediaTypes
    }
}
