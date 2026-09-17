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
package org.grails.web.servlet.mvc

import jakarta.servlet.FilterChain

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.i18n.SimpleTimeZoneAwareLocaleContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.support.StaticWebApplicationContext
import spock.lang.Specification

import grails.web.mvc.FlashScope
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils

class GrailsWebRequestFilterSpec extends Specification {

    MockServletContext servletContext = new MockServletContext()
    GrailsWebRequestFilter filter = new GrailsWebRequestFilter()

    void setup() {
        filter.servletContext = servletContext
    }

    void cleanup() {
        WebUtils.clearGrailsWebRequest()
        LocaleContextHolder.resetLocaleContext()
    }

    void 'a plain request binds a GrailsWebRequest for the chain and clears it afterward'() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext)
        request.preferredLocales = [Locale.CANADA]
        MockHttpServletResponse response = new MockHttpServletResponse()
        GrailsWebRequest seen
        FilterChain chain = { req, res -> seen = WebUtils.retrieveGrailsWebRequest() } as FilterChain

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        seen instanceof GrailsWebRequest
        seen.request.is(request)
        LocaleContextHolder.locale == Locale.getDefault()
        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() == null

        and: 'the flash scope was advanced to its next state before the chain ran'
        FlashScope flash = seen.attributes.getFlashScope(request)
        flash != null
    }

    void 'a forward or include request restores the previously bound web request instead of clearing it'() {
        given:
        MockHttpServletRequest outerRequest = new MockHttpServletRequest(servletContext)
        MockHttpServletResponse outerResponse = new MockHttpServletResponse()
        GrailsWebRequest previous = new GrailsWebRequest(outerRequest, outerResponse, servletContext)
        WebUtils.storeGrailsWebRequest(previous)

        MockHttpServletRequest forwardedRequest = new MockHttpServletRequest(servletContext)
        forwardedRequest.setAttribute('jakarta.servlet.forward.request_uri', '/original')
        MockHttpServletResponse response = new MockHttpServletResponse()
        GrailsWebRequest seenDuringChain
        FilterChain chain = { req, res -> seenDuringChain = WebUtils.retrieveGrailsWebRequest() } as FilterChain

        when:
        filter.doFilterInternal(forwardedRequest, response, chain)

        then:
        seenDuringChain instanceof GrailsWebRequest
        !seenDuringChain.is(previous)
        WebUtils.retrieveGrailsWebRequest().is(previous)

        cleanup:
        WebUtils.clearGrailsWebRequest()
    }

    void 'async and error dispatches are always filtered'() {
        expect:
        !filter.shouldNotFilterAsyncDispatch()
        !filter.shouldNotFilterErrorDispatch()
    }

    void 'setApplicationContext collects every registered ParameterCreationListener bean'() {
        given:
        ParameterCreationListener oneListener = Stub(ParameterCreationListener)
        ParameterCreationListener twoListener = Stub(ParameterCreationListener)
        org.springframework.context.support.StaticApplicationContext ctx = new org.springframework.context.support.StaticApplicationContext()
        ctx.beanFactory.registerSingleton('one', oneListener)
        ctx.beanFactory.registerSingleton('two', twoListener)

        when:
        filter.setApplicationContext(ctx)

        then:
        filter.paramListenerBeans.size() == 2
        filter.paramListenerBeans.containsAll([oneListener, twoListener])
    }

    void 'registered listeners are attached to the web request during the chain'() {
        given:
        List<GrailsWebRequest> seen = []
        ParameterCreationListener listener = Stub(ParameterCreationListener)
        filter.paramListenerBeans = [listener]
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext)
        MockHttpServletResponse response = new MockHttpServletResponse()
        FilterChain chain = { req, res -> seen << WebUtils.retrieveGrailsWebRequest() } as FilterChain

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        seen.size() == 1
    }

    void 'the locale context established outside Grails is restored after the request'() {
        given: 'a LocaleContext installed by a filter outside Grails, carrying a time zone'
        def outerContext = new SimpleTimeZoneAwareLocaleContext(Locale.FRANCE, TimeZone.getTimeZone('Europe/Paris'))
        LocaleContextHolder.setLocaleContext(outerContext)

        when:
        newFilter().doFilter(requestWithLocale(Locale.GERMANY), new MockHttpServletResponse(), { req, res -> } as FilterChain)

        then: 'the outer context is put back, time zone and all'
        LocaleContextHolder.localeContext.is(outerContext)
        LocaleContextHolder.timeZone.ID == 'Europe/Paris'
    }

    void 'an include restores the locale context of the enclosing request'() {
        given: 'an outer request whose locale context is in place'
        def outerContext = new SimpleTimeZoneAwareLocaleContext(Locale.FRANCE, TimeZone.getTimeZone('Europe/Paris'))
        LocaleContextHolder.setLocaleContext(outerContext)

        when: 'an include is dispatched through the filter'
        def includeRequest = requestWithLocale(Locale.JAPAN)
        includeRequest.setAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE, '/some/include')
        newFilter().doFilter(includeRequest, new MockHttpServletResponse(), { req, res -> } as FilterChain)

        then: 'the include does not leave its own locale behind for the rest of the outer request'
        LocaleContextHolder.localeContext.is(outerContext)
        LocaleContextHolder.timeZone.ID == 'Europe/Paris'
    }

    void 'the request locale is in effect while the chain runs'() {
        given:
        LocaleContextHolder.setLocaleContext(new SimpleTimeZoneAwareLocaleContext(Locale.FRANCE, TimeZone.default))
        Locale seen = null

        when:
        newFilter().doFilter(requestWithLocale(Locale.GERMANY), new MockHttpServletResponse(),
                { req, res -> seen = LocaleContextHolder.locale } as FilterChain)

        then: 'the filter installs the locale of the request being handled'
        seen == Locale.GERMANY
    }

    private MockHttpServletRequest requestWithLocale(Locale locale) {
        new MockHttpServletRequest(servletContext).tap {
            it.addPreferredLocale(locale)
        }
    }

    private GrailsWebRequestFilter newFilter() {
        def applicationContext = new StaticWebApplicationContext()
        applicationContext.servletContext = servletContext
        applicationContext.refresh()
        def newFilter = new GrailsWebRequestFilter()
        newFilter.setApplicationContext(applicationContext)
        newFilter.setServletContext(servletContext)
        newFilter
    }

}
