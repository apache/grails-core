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
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.i18n.SimpleTimeZoneAwareLocaleContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.StaticWebApplicationContext

import org.grails.web.util.WebUtils

import spock.lang.Specification

class GrailsWebRequestFilterSpec extends Specification {

    MockServletContext servletContext = new MockServletContext()

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
        LocaleContextHolder.resetLocaleContext()
    }

    void 'the locale context established outside Grails is restored after the request'() {
        given: 'a LocaleContext installed by a filter outside Grails, carrying a time zone'
        def outerContext = new SimpleTimeZoneAwareLocaleContext(Locale.FRANCE, TimeZone.getTimeZone('Europe/Paris'))
        LocaleContextHolder.setLocaleContext(outerContext)

        when:
        filter().doFilter(request(Locale.GERMANY), new MockHttpServletResponse(), { req, res -> } as FilterChain)

        then: 'the outer context is put back, time zone and all'
        LocaleContextHolder.localeContext.is(outerContext)
        LocaleContextHolder.timeZone.ID == 'Europe/Paris'
    }

    void 'an include restores the locale context of the enclosing request'() {
        given: 'an outer request whose locale context is in place'
        def outerContext = new SimpleTimeZoneAwareLocaleContext(Locale.FRANCE, TimeZone.getTimeZone('Europe/Paris'))
        LocaleContextHolder.setLocaleContext(outerContext)

        when: 'an include is dispatched through the filter'
        def includeRequest = request(Locale.JAPAN)
        includeRequest.setAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE, '/some/include')
        filter().doFilter(includeRequest, new MockHttpServletResponse(), { req, res -> } as FilterChain)

        then: 'the include does not leave its own locale behind for the rest of the outer request'
        LocaleContextHolder.localeContext.is(outerContext)
        LocaleContextHolder.timeZone.ID == 'Europe/Paris'
    }

    void 'the request locale is in effect while the chain runs'() {
        given:
        LocaleContextHolder.setLocaleContext(new SimpleTimeZoneAwareLocaleContext(Locale.FRANCE, TimeZone.default))
        Locale seen = null

        when:
        filter().doFilter(request(Locale.GERMANY), new MockHttpServletResponse(),
                { req, res -> seen = LocaleContextHolder.locale } as FilterChain)

        then: 'the filter installs the locale of the request being handled'
        seen == Locale.GERMANY
    }

    private MockHttpServletRequest request(Locale locale) {
        new MockHttpServletRequest(servletContext).tap {
            it.addPreferredLocale(locale)
        }
    }

    private GrailsWebRequestFilter filter() {
        def applicationContext = new StaticWebApplicationContext()
        applicationContext.servletContext = servletContext
        applicationContext.refresh()
        def filter = new GrailsWebRequestFilter()
        filter.setApplicationContext(applicationContext)
        filter.setServletContext(servletContext)
        filter
    }
}
