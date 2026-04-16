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
package org.grails.web.errors

import grails.config.Config
import grails.core.GrailsApplication
import grails.web.mapping.UrlMappingsHolder
import grails.web.mapping.exceptions.UrlMappingException
import org.grails.exceptions.reporting.DefaultStackTraceFilterer
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

import jakarta.servlet.http.HttpServletRequest

class GrailsExceptionResolverSpec extends Specification {

    def "exception not thrown if an UrlMappingException is thrown while trying to match a request uri with a UrlMappingInfo "() {
        given:
        GrailsExceptionResolver grailsExceptionResolver = new GrailsExceptionResolver()

        when:
        def urlMappingsHolder = Mock(UrlMappingsHolder)
        urlMappingsHolder.match(_ as String) >> { String uri ->
            throw new UrlMappingException('Unable to establish controller name to dispatch for')
        }
        HttpServletRequest request = new MockHttpServletRequest()
        Map params = grailsExceptionResolver.extractRequestParamsWithUrlMappingHolder(urlMappingsHolder, request)

        then:
        noExceptionThrown()
        params.isEmpty()
    }

    void "logStackTrace emits only the resolver log"() {
        given: "Captured System.err"
            def originalErr = System.err
            def baos = new ByteArrayOutputStream()
            System.setErr(new PrintStream(baos, true))

        and: "A resolver with no grailsApplication wired"
            def resolver = new GrailsExceptionResolver()
            def request = new MockHttpServletRequest('GET', '/test')
            def exception = new RuntimeException('boom')

        when:
            resolver.logStackTrace(exception, request)

        then: "Only the GrailsExceptionResolver logger emits; StackTrace logger is silent"
            System.err.flush()
            def captured = baos.toString()
            captured.contains('o.g.web.errors.GrailsExceptionResolver') ||
                captured.contains('org.grails.web.errors.GrailsExceptionResolver')
            !captured.contains('ERROR StackTrace ')

        cleanup:
            System.setErr(originalErr)
    }

    void "logFullStackTraceIfEnabled is a no-op when the opt-in property is unset"() {
        given: "Captured System.err"
            def originalErr = System.err
            def baos = new ByteArrayOutputStream()
            System.setErr(new PrintStream(baos, true))

        and: "A resolver with no grailsApplication wired"
            def resolver = new GrailsExceptionResolver()
            def exception = new RuntimeException('boom')

        when:
            resolver.logFullStackTraceIfEnabled(exception)

        then: "No StackTrace log entry is emitted"
            System.err.flush()
            !baos.toString().contains('ERROR StackTrace ')

        cleanup:
            System.setErr(originalErr)
    }

    void "getRequestLogMessage appends auditor when logAuditor is enabled and the lookup returns a value"() {
        given:
            def config = Mock(Config)
            config.getProperty('grails.exceptionresolver.logAuditor', Boolean, true) >> true
            config.getProperty('grails.exceptionresolver.logRequestParameters', Boolean, _) >> false
            def grailsApp = Mock(GrailsApplication)
            grailsApp.getConfig() >> config
            def resolver = new GrailsExceptionResolver()
            resolver.grailsApplication = grailsApp
            resolver.auditorAwareLookup = new AuditorAwareLookup(null) {
                @Override
                Optional<?> getCurrentAuditor() { Optional.of('alice') }
            }
            def request = new MockHttpServletRequest('GET', '/test')

        when:
            def msg = resolver.getRequestLogMessage('RuntimeException', request, 'boom')

        then:
            msg.contains('(user: alice)')
    }

