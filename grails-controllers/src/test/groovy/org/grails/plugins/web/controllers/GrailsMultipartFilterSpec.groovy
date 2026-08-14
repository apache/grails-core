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
package org.grails.plugins.web.controllers

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest

import org.jspecify.annotations.NonNull
import spock.lang.Specification

import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartHttpServletRequest
import org.springframework.web.multipart.MultipartResolver

class GrailsMultipartFilterSpec extends Specification {

    void 'multipart cleanup is deferred until async processing completes'() {
        given:
        def resolver = Mock(MultipartResolver)
        def multipartRequest = new MockMultipartHttpServletRequest(asyncSupported: true)
        def filter = new TestGrailsMultipartFilter(resolver)
        def response = new MockHttpServletResponse()

        when:
        filter.doFilter(new MockMultipartHttpServletRequest(), response, { request, ignored ->
            request.startAsync()
        } as FilterChain)

        then:
        1 * resolver.isMultipart(_ as MockMultipartHttpServletRequest) >> true
        1 * resolver.resolveMultipart(_ as MockMultipartHttpServletRequest) >> multipartRequest
        0 * resolver.cleanupMultipart(_)

        when:
        multipartRequest.asyncContext.complete()

        then:
        1 * resolver.cleanupMultipart(multipartRequest)
    }

    private static class TestGrailsMultipartFilter extends GrailsMultipartFilter {

        private final MultipartResolver resolver

        TestGrailsMultipartFilter(MultipartResolver resolver) {
            this.resolver = resolver
        }

        @Override
        protected MultipartResolver lookupMultipartResolver(@NonNull HttpServletRequest request) {
            resolver
        }
    }
}
