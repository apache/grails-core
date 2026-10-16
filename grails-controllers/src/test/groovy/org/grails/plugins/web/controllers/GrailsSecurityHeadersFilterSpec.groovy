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

import ch.qos.logback.classic.Level
import org.apache.grails.core.testing.support.LogCapture
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

import jakarta.servlet.DispatcherType

import spock.lang.Specification

/**
 * Covers the request paths of {@link GrailsSecurityHeadersFilter} that the auto-configuration and
 * embedded Tomcat specs do not reach: the error dispatch of a request the container is already
 * serving an error for, an unset header, and the once-only reverse proxy notice.
 */
class GrailsSecurityHeadersFilterSpec extends Specification {

    void 'the filter still writes the headers on the error dispatch of a request that raised an error'() {
        given:
        def request = new MockHttpServletRequest('GET', '/boom')
        request.dispatcherType = DispatcherType.ERROR
        request.setAttribute('jakarta.servlet.error.request_uri', '/boom')
        def response = new MockHttpServletResponse()

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('X-Content-Type-Options') == 'nosniff'
        response.getHeader('X-Frame-Options') == 'SAMEORIGIN'
    }

    void 'an unset header is skipped even on a secure request'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        request.secure = true
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.hsts = null
        properties.contentSecurityPolicy = null

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('Strict-Transport-Security') == null
        response.getHeader('Content-Security-Policy') == null
        response.getHeader('X-Content-Type-Options') == 'nosniff'
    }

    void 'defaults=auto applies the defaults to a request that carries no reverse proxy header'() {
        given:
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.defaults = GrailsSecurityHeadersProperties.Defaults.AUTO

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(new MockHttpServletRequest('GET', '/'), response, new MockFilterChain())

        then:
        response.getHeader('X-Content-Type-Options') == 'nosniff'
    }

    void 'defaults=auto ignores a reverse proxy header that carries no value'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        request.addHeader('Via', '  ')
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.defaults = GrailsSecurityHeadersProperties.Defaults.AUTO

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('X-Content-Type-Options') == 'nosniff'
    }

    void 'defaults=auto announces the reverse proxy once however many proxied requests follow'() {
        given:
        def logCapture = new LogCapture(GrailsSecurityHeadersFilter)
        def properties = new GrailsSecurityHeadersProperties()
        properties.defaults = GrailsSecurityHeadersProperties.Defaults.AUTO
        def filter = new GrailsSecurityHeadersFilter(properties)

        when:
        2.times {
            def request = new MockHttpServletRequest('GET', '/')
            request.addHeader('Via', '1.1 proxy')
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain())
        }

        then:
        def notices = logCapture.events.findAll { it.level == Level.INFO }
        notices.size() == 1
        notices[0].formattedMessage.contains('Via request header')

        cleanup:
        logCapture.close()
    }
}
