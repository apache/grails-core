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
package grails.plugin.springsecurity.web

import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.web.context.request.RequestContextHolder

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils

import spock.lang.Specification

class UpdateRequestContextHolderExceptionTranslationFilterSpec extends Specification {

    MockServletContext servletContext = new MockServletContext()

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'the rebound web request answers for the filter chain request, not the superseded one'() {
        given: 'a web request bound earlier in the chain, carrying state of its own'
        GrailsWebRequest superseded = webRequest()
        superseded.controllerName = 'book'
        WebUtils.storeGrailsWebRequest(superseded)

        and: 'a later filter carrying a different request and response'
        def chainRequest = new MockHttpServletRequest(servletContext)
        def chainResponse = new MockHttpServletResponse()

        when: 'the filter runs'
        filter().doFilter(chainRequest, chainResponse, new MockFilterChain())
        GrailsWebRequest rebound = GrailsWebRequest.lookup()

        then: 'it replaced the bound web request with a delegating one'
        rebound instanceof DelegatingGrailsWebRequest
        !rebound.is(superseded)

        and: 'both request accessors report the request the chain is carrying'
        rebound.getRequest().is(chainRequest)
        rebound.getCurrentRequest().is(chainRequest)
        rebound.getResponse().is(chainResponse)

        and: 'it holds on to the superseded instance without disturbing it'
        rebound.current.is(superseded)
        !superseded.getRequest().is(chainRequest)

        and: 'request-derived state is read from the chain request rather than carried over'
        superseded.controllerName == 'book'
        rebound.controllerName == null
    }

    void 'a web request that is already delegating is left alone'() {
        given: 'a delegating web request already bound'
        GrailsWebRequest already = new DelegatingGrailsWebRequest(new MockHttpServletRequest(servletContext),
                new MockHttpServletResponse(), webRequest())
        WebUtils.storeGrailsWebRequest(already)

        when: 'the filter runs again, as it does for a nested chain'
        filter().doFilter(new MockHttpServletRequest(servletContext), new MockHttpServletResponse(), new MockFilterChain())

        then: 'it is not wrapped a second time'
        GrailsWebRequest.lookup().is(already)
    }

    private GrailsWebRequest webRequest() {
        new GrailsWebRequest(new MockHttpServletRequest(servletContext), new MockHttpServletResponse(), servletContext)
    }

    private UpdateRequestContextHolderExceptionTranslationFilter filter() {
        new UpdateRequestContextHolderExceptionTranslationFilter(Mock(AuthenticationEntryPoint))
    }
}
