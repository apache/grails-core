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
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import grails.web.mapping.exceptions.UrlMappingException
import org.apache.grails.core.testing.support.LogCapture
import org.grails.exceptions.reporting.DefaultStackTraceFilterer
import org.apache.grails.core.GrailsBootstrapRegistryInitializer
import org.grails.exceptions.reporting.StackTraceFilterer
import org.springframework.beans.factory.BeanNotOfRequiredTypeException
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.ModelAndView
import spock.lang.Specification

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

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
        given: "captures of both the resolver logger and the StackTrace logger"
        def resolverLog = new LogCapture(GrailsExceptionResolver)
        def stackLog = new LogCapture(DefaultStackTraceFilterer.STACK_LOG_NAME)

        and: "A resolver with no grailsApplication wired"
        def resolver = new GrailsExceptionResolver()
        def request = new MockHttpServletRequest('GET', '/test')
        def exception = new RuntimeException('boom')

        when:
        resolver.logStackTrace(exception, request)

        then: "Only the GrailsExceptionResolver logger emits; StackTrace logger is silent"
        resolverLog.events.any { it.loggerName == GrailsExceptionResolver.name }
        stackLog.events.isEmpty()

        cleanup:
        resolverLog.close()
        stackLog.close()
    }

    void "logFullStackTraceIfEnabled is a no-op when the opt-in property is unset"() {
        given: "a capture of the StackTrace logger"
        def stackLog = new LogCapture(DefaultStackTraceFilterer.STACK_LOG_NAME)

        and: "A resolver with no grailsApplication wired"
        def resolver = new GrailsExceptionResolver()
        def exception = new RuntimeException('boom')

        when:
        resolver.logFullStackTraceIfEnabled(exception)

        then: "No StackTrace log entry is emitted"
        stackLog.events.isEmpty()

        cleanup:
        stackLog.close()
    }

    void "getRequestLogMessage appends auditor when logAuditor is enabled and the lookup returns a value"() {
        given:
        def config = Mock(Config)
        config.getProperty('grails.exceptionresolver.logAuditor', Boolean, false) >> true
        config.getProperty('grails.exceptionresolver.logRemoteAddr', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTrace', Boolean, false) >> false
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
        config.getProperty('grails.exceptionresolver.logAuditor', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logRemoteAddr', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTrace', Boolean, false) >> false
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
        config.getProperty('grails.exceptionresolver.logAuditor', Boolean, false) >> true
        config.getProperty('grails.exceptionresolver.logRemoteAddr', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTrace', Boolean, false) >> false
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

    void "getRequestLogMessage appends remote address when logRemoteAddr is enabled"() {
        given:
        def config = Mock(Config)
        config.getProperty('grails.exceptionresolver.logAuditor', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logRemoteAddr', Boolean, false) >> true
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTrace', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logRequestParameters', Boolean, _) >> false
        def grailsApp = Mock(GrailsApplication)
        grailsApp.getConfig() >> config
        def resolver = new GrailsExceptionResolver()
        resolver.grailsApplication = grailsApp
        def request = new MockHttpServletRequest('GET', '/test')
        request.remoteAddr = '198.51.100.42'

        when:
        def msg = resolver.getRequestLogMessage('RuntimeException', request, 'boom')

        then:
        msg.contains('(ip: 198.51.100.42)')
        !msg.contains('user:')
    }

    void "getRequestLogMessage combines remote address and auditor into a single clause when both are enabled"() {
        given:
        def config = Mock(Config)
        config.getProperty('grails.exceptionresolver.logAuditor', Boolean, false) >> true
        config.getProperty('grails.exceptionresolver.logRemoteAddr', Boolean, false) >> true
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTrace', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logRequestParameters', Boolean, _) >> false
        def grailsApp = Mock(GrailsApplication)
        grailsApp.getConfig() >> config
        def resolver = new GrailsExceptionResolver()
        resolver.grailsApplication = grailsApp
        resolver.auditorAwareLookup = new AuditorAwareLookup(null) {

            @Override
            Optional<?> getCurrentAuditor() { Optional.of(42L) }
        }
        def request = new MockHttpServletRequest('GET', '/test')
        request.remoteAddr = '198.51.100.42'

        when:
        def msg = resolver.getRequestLogMessage('RuntimeException', request, 'boom')

        then:
        msg.contains('(ip: 198.51.100.42, user: 42)')
    }

    void "subclasses can override resolveRemoteAddr to supply a custom IP extraction"() {
        given:
        def config = Mock(Config)
        config.getProperty('grails.exceptionresolver.logAuditor', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logRemoteAddr', Boolean, false) >> true
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTrace', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logRequestParameters', Boolean, _) >> false
        def grailsApp = Mock(GrailsApplication)
        grailsApp.getConfig() >> config
        def resolver = new GrailsExceptionResolver() {

            @Override
            protected String resolveRemoteAddr(HttpServletRequest req) {
                req.getHeader('X-Forwarded-For') ?: req.remoteAddr
            }
        }
        resolver.grailsApplication = grailsApp
        def request = new MockHttpServletRequest('GET', '/test')
        request.remoteAddr = '10.0.0.1'
        request.addHeader('X-Forwarded-For', '203.0.113.7')

        when:
        def msg = resolver.getRequestLogMessage('RuntimeException', request, 'boom')

        then:
        msg.contains('(ip: 203.0.113.7)')
    }

    void "AuditorAwareLookup returns empty when no application context is provided"() {
        given:
        def lookup = new AuditorAwareLookup(null)

        expect:
        !lookup.getCurrentAuditor().isPresent()
    }

    void "logFullStackTraceIfEnabled emits the unfiltered trace when opt-in is enabled, and filterStackTrace then removes internal frames so the resolver log only sees the filtered trace"() {
        given: "captures of both the resolver logger and the StackTrace logger"
        def resolverLog = new LogCapture(GrailsExceptionResolver)
        def stackLog = new LogCapture(DefaultStackTraceFilterer.STACK_LOG_NAME)

        and: "A resolver whose config opts in to full stack trace logging"
        def config = Mock(Config)
        config.getProperty('grails.exceptionresolver.logFullStackTrace', Boolean, false) >> true
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
        config.getProperty('grails.exceptionresolver.logAuditor', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logRemoteAddr', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
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

        then: "Both loggers emit exactly once"
        stackLog.events.size() == 1
        resolverLog.events.size() == 1
        stackLog.events[0].formattedMessage.contains(StackTraceFilterer.FULL_STACK_TRACE_MESSAGE)
        resolverLog.events[0].loggerName == GrailsExceptionResolver.name

        and: "The application frame appears in both the unfiltered and filtered log entries"
        [stackLog.events[0], resolverLog.events[0]].every { event ->
            event.throwableProxy.stackTraceElementProxyArray.any {
                it.stackTraceElement.className == 'com.example.MyController'
            }
        }

        and: "The internal frame appears only in the unfiltered StackTrace entry, not in the filtered resolver entry"
        stackLog.events[0].throwableProxy.stackTraceElementProxyArray.any {
            it.stackTraceElement.className == 'java.lang.reflect.Method'
        }
        resolverLog.events[0].throwableProxy.stackTraceElementProxyArray.every {
            it.stackTraceElement.className != 'java.lang.reflect.Method'
        }

        cleanup:
        resolverLog.close()
        stackLog.close()
    }

    void "getRequestLogMessage masks excluded request parameters case-insensitively"() {
        given:
        def config = Mock(Config)
        config.getProperty('grails.exceptionresolver.logRequestParameters', Boolean, _) >> true
        config.getProperty('grails.exceptionresolver.params.exclude', List, _) >> [null, 'password', 'token']
        config.getProperty('grails.exceptionresolver.logAuditor', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logRemoteAddr', Boolean, false) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
        config.getProperty('grails.exceptionresolver.logFullStackTrace', Boolean, false) >> false
        def grailsApp = Mock(GrailsApplication)
        grailsApp.getConfig() >> config
        def resolver = new GrailsExceptionResolver()
        resolver.grailsApplication = grailsApp
        def request = new MockHttpServletRequest('POST', '/login')
        request.addParameter('Password', 'secret')
        request.addParameter('apiToken', 'visible')
        request.addParameter('TOKEN', 'abc123')
        request.addParameter('username', 'sherlock')

        when:
        def msg = resolver.getRequestLogMessage('RuntimeException', request, 'boom')

        then:
        msg.contains('Password: ***')
        msg.contains('TOKEN: ***')
        msg.contains('username: sherlock')
        msg.contains('apiToken: visible')
        !msg.contains('Password: secret')
        !msg.contains('TOKEN: abc123')
    }

    void "createStackFilterer reuses the StackTraceFilterer promoted by GrailsBootstrapRegistryInitializer instead of building a second copy"() {
        given:
        def promoted = new DefaultStackTraceFilterer()
        def mainContext = Mock(ApplicationContext)
        mainContext.getBean(GrailsBootstrapRegistryInitializer.STACK_TRACE_FILTERER_BEAN_NAME, StackTraceFilterer) >> promoted
        def grailsApp = Mock(GrailsApplication)
        grailsApp.getMainContext() >> mainContext
        def resolver = new GrailsExceptionResolver()
        resolver.grailsApplication = grailsApp

        when:
        resolver.createStackFilterer()

        then: 'the promoted bean is reused verbatim'
        resolver.stackFilterer.is(promoted)

        and: 'config is never consulted since the promoted bean already had it applied at bootstrap time'
        0 * grailsApp.getConfig()
    }

    void "createStackFilterer falls back to building from config when no StackTraceFilterer bean is promoted"() {
        given:
        def config = Mock(Config)
        config.getProperty('grails.logging.stackTraceFiltererClass', Class, DefaultStackTraceFilterer) >> DefaultStackTraceFilterer
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> true
        def mainContext = Mock(ApplicationContext)
        mainContext.getBean(GrailsBootstrapRegistryInitializer.STACK_TRACE_FILTERER_BEAN_NAME, StackTraceFilterer) >> { throw new NoSuchBeanDefinitionException(GrailsBootstrapRegistryInitializer.STACK_TRACE_FILTERER_BEAN_NAME) }
        def grailsApp = Mock(GrailsApplication)
        grailsApp.getMainContext() >> mainContext
        grailsApp.getConfig() >> config
        def resolver = new GrailsExceptionResolver()
        resolver.grailsApplication = grailsApp

        when:
        resolver.createStackFilterer()

        then:
        resolver.stackFilterer instanceof DefaultStackTraceFilterer
    }

    void "createStackFilterer falls back to building from config when the application has no main context yet"() {
        given:
        def config = Mock(Config)
        config.getProperty('grails.logging.stackTraceFiltererClass', Class, DefaultStackTraceFilterer) >> DefaultStackTraceFilterer
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> true
        def grailsApp = Mock(GrailsApplication)
        grailsApp.getMainContext() >> null
        grailsApp.getConfig() >> config
        def resolver = new GrailsExceptionResolver()
        resolver.grailsApplication = grailsApp

        when:
        resolver.createStackFilterer()

        then:
        noExceptionThrown()
        resolver.stackFilterer instanceof DefaultStackTraceFilterer
    }

    void "createStackFilterer falls back to building from config when a bean of an unrelated type holds the name"() {
        given: 'an application that registers its own stackTraceFilterer bean of an incompatible type'
        def config = Mock(Config)
        config.getProperty('grails.logging.stackTraceFiltererClass', Class, DefaultStackTraceFilterer) >> DefaultStackTraceFilterer
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> true
        def mainContext = Mock(ApplicationContext)
        mainContext.getBean(GrailsBootstrapRegistryInitializer.STACK_TRACE_FILTERER_BEAN_NAME, StackTraceFilterer) >> {
            throw new BeanNotOfRequiredTypeException(
                    GrailsBootstrapRegistryInitializer.STACK_TRACE_FILTERER_BEAN_NAME, StackTraceFilterer, String)
        }
        def grailsApp = Mock(GrailsApplication)
        grailsApp.getMainContext() >> mainContext
        grailsApp.getConfig() >> config
        def resolver = new GrailsExceptionResolver()
        resolver.grailsApplication = grailsApp

        when:
        resolver.createStackFilterer()

        then: 'a name collision degrades to the default rather than failing the context'
        noExceptionThrown()
        resolver.stackFilterer instanceof DefaultStackTraceFilterer
    }

    void "an error handler that fails inside its own forward is not forwarded to again"() {
        given: 'a "500" mapping onto a controller action'
        def info = Mock(UrlMappingInfo)
        info.getViewName() >> null
        info.getControllerName() >> 'errors'
        def urlMappings = Mock(UrlMappingsHolder)
        urlMappings.match(_ as String) >> null
        urlMappings.matchStatusCode(500, _ as Throwable) >> null
        urlMappings.matchStatusCode(500) >> info

        and: 'an error handler that fails again inside the dispatch it was forwarded to'
        def forwards = []
        def resolver = new GrailsExceptionResolver() {

            @Override
            protected void forwardRequest(UrlMappingInfo forwarded, HttpServletRequest req,
                    HttpServletResponse res, ModelAndView mv, String uri) {
                forwards << uri
                if (forwards.size() < 10) {
                    resolveViewOrForward(new RuntimeException('boom again'), urlMappings, req, res,
                            new ModelAndView())
                }
            }
        }
        def request = new MockHttpServletRequest('POST', '/upload/upload')
        def response = new MockHttpServletResponse()

        when:
        def result = resolver.resolveViewOrForward(new RuntimeException('boom'), urlMappings, request, response,
                new ModelAndView())

        then: 'the forwarded dispatch does not forward again, so it cannot recurse'
        forwards.size() == 1

        and: 'the outer attempt reports the error handler as having run'
        result.viewName == null
        result.model.isEmpty()
    }

    void "a later error on the same request can still be forwarded to the error handler"() {
        given: 'a "500" mapping onto a controller action, and an error handler that renders normally'
        def info = Mock(UrlMappingInfo)
        info.getViewName() >> null
        info.getControllerName() >> 'errors'
        def urlMappings = Mock(UrlMappingsHolder)
        urlMappings.match(_ as String) >> null
        urlMappings.matchStatusCode(500, _ as Throwable) >> null
        urlMappings.matchStatusCode(500) >> info

        def forwards = []
        def resolver = new GrailsExceptionResolver() {

            @Override
            protected void forwardRequest(UrlMappingInfo forwarded, HttpServletRequest req,
                    HttpServletResponse res, ModelAndView mv, String uri) {
                forwards << uri
            }
        }
        def request = new MockHttpServletRequest('POST', '/upload/upload')
        def response = new MockHttpServletResponse()

        when: 'two errors are resolved in sequence, as an include and its enclosing request would'
        resolver.resolveViewOrForward(new RuntimeException('boom'), urlMappings, request, response,
                new ModelAndView())
        resolver.resolveViewOrForward(new RuntimeException('boom'), urlMappings, request, response,
                new ModelAndView())

        then: 'the guard only suppresses re-entry, so both are forwarded'
        forwards.size() == 2
    }
}