    void "getRequestLogMessage omits auditor when logAuditor is disabled"() {
        given:
            def config = Mock(Config)
            config.getProperty('grails.exceptionresolver.logAuditor', Boolean, true) >> false
            config.getProperty('grails.exceptionresolver.logRequestParameters', Boolean, _) >> false
            def grailsApp = Mock(GrailsApplication)
            grailsApp.getConfig() >> config
            def resolver = new GrailsExceptionResolver()
            resolver.grailsApplication = grailsApp
            resolver.auditorAwareLookup = new AuditorAwareLookup(null) {
                @Override
                Optional<?> getCurrentAuditor() { Optional.of('alice') }
            }
            def request = new MockHttpServletRequest('GET', '/test')

        when:
            def msg = resolver.getRequestLogMessage('RuntimeException', request, 'boom')

        then:
            !msg.contains('(user:')
    }

    void "getRequestLogMessage omits auditor when logAuditor is enabled but auditor is absent"() {
        given:
            def config = Mock(Config)
            config.getProperty('grails.exceptionresolver.logAuditor', Boolean, true) >> true
            config.getProperty('grails.exceptionresolver.logRequestParameters', Boolean, _) >> false
            def grailsApp = Mock(GrailsApplication)
            grailsApp.getConfig() >> config
            def resolver = new GrailsExceptionResolver()
            resolver.grailsApplication = grailsApp
            resolver.auditorAwareLookup = new AuditorAwareLookup(null) {
                @Override
                Optional<?> getCurrentAuditor() { Optional.empty() }
            }
            def request = new MockHttpServletRequest('GET', '/test')

        when:
            def msg = resolver.getRequestLogMessage('RuntimeException', request, 'boom')

        then:
            !msg.contains('(user:')
    }

    void "AuditorAwareLookup returns empty when no application context is provided"() {
        given:
            def lookup = new AuditorAwareLookup(null)

        expect:
            !lookup.getCurrentAuditor().isPresent()
    }

    void "logFullStackTraceIfEnabled emits the unfiltered trace when opt-in is enabled, and filterStackTrace then removes internal frames so the resolver log only sees the filtered trace"() {
        given: "Captured System.err"
            def originalErr = System.err
            def baos = new ByteArrayOutputStream()
            System.setErr(new PrintStream(baos, true))

        and: "A resolver whose config opts in to full stack trace logging"
            def config = Mock(Config)
            config.getProperty('grails.exceptionresolver.logFullStackTrace', Boolean, false) >> true
            config.getProperty('grails.exceptionresolver.logAuditor', Boolean, true) >> false
            config.getProperty('grails.exceptionresolver.logRequestParameters', Boolean, _) >> false
            config.getProperty('grails.logging.stackTraceFiltererClass', Class, _) >>
                DefaultStackTraceFilterer
            def grailsApp = Mock(GrailsApplication)
            grailsApp.getConfig() >> config
            def resolver = new GrailsExceptionResolver()
            resolver.grailsApplication = grailsApp

        and: "An exception with a mix of internal (filterable) and application frames"
            def exception = new RuntimeException('boom')
            exception.stackTrace = [
                new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 580),
                new StackTraceElement('com.example.MyController', 'show', 'MyController.groovy', 10),
            ] as StackTraceElement[]
            def request = new MockHttpServletRequest('GET', '/test')

        when: "The real resolveException ordering runs: log full trace, filter, then log with request context"
            resolver.logFullStackTraceIfEnabled(exception)
            resolver.filterStackTrace(exception)
            resolver.logStackTrace(exception, request)

        then: "Both loggers emit"
            System.err.flush()
            def captured = baos.toString()
            captured.contains('ERROR StackTrace ')
            captured.contains('Full Stack Trace:')
            captured.contains('o.g.web.errors.GrailsExceptionResolver') ||
                captured.contains('org.grails.web.errors.GrailsExceptionResolver')

        and: "The application frame appears in both log entries"
            captured.count('com.example.MyController.show(MyController.groovy:10)') == 2

        and: "The internal frame appears only once — in the unfiltered StackTrace entry, not in the filtered resolver entry"
            captured.count('java.lang.reflect.Method.invoke(Method.java:580)') == 1

        cleanup:
            System.setErr(originalErr)
    }
}
