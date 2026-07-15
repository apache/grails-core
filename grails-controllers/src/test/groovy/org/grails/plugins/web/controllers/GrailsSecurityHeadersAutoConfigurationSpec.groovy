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

import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

import spock.lang.Specification

class GrailsSecurityHeadersAutoConfigurationSpec extends Specification {

    void 'default servlet web auto-configuration registers the security headers filter'() {
        expect:
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(GrailsSecurityHeadersFilter).length == 1
                    assert context.getBean('grailsSecurityHeadersFilter') instanceof FilterRegistrationBean
                }
    }

    void 'security headers auto-configuration does not run for non-web applications'() {
        expect:
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(GrailsSecurityHeadersFilter).length == 0
                }
    }

    void 'security headers auto-configuration can be disabled'() {
        expect:
        new WebApplicationContextRunner()
                .withPropertyValues('grails.security.headers.enabled=false')
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(GrailsSecurityHeadersFilter).length == 0
                }
    }

    void 'application-defined security headers filter makes the auto-configured filter back off'() {
        given:
        def userFilter = new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties())

        expect:
        new WebApplicationContextRunner()
                .withBean(GrailsSecurityHeadersFilter) { userFilter }
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBean(GrailsSecurityHeadersFilter).is(userFilter)
                    assert context.getBeanNamesForType(GrailsSecurityHeadersFilter).length == 1
                }
    }

    void 'application-defined security headers registration makes the raw filter back off'() {
        given:
        def userRegistration = new FilterRegistrationBean()

        expect:
        new WebApplicationContextRunner()
                .withBean('grailsSecurityHeadersFilter', FilterRegistrationBean) { userRegistration }
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBean('grailsSecurityHeadersFilter').is(userRegistration)
                    assert context.getBeanNamesForType(GrailsSecurityHeadersFilter).length == 0
                }
    }

    void 'default filter writes browser hardening headers and skips disabled optional headers'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        def filter = new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties())

        when:
        filter.doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('X-Content-Type-Options') == 'nosniff'
        response.getHeader('X-Frame-Options') == 'SAMEORIGIN'
        response.getHeader('Referrer-Policy') == 'strict-origin-when-cross-origin'
        response.getHeader('X-XSS-Protection') == '0'
        response.getHeader('Strict-Transport-Security') == null
        response.getHeader('Content-Security-Policy') == null
    }

    void 'default filter writes browser hardening headers for error dispatches'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        request.dispatcherType = DispatcherType.ERROR
        def response = new MockHttpServletResponse()
        def filter = new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties())

        when:
        filter.doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('X-Content-Type-Options') == 'nosniff'
        response.getHeader('X-Frame-Options') == 'SAMEORIGIN'
        response.getHeader('Referrer-Policy') == 'strict-origin-when-cross-origin'
        response.getHeader('X-XSS-Protection') == '0'
    }

    void 'filter applies configured overrides and optional headers'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        request.secure = true
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.frameOptions.value = 'DENY'
        properties.referrerPolicy.value = 'no-referrer-when-downgrade'
        properties.hsts.enabled = true
        properties.hsts.value = 'max-age=63072000; includeSubDomains'
        properties.contentSecurityPolicy.enabled = true
        properties.contentSecurityPolicy.value = "default-src 'self'"

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('X-Frame-Options') == 'DENY'
        response.getHeader('Referrer-Policy') == 'no-referrer-when-downgrade'
        response.getHeader('Strict-Transport-Security') == 'max-age=63072000; includeSubDomains'
        response.getHeader('Content-Security-Policy') == "default-src 'self'"
    }

    void 'filter respects per-header disable switches and existing response headers'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        response.setHeader('X-Frame-Options', 'DENY')
        def properties = new GrailsSecurityHeadersProperties()
        properties.contentTypeOptions.enabled = false
        properties.xssProtection.enabled = false

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('X-Content-Type-Options') == null
        response.getHeader('X-XSS-Protection') == null
        response.getHeader('X-Frame-Options') == 'DENY'
        response.getHeader('Referrer-Policy') == 'strict-origin-when-cross-origin'
    }

    void 'filter writes security headers before downstream commits the response'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.sendRedirect('/target')
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        response.committed
        response.redirectedUrl == '/target'
        response.getHeader('X-Content-Type-Options') == 'nosniff'
        response.getHeader('X-Frame-Options') == 'SAMEORIGIN'
        response.getHeader('Referrer-Policy') == 'strict-origin-when-cross-origin'
        response.getHeader('X-XSS-Protection') == '0'
    }
}
