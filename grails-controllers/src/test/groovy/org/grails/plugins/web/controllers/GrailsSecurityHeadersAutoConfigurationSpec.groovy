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
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper

import ch.qos.logback.classic.Level
import org.apache.grails.core.testing.support.LogCapture
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.header.HeaderWriterFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter
import org.springframework.web.filter.CharacterEncodingFilter

import org.grails.web.config.http.GrailsFilters

import spock.lang.Specification
import spock.lang.Unroll

class GrailsSecurityHeadersAutoConfigurationSpec extends Specification {

    private static final List<String> DEFAULT_HEADER_NAMES =
            ['X-Content-Type-Options', 'X-Frame-Options', 'Referrer-Policy', 'X-XSS-Protection']

    void 'default servlet web auto-configuration registers the security headers filter'() {
        expect:
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(GrailsSecurityHeadersFilter).length == 1
                    assert context.getBean('grailsSecurityHeadersFilter') instanceof FilterRegistrationBean
                }
    }

    void 'the security headers filter is registered outermost so every later filter can win at commit time'() {
        expect:
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    def registration = context.getBean('grailsSecurityHeadersFilter', FilterRegistrationBean)
                    assert registration.order == GrailsFilters.FIRST.getOrder()
                    assert registration.order < GrailsFilters.ASSET_PIPELINE_FILTER.getOrder()
                    assert registration.urlPatterns == ['/*'] as Set
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

    void 'security headers auto-configuration stays active when Spring Security header writing is on the classpath'() {
        expect: 'the real HeaderWriterFilter class is on this spec\'s test classpath'
        HeaderWriterFilter != null
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(GrailsSecurityHeadersFilter).length == 1
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

    void 'a registration declared for the security headers filter under another name makes the auto-configured registration back off'() {
        expect: 'the user filter is not registered a second time at FIRST on /*'
        new WebApplicationContextRunner()
                .withUserConfiguration(UserFilterAndRegistration)
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBean(GrailsSecurityHeadersFilter).is(context.getBean('mySecurityHeaders'))
                    assert context.getBeanNamesForType(GrailsSecurityHeadersFilter).length == 1
                    assert context.getBeanNamesForType(FilterRegistrationBean) == ['mySecurityHeadersRegistration'] as String[]
                    assert !context.containsBean('grailsSecurityHeadersFilter')
                }
    }

    void 'a registration that constructs the security headers filter itself makes the raw filter bean back off too'() {
        expect: 'otherwise Boot would auto-register the orphaned Grails filter bean alongside the user registration'
        new WebApplicationContextRunner()
                .withUserConfiguration(InlineFilterRegistration)
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(GrailsSecurityHeadersFilter).length == 0
                    assert context.getBeanNamesForType(FilterRegistrationBean) == ['inlineSecurityHeadersRegistration'] as String[]
                }
    }

    void 'registrations for other filters do not make the security headers filter back off'() {
        expect:
        new WebApplicationContextRunner()
                .withUserConfiguration(UnrelatedFilterRegistrations)
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBeanNamesForType(GrailsSecurityHeadersFilter).length == 1
                    assert context.getBean('grailsSecurityHeadersFilter') instanceof FilterRegistrationBean
                    assert context.getBeanNamesForType(FilterRegistrationBean).length == 3
                }
    }

    void 'a filter constructed with the headers disabled passes the response through untouched'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.enabled = false
        boolean chainReached = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            chainReached = true
            assert downstreamResponse.is(response)
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, downstream)

        then:
        chainReached
        DEFAULT_HEADER_NAMES.every { response.getHeader(it) == null }
    }

    void 'an enabled header with a blank value is not sent and is warned about once'() {
        given:
        def logCapture = new LogCapture(GrailsSecurityHeadersFilter)
        def properties = new GrailsSecurityHeadersProperties()
        properties.contentSecurityPolicy.enabled = true
        properties.contentSecurityPolicy.value = value
        def filter = new GrailsSecurityHeadersFilter(properties)

        when: 'two requests pass through the filter'
        def first = new MockHttpServletResponse()
        filter.doFilter(new MockHttpServletRequest('GET', '/'), first, new MockFilterChain())
        def second = new MockHttpServletResponse()
        filter.doFilter(new MockHttpServletRequest('GET', '/'), second, new MockFilterChain())

        then:
        first.getHeader('Content-Security-Policy') == null
        second.getHeader('Content-Security-Policy') == null
        first.getHeader('X-Content-Type-Options') == 'nosniff'

        and: 'the misconfiguration is logged once, naming the header and the property to fix'
        def warnings = logCapture.events.findAll { it.level == Level.WARN }
        warnings.size() == 1
        warnings[0].formattedMessage.contains('Content-Security-Policy')
        warnings[0].formattedMessage.contains('grails.security.headers.content-security-policy.value')

        cleanup:
        logCapture.close()

        where:
        value << [null, '', '   ']
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

    void 'headers set downstream of the filter win over the Grails defaults'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.setHeader('X-Frame-Options', 'DENY')
            downstreamResponse.setHeader('Referrer-Policy', 'no-referrer')
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        response.getHeader('X-Frame-Options') == 'DENY'
        response.getHeader('Referrer-Policy') == 'no-referrer'
        response.getHeader('X-Content-Type-Options') == 'nosniff'
        response.getHeader('X-XSS-Protection') == '0'
    }

    void 'Spring Security header writers running after the filter win and Grails fills the rest'() {
        given: 'Spring Security writes X-Frame-Options: DENY at commit time, as its HeaderWriterFilter does in a real chain'
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        def springSecurity = new HeaderWriterFilter([
                new XFrameOptionsHeaderWriter(XFrameOptionsHeaderWriter.XFrameOptionsMode.DENY)
        ])
        def servlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse res) {
                res.writer.write('ok')
            }
        }

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties())
                .doFilter(request, response, new MockFilterChain(servlet, springSecurity))

        then:
        response.getHeader('X-Frame-Options') == 'DENY'
        response.getHeader('X-Content-Type-Options') == 'nosniff'
        response.getHeader('Referrer-Policy') == 'strict-origin-when-cross-origin'
        response.getHeader('X-XSS-Protection') == '0'
        response.contentAsString == 'ok'
    }

    void 'a Spring Security writer that overwrites unconditionally still wins when its chain is ordered ahead of the filter'() {
        given: 'spring.security.filter.order placed ahead of the Grails filter, so its wrapper is the outer one'
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        def springSecurity = new HeaderWriterFilter([
                new XFrameOptionsHeaderWriter(XFrameOptionsHeaderWriter.XFrameOptionsMode.DENY)
        ])
        def servlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse res) {
                res.writer.write('ok')
            }
        }

        when:
        springSecurity.doFilter(request, response,
                new MockFilterChain(servlet, new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties())))

        then: 'the outer Spring Security wrapper is now the last to write, and XFrameOptionsHeaderWriter replaces the Grails default'
        response.getHeader('X-Frame-Options') == 'DENY'
        response.getHeader('X-Content-Type-Options') == 'nosniff'
    }

    void 'a Spring Security writer that only fills absent headers finds the Grails value when its chain is ordered ahead of the filter'() {
        given: 'ReferrerPolicyHeaderWriter, like every Spring Security writer but the X-Frame-Options one, backs off on containsHeader'
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        def springSecurity = new HeaderWriterFilter([
                new ReferrerPolicyHeaderWriter(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)
        ])
        def servlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse res) {
                res.writer.write('ok')
            }
        }
        def properties = new GrailsSecurityHeadersProperties()
        properties.referrerPolicy.enabled = referrerPolicyEnabled

        when:
        springSecurity.doFilter(request, response,
                new MockFilterChain(servlet, new GrailsSecurityHeadersFilter(properties)))

        then: 'the Grails default is already there by the time the outer writer runs; disabling it hands the header to Spring Security'
        response.getHeader('Referrer-Policy') == expectedReferrerPolicy
        response.getHeader('X-Content-Type-Options') == 'nosniff'

        where:
        referrerPolicyEnabled || expectedReferrerPolicy
        true                  || 'strict-origin-when-cross-origin'
        false                 || 'no-referrer'
    }

    void 'a downstream filter that serves the response itself without continuing the chain still gets the headers'() {
        given: 'the asset-pipeline pattern: write the asset, flushBuffer, never call chain.doFilter'
        def request = new MockHttpServletRequest('GET', '/assets/app.js')
        def response = new MockHttpServletResponse()
        boolean headersPresentAtFlush = false
        FilterChain assetFilter = { downstreamRequest, downstreamResponse ->
            downstreamResponse.contentType = 'application/javascript'
            downstreamResponse.outputStream.write('console.log(1)'.bytes)
            downstreamResponse.flushBuffer()
            headersPresentAtFlush = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, assetFilter)

        then:
        response.committed
        headersPresentAtFlush
        response.getHeader('X-Content-Type-Options') == 'nosniff'
    }

    void 'filter writes security headers before a downstream redirect commits the response'() {
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
        DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
    }

    void 'filter writes security headers before a downstream redirect with an explicit status commits the response'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.sendRedirect('/target', 308, true)
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        response.committed
        response.status == 308
        response.redirectedUrl == '/target'
        DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
    }

    @Unroll
    void 'filter writes security headers before a downstream sendError(#description) commits the response'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            if (message == null) {
                downstreamResponse.sendError(404)
            }
            else {
                downstreamResponse.sendError(404, message)
            }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        response.committed
        response.status == 404
        DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }

        where:
        description       | message
        'status only'     | null
        'status, message' | 'missing'
    }

    void 'filter writes security headers before a streaming downstream flushes the response'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        boolean headersPresentAtFlush = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            def out = downstreamResponse.outputStream
            out.write('chunk'.bytes)
            out.flush()
            headersPresentAtFlush = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
            out.write('more'.bytes)
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        response.committed
        headersPresentAtFlush
        response.contentAsString == 'chunkmore'
    }

    @Unroll
    void 'filter writes security headers before closing the #streamType commits the response'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        boolean headersPresentAtClose = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            if (streamType == 'writer') {
                def writer = downstreamResponse.writer
                writer.write('done')
                writer.close()
            }
            else {
                def out = downstreamResponse.outputStream
                out.write('done'.bytes)
                out.close()
            }
            headersPresentAtClose = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        headersPresentAtClose
        response.contentAsString == 'done'

        where:
        streamType << ['writer', 'output stream']
    }

    void 'filter writes security headers once the body outgrows the response buffer'() {
        given: 'a container commits as soon as the buffer fills, with no explicit flush from the application'
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        response.bufferSize = 16
        boolean headersPresentBeforeBufferFull = false
        boolean headersPresentAfterBufferFull = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            def out = downstreamResponse.outputStream
            out.write(('x' * 8).bytes)
            headersPresentBeforeBufferFull = DEFAULT_HEADER_NAMES.any { response.getHeader(it) != null }
            out.write(('y' * 56).bytes)
            headersPresentAfterBufferFull = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        !headersPresentBeforeBufferFull
        headersPresentAfterBufferFull
    }

    void 'filter writes security headers once the body written through the writer outgrows the response buffer'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        response.bufferSize = 16
        boolean headersPresentAfterBufferFull = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.writer.write('z' * 64)
            headersPresentAfterBufferFull = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        headersPresentAfterBufferFull
    }

    void 'a reset after the headers were written re-arms the filter for the replacement response'() {
        given: 'the body reaches its declared length without committing, then an error handler starts over'
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        boolean headersPresentBeforeReset = false
        boolean headersPresentAfterReset = true
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.setContentLength(2)
            downstreamResponse.writer.write('ok')
            headersPresentBeforeReset = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
            downstreamResponse.reset()
            headersPresentAfterReset = DEFAULT_HEADER_NAMES.any { response.getHeader(it) != null }
            downstreamResponse.status = 500
            downstreamResponse.writer.write('error')
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        headersPresentBeforeReset
        !headersPresentAfterReset
        response.status == 500
        response.contentAsString == 'error'
        DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
    }

    void 'a reset forgets the declared content length so the replacement body commits on its own terms'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        boolean headersPresentEarly = true
        boolean headersPresentAtNewLength = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.setContentLength(2)
            downstreamResponse.writer.write('ok')
            downstreamResponse.reset()
            downstreamResponse.setContentLength(5)
            def writer = downstreamResponse.writer
            writer.write('er')
            headersPresentEarly = DEFAULT_HEADER_NAMES.any { response.getHeader(it) != null }
            writer.write('ror')
            headersPresentAtNewLength = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        !headersPresentEarly
        headersPresentAtNewLength
    }

    void 'a writer obtained after a reset counts in the character encoding set after the reset'() {
        given: 'twelve characters are 12 bytes in UTF-8 but 24 in UTF-16BE, past a 16 byte buffer'
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        response.bufferSize = 16
        boolean sameWriterBeforeReset = false
        boolean newWriterAfterReset = false
        boolean headersPresentAfterBufferFull = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.characterEncoding = 'UTF-8'
            def first = downstreamResponse.writer
            sameWriterBeforeReset = first.is(downstreamResponse.writer)
            downstreamResponse.reset()
            downstreamResponse.characterEncoding = 'UTF-16BE'
            def second = downstreamResponse.writer
            newWriterAfterReset = !second.is(first)
            second.write('x' * 12)
            headersPresentAfterBufferFull = response.committed &&
                    DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        sameWriterBeforeReset
        newWriterAfterReset
        headersPresentAfterBufferFull
        response.contentAsByteArray.length == 24
    }

    void 'resetBuffer restarts the count of body written toward the buffer size'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        response.bufferSize = 16
        boolean headersPresentAfterSecondWrite = true
        boolean headersPresentAfterThirdWrite = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            def out = downstreamResponse.outputStream
            out.write(('a' * 8).bytes)
            downstreamResponse.resetBuffer()
            out.write(('b' * 8).bytes)
            headersPresentAfterSecondWrite = DEFAULT_HEADER_NAMES.any { response.getHeader(it) != null }
            out.write(('c' * 8).bytes)
            headersPresentAfterThirdWrite = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        !headersPresentAfterSecondWrite
        headersPresentAfterThirdWrite
    }

    void 'a buffer size set after a Content-Length declaration is what the buffer-full check uses'() {
        given: 'declaring the length reads the buffer size early; shrinking the buffer afterwards is still legal'
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        boolean headersPresentAtSmallBufferFull = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.setContentLength(100)
            downstreamResponse.bufferSize = 16
            downstreamResponse.outputStream.write(('x' * 16).bytes)
            headersPresentAtSmallBufferFull = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        headersPresentAtSmallBufferFull
    }

    void 'filter counts the line separator written by println toward the declared content length'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        boolean headersPresentAfterPrintln = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.setContentLength(2 + System.lineSeparator().length())
            downstreamResponse.writer.println('hi')
            headersPresentAfterPrintln = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        headersPresentAfterPrintln
        response.contentAsString == 'hi' + System.lineSeparator()
    }

    @Unroll
    void 'filter honors a Content-Length declared through #description'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        boolean headersPresentAfterLastByte = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            declare(downstreamResponse)
            downstreamResponse.outputStream.write('hello'.bytes)
            headersPresentAfterLastByte = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        headersPresentAfterLastByte

        where:
        description            | declare
        'setContentLengthLong' | { HttpServletResponse r -> r.setContentLengthLong(5L) }
        'setHeader'            | { HttpServletResponse r -> r.setHeader('Content-Length', '5') }
        'addHeader'            | { HttpServletResponse r -> r.addHeader('content-length', '5') }
        'setIntHeader'         | { HttpServletResponse r -> r.setIntHeader('Content-Length', 5) }
        'addIntHeader'         | { HttpServletResponse r -> r.addIntHeader('Content-Length', 5) }
    }

    void 'a Content-Length declared after the body was written commits at once'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        boolean headersPresentAfterDeclaration = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.outputStream.write('hello'.bytes)
            downstreamResponse.setContentLength(5)
            headersPresentAfterDeclaration = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        headersPresentAfterDeclaration
    }

    void 'a malformed Content-Length header is ignored and the headers are still written when the chain returns'() {
        given: 'a container that, like Tomcat, drops an unparseable Content-Length instead of failing the request'
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        def lenientContainer = new HttpServletResponseWrapper(response) {
            @Override
            void setHeader(String name, String value) {
                if (!('Content-Length'.equalsIgnoreCase(name) && !value.isLong())) {
                    super.setHeader(name, value)
                }
            }
        }
        boolean headersPresentInsideChain = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.setHeader('Content-Length', 'not-a-number')
            downstreamResponse.outputStream.write('hello'.bytes)
            headersPresentInsideChain = DEFAULT_HEADER_NAMES.any { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties())
                .doFilter(request, lenientContainer, downstream)

        then:
        !headersPresentInsideChain
        DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
    }

    void 'filter writes security headers once the declared content length has been written'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        boolean headersPresentBeforeLastByte = false
        boolean headersPresentAfterLastByte = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.setContentLength(5)
            def writer = downstreamResponse.writer
            writer.write('hell')
            headersPresentBeforeLastByte = DEFAULT_HEADER_NAMES.any { response.getHeader(it) != null }
            writer.write('o')
            headersPresentAfterLastByte = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        !headersPresentBeforeLastByte
        headersPresentAfterLastByte
    }

    @Unroll
    void 'writer output fills the response buffer in encoded #encoding bytes: #description'() {
        given: 'the container buffer is sized in bytes, so multi-byte text fills it before the character count does'
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        response.characterEncoding = encoding
        response.bufferSize = bufferBytes
        boolean headersPresentBeforeLastWrite = true
        boolean headersPresentAfterLastWrite = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            def writer = downstreamResponse.writer
            write(writer, head)
            headersPresentBeforeLastWrite = DEFAULT_HEADER_NAMES.any { response.getHeader(it) != null }
            write(writer, last)
            headersPresentAfterLastWrite = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        !headersPresentBeforeLastWrite
        headersPresentAfterLastWrite

        where:
        encoding     | head   | last | bufferBytes | description                                   | write
        'UTF-8'      | '日日日' | '日'  | 12          | 'three-byte characters through write(String)' | { PrintWriter w, String t -> w.write(t) }
        'UTF-8'      | '日日日' | '日'  | 12          | 'three-byte characters through write(char[])' | { PrintWriter w, String t -> w.write(t.toCharArray()) }
        'UTF-8'      | '日日日' | '日'  | 12          | 'three-byte characters through write(int)'    | { PrintWriter w, String t -> t.each { w.write((int) it.charAt(0)) } }
        'UTF-8'      | 'жжж'  | 'ж'  | 8           | 'two-byte characters'                         | { PrintWriter w, String t -> w.write(t) }
        'UTF-8'      | '😀'   | '😀' | 8           | 'surrogate pairs, four bytes each'            | { PrintWriter w, String t -> w.write(t) }
        'UTF-8'      | 'abc'  | 'd'  | 4           | 'ASCII, one byte per character'               | { PrintWriter w, String t -> w.write(t) }
        'ISO-8859-1' | 'éé'   | 'é'  | 3           | 'a single-byte encoding'                      | { PrintWriter w, String t -> w.write(t) }
        'Shift_JIS'  | '日'    | '本'  | 4           | 'the maximum bytes per character of another encoding' | { PrintWriter w, String t -> w.write(t) }
    }

    void 'multi-byte writer output reaches a declared Content-Length in bytes'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        response.characterEncoding = 'UTF-8'
        boolean headersPresentBeforeLastByte = true
        boolean headersPresentAfterLastByte = false
        FilterChain downstream = { downstreamRequest, downstreamResponse ->
            downstreamResponse.setContentLength(6)
            def writer = downstreamResponse.writer
            writer.write('日')
            headersPresentBeforeLastByte = DEFAULT_HEADER_NAMES.any { response.getHeader(it) != null }
            writer.write('本')
            headersPresentAfterLastByte = DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
        } as FilterChain

        when:
        new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()).doFilter(request, response, downstream)

        then:
        !headersPresentBeforeLastByte
        headersPresentAfterLastByte
    }

    @Unroll
    void 'HSTS honors the forwarded scheme behind a TLS-terminating proxy: #headerName=#headerValue -> #expected'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        request.secure = false
        request.addHeader(headerName, headerValue)
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.hsts.enabled = true

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        (response.getHeader('Strict-Transport-Security') != null) == expected

        where:
        headerName          | headerValue                                  || expected
        'X-Forwarded-Proto' | 'https'                                      || true
        'X-Forwarded-Proto' | 'HTTPS'                                      || true
        'X-Forwarded-Proto' | 'https, http'                                || true
        'X-Forwarded-Proto' | 'http'                                       || false
        'X-Forwarded-Proto' | 'http, https'                                || false
        'Forwarded'         | 'for=192.0.2.60;proto=https;by=203.0.113.43' || true
        'Forwarded'         | 'for=192.0.2.60;proto="https"'               || true
        'Forwarded'         | 'for=192.0.2.60; PROTO=https'                || true
        'Forwarded'         | 'for=192.0.2.60;proto=http, proto=https'     || false
        'Forwarded'         | 'for=192.0.2.60'                             || false
        'X-Forwarded-Ssl'   | 'on'                                         || true
        'X-Forwarded-For'   | '192.0.2.60'                                 || false
    }

    @Unroll
    void 'HSTS is not sent when the forwarded headers cannot be parsed: #headerName: #headerValue'() {
        given: 'a malformed forwarded value invalidates the whole forwarded header set'
        def request = new MockHttpServletRequest('GET', '/')
        request.secure = false
        request.addHeader('X-Forwarded-Proto', 'https')
        request.addHeader(headerName, headerValue)
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.hsts.enabled = true

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('Strict-Transport-Security') == null
        response.getHeader('X-Content-Type-Options') == 'nosniff'

        where:
        headerName         | headerValue
        'X-Forwarded-Port' | 'not-a-port'
        'X-Forwarded-Host' | 'a:b:c'
        'Forwarded'        | 'host=a:b:c;proto=https'
    }

    @Unroll
    void 'HSTS resolves the forwarded scheme for a request path java.net.URI rejects: #path (#forwarded)'() {
        given: 'Tomcat accepts such paths when relaxedPathChars allows them'
        def request = new MockHttpServletRequest('GET', path)
        request.secure = false
        if (forwarded) {
            request.addHeader('X-Forwarded-Proto', 'https')
        }
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.hsts.enabled = true

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        (response.getHeader('Strict-Transport-Security') != null) == forwarded
        response.getHeader('X-Content-Type-Options') == 'nosniff'

        where:
        path    | forwarded
        '/p|x'  | true
        '/p{x}' | true
        '/p[x]' | true
        '/p^x'  | true
        '/p`x'  | true
        '/p|x'  | false
    }

    void 'HSTS is sent when the container reports an https scheme on a connection it does not mark secure'() {
        given: 'a connector configured with scheme="https" but not secure, as some proxy setups do'
        def request = new MockHttpServletRequest('GET', '/')
        request.secure = false
        request.scheme = 'https'
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.hsts.enabled = true

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('Strict-Transport-Security') == 'max-age=31536000'
    }

    void 'HSTS is not sent on an insecure request with no forwarded scheme'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        request.secure = false
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.hsts.enabled = true

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('Strict-Transport-Security') == null
    }

    void 'the defaults are applied by default, including to requests relayed through a reverse proxy'() {
        given:
        def properties = new GrailsSecurityHeadersProperties()
        def request = new MockHttpServletRequest('GET', '/')
        request.addHeader('X-Forwarded-For', '203.0.113.7')
        request.addHeader('X-Real-IP', '203.0.113.7')
        def response = new MockHttpServletResponse()

        expect:
        properties.defaults == GrailsSecurityHeadersProperties.Defaults.ALWAYS

        when:
        new GrailsSecurityHeadersFilter(properties, true).doFilter(request, response, new MockFilterChain())

        then:
        DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
    }

    @Unroll
    void 'defaults=auto: a proxied request (#headerName) suppresses the defaults but keeps explicit headers'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        request.addHeader(headerName, 'proxy-value')
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.defaults = GrailsSecurityHeadersProperties.Defaults.AUTO
        properties.frameOptions.value = 'DENY'

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('X-Frame-Options') == 'DENY'
        response.getHeader('X-Content-Type-Options') == null
        response.getHeader('Referrer-Policy') == null
        response.getHeader('X-XSS-Protection') == null

        where:
        headerName << ['Forwarded', 'X-Forwarded-For', 'X-Forwarded-Proto', 'X-Forwarded-Host', 'Via', 'X-Real-IP']
    }

    void 'defaults=always applies the defaults even when a reverse proxy is detected'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        request.addHeader('X-Forwarded-For', '192.0.2.60')
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.defaults = GrailsSecurityHeadersProperties.Defaults.ALWAYS

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
    }

    void 'defaults=never sends only explicitly configured headers even without a proxy'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.defaults = GrailsSecurityHeadersProperties.Defaults.NEVER
        properties.referrerPolicy.enabled = true

        when:
        new GrailsSecurityHeadersFilter(properties).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('Referrer-Policy') == 'strict-origin-when-cross-origin'
        response.getHeader('X-Content-Type-Options') == null
        response.getHeader('X-Frame-Options') == null
        response.getHeader('X-XSS-Protection') == null
    }

    void 'defaults=auto: a filter told the deployment is proxied suppresses defaults without per-request signals'() {
        given:
        def request = new MockHttpServletRequest('GET', '/')
        def response = new MockHttpServletResponse()
        def properties = new GrailsSecurityHeadersProperties()
        properties.defaults = GrailsSecurityHeadersProperties.Defaults.AUTO
        properties.contentSecurityPolicy.enabled = true
        properties.contentSecurityPolicy.value = "default-src 'self'"

        when:
        new GrailsSecurityHeadersFilter(properties, true).doFilter(request, response, new MockFilterChain())

        then:
        response.getHeader('Content-Security-Policy') == "default-src 'self'"
        DEFAULT_HEADER_NAMES.every { response.getHeader(it) == null }
    }

    void 'headers are not marked explicit until the application configures them'() {
        given:
        def properties = new GrailsSecurityHeadersProperties()

        expect:
        !properties.contentTypeOptions.explicit
        !properties.frameOptions.explicit
        !properties.referrerPolicy.explicit
        !properties.xssProtection.explicit
        !properties.hsts.explicit
        !properties.contentSecurityPolicy.explicit

        when:
        properties.hsts.enabled = true
        properties.frameOptions.value = 'DENY'

        then:
        properties.hsts.explicit
        properties.frameOptions.explicit
        !properties.referrerPolicy.explicit
    }

    @Unroll
    void 'auto-configured filter applies the defaults to a proxied request by default when #property is set'() {
        expect:
        new WebApplicationContextRunner()
                .withPropertyValues(property)
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    def request = new MockHttpServletRequest('GET', '/')
                    request.addHeader('X-Forwarded-For', '203.0.113.7')
                    def response = new MockHttpServletResponse()
                    context.getBean(GrailsSecurityHeadersFilter).doFilter(request, response, new MockFilterChain())
                    assert DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
                }

        where:
        property << ['server.forward-headers-strategy=framework',
                     'server.forward-headers-strategy=native',
                     'spring.main.cloud-platform=kubernetes']
    }

    @Unroll
    void 'defaults=auto: auto-configured filter treats every request as proxied when #property is set'() {
        expect:
        new WebApplicationContextRunner()
                .withPropertyValues(property, 'grails.security.headers.defaults=auto',
                        'grails.security.headers.frame-options.value=DENY')
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    def response = new MockHttpServletResponse()
                    context.getBean(GrailsSecurityHeadersFilter)
                            .doFilter(new MockHttpServletRequest('GET', '/'), response, new MockFilterChain())
                    assert response.getHeader('X-Frame-Options') == 'DENY'
                    assert response.getHeader('X-Content-Type-Options') == null
                    assert response.getHeader('Referrer-Policy') == null
                    assert response.getHeader('X-XSS-Protection') == null
                }

        where:
        property << ['server.forward-headers-strategy=framework',
                     'server.forward-headers-strategy=native',
                     'spring.main.cloud-platform=kubernetes']
    }

    @Unroll
    void 'defaults=auto: auto-configured filter applies defaults to a direct request when #description'() {
        expect:
        new WebApplicationContextRunner()
                .withPropertyValues((properties + ['grails.security.headers.defaults=auto']) as String[])
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    def response = new MockHttpServletResponse()
                    context.getBean(GrailsSecurityHeadersFilter)
                            .doFilter(new MockHttpServletRequest('GET', '/'), response, new MockFilterChain())
                    assert DEFAULT_HEADER_NAMES.every { response.getHeader(it) != null }
                }

        where:
        description                           | properties
        'nothing proxy-related is configured' | []
        'forward-headers-strategy is none'    | ['server.forward-headers-strategy=none']
        'cloud platform is none'              | ['spring.main.cloud-platform=none']
    }

    void 'defaults=auto: headers bound from application configuration are explicit and survive proxy detection'() {
        expect:
        new WebApplicationContextRunner()
                .withPropertyValues('grails.security.headers.defaults=auto',
                        'grails.security.headers.hsts.enabled=true',
                        'grails.security.headers.content-security-policy.enabled=true',
                        "grails.security.headers.content-security-policy.value=default-src 'self'")
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    def request = new MockHttpServletRequest('GET', '/')
                    request.addHeader('X-Forwarded-Proto', 'https')
                    request.addHeader('X-Forwarded-For', '192.0.2.60')
                    def response = new MockHttpServletResponse()
                    context.getBean(GrailsSecurityHeadersFilter).doFilter(request, response, new MockFilterChain())
                    assert response.getHeader('Strict-Transport-Security') == 'max-age=31536000'
                    assert response.getHeader('Content-Security-Policy') == "default-src 'self'"
                    assert DEFAULT_HEADER_NAMES.every { response.getHeader(it) == null }
                }
    }

    @Unroll
    void 'the defaults mode #mode binds from application configuration and a proxied request #outcome the defaults'() {
        expect:
        new WebApplicationContextRunner()
                .withPropertyValues("grails.security.headers.defaults=${mode}")
                .withConfiguration(AutoConfigurations.of(GrailsSecurityHeadersAutoConfiguration))
                .run { context ->
                    assert context.getBean(GrailsSecurityHeadersProperties).defaults == expectedMode
                    def request = new MockHttpServletRequest('GET', '/')
                    request.addHeader('Via', '1.1 proxy')
                    def response = new MockHttpServletResponse()
                    context.getBean(GrailsSecurityHeadersFilter).doFilter(request, response, new MockFilterChain())
                    assert DEFAULT_HEADER_NAMES.every { (response.getHeader(it) != null) == defaultsSent }
                }

        where:
        mode     | expectedMode                                      | defaultsSent
        'always' | GrailsSecurityHeadersProperties.Defaults.ALWAYS   | true
        'auto'   | GrailsSecurityHeadersProperties.Defaults.AUTO     | false
        'never'  | GrailsSecurityHeadersProperties.Defaults.NEVER    | false
        outcome = defaultsSent ? 'receives' : 'does not receive'
    }

    @Configuration(proxyBeanMethods = false)
    static class UserFilterAndRegistration {

        @Bean
        GrailsSecurityHeadersFilter mySecurityHeaders() {
            new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties())
        }

        @Bean
        FilterRegistrationBean<GrailsSecurityHeadersFilter> mySecurityHeadersRegistration(
                GrailsSecurityHeadersFilter mySecurityHeaders) {
            def registration = new FilterRegistrationBean<GrailsSecurityHeadersFilter>(mySecurityHeaders)
            registration.addUrlPatterns('/api/*')
            registration
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InlineFilterRegistration {

        @Bean
        FilterRegistrationBean<GrailsSecurityHeadersFilter> inlineSecurityHeadersRegistration() {
            new FilterRegistrationBean<GrailsSecurityHeadersFilter>(
                    new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()))
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UnrelatedFilterRegistrations {

        @Bean
        FilterRegistrationBean<CharacterEncodingFilter> encodingRegistration() {
            new FilterRegistrationBean<CharacterEncodingFilter>(new CharacterEncodingFilter())
        }

        @Bean
        FilterRegistrationBean rawRegistration() {
            new FilterRegistrationBean(new CharacterEncodingFilter())
        }
    }
}
