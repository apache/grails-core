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
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import grails.web.mapping.exceptions.UrlMappingException
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import java.lang.reflect.InvocationTargetException
import org.apache.grails.core.testing.support.LogCapture
import org.codehaus.groovy.runtime.InvokerInvocationException
import org.grails.exceptions.reporting.DefaultStackTraceFilterer
import org.apache.grails.core.GrailsBootstrapRegistryInitializer
import org.grails.exceptions.reporting.StackTraceFilterer
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.mapping.mvc.GrailsControllerUrlMappings
import org.grails.web.servlet.mvc.GrailsDispatcherServlet
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.mvc.exceptions.ControllerExecutionException
import org.grails.web.servlet.view.CompositeViewResolver
import org.grails.web.util.WebUtils as GrailsWebUtils
import org.springframework.beans.factory.BeanNotOfRequiredTypeException
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import org.springframework.context.support.StaticApplicationContext
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockRequestDispatcher
import org.springframework.mock.web.MockServletConfig
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.context.request.async.AsyncRequestTimeoutException
import org.springframework.web.filter.ServerHttpObservationFilter
import org.springframework.web.util.WebUtils
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.StaticWebApplicationContext
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping
import org.springframework.web.servlet.mvc.Controller
import org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver
import org.springframework.web.servlet.view.InternalResourceView
import spock.lang.Issue
import spock.lang.Specification

import jakarta.servlet.AsyncEvent
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.Servlet
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper

class GrailsExceptionResolverSpec extends Specification {

    private static final String GRAILS = 'a GrailsWebRequest'
    private static final String PLAIN_OVER_GRAILS = 'plain attributes over a stored GrailsWebRequest'
    private static final String PLAIN = 'plain attributes'
    private static final String NOTHING = 'nothing'
    private static final Exception FAILURE = new IllegalStateException('failed')
    private static final AssertionError FAILED_ASSERT = new AssertionError('failed')
    private static final StackOverflowError TOO_DEEP = new StackOverflowError('too deep')

    private final MockServletContext servletContext = new MockServletContext()
    private StaticWebApplicationContext webContext

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
        webContext?.close()
    }

    void 'an exception #description is recorded as the error of the HTTP server observation'() {
        given: 'the filter a running application has around its requests, whose registry the application need not know'
        ServerRequestObservationContext observed = null
        List<Throwable> notified = []
        def registry = registryReporting({ observed = it }, { notified << it })
        def resolver = observedResolver()
        def request = new MockHttpServletRequest('GET', '/failing')
        def response = new MockHttpServletResponse()
        FilterChain application = { req, res ->
            resolver.resolveException((HttpServletRequest) req, (HttpServletResponse) res, null, thrown)
        } as FilterChain

        when:
        new ServerHttpObservationFilter(registry).doFilter(request, response, application)

        then: 'or its metrics would say nothing failed'
        response.status == status
        observed.error.is(recorded)

        and: 'or the handler that marks the span would not hear of it'
        notified.size() == 1
        notified.first().is(recorded)

        where:
        description                                  | thrown                                                                         | recorded      | status
        'an action threw'                            | FAILURE                                                                        | FAILURE       | 500
        'wrapped by Groovy'                          | new InvokerInvocationException(new InvocationTargetException(FAILURE))         | FAILURE       | 500
        'that is an Error DispatcherServlet wrapped' | new ServletException("Handler dispatch failed: $FAILED_ASSERT", FAILED_ASSERT) | FAILED_ASSERT | 500
        'that is an Error Groovy wrapped'            | new InvokerInvocationException(TOO_DEEP)                                       | TOO_DEEP      | 500
    }

    void 'the exception an error handler answers stays the error of the HTTP server observation'() {
        given: 'the filter a running application has around its requests, and an error handler to forward to'
        ServerRequestObservationContext observed = null
        List<Throwable> notified = []
        def registry = registryReporting({ observed = it }, { notified << it })
        def resolver = resolverFor({ '500'(controller: 'errors', action: 'serverError') }, true)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(GRAILS, request, response)
        def original = new IllegalStateException('original')
        FilterChain application = { req, res ->
            resolver.resolveException((HttpServletRequest) req, (HttpServletResponse) res, null, original)
        } as FilterChain

        when:
        new ServerHttpObservationFilter(registry).doFilter(request, response, application)

        then: 'the handler answered, and the wrapper its model left on the request is not what is reported'
        response.forwardedUrl == '/errors/serverError'
        observed.error.is(original)

        and: 'the handlers hear of it once, though both the resolver and the filter could tell them'
        notified.size() == 1
        notified.first().is(original)
    }

    void 'the handlers hear once of an exception an error handler answers on an asynchronous dispatch, #where'() {
        given: 'the filter, around a request whose first dispatch started asynchronous processing'
        ServerRequestObservationContext observed = null
        List<Throwable> notified = []
        def registry = registryReporting({ observed = it }, { notified << it })
        def resolver = resolverFor({ '500'(controller: 'errors', action: 'serverError') }, true)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(GRAILS, request, response)
        def original = new IllegalStateException('original')

        when: 'the asynchronous dispatch fails, the error controller answers, and the processing completes'
        observeAsynchronousExchange(registry, request, response, { req, res ->
            request.dispatcherType = resolvedOn
            resolver.resolveException((HttpServletRequest) req, (HttpServletResponse) res, null, original)
            request.dispatcherType = DispatcherType.ASYNC
        } as FilterChain)

        then: 'the filter reads it once the processing has completed, so the resolver does not report it as well'
        response.forwardedUrl == '/errors/serverError'
        observed.error.is(original)
        notified.size() == 1
        notified.first().is(original)

        where:
        where                       | resolvedOn
        'in the action dispatched'  | DispatcherType.ASYNC
        'in an action forwarded to' | DispatcherType.FORWARD
    }

    void 'the handlers hear once of an exception an error handler answers on a request dispatch, when the request still reads as a forward afterwards'() {
        given: 'a container that leaves a forwarded request reading as a forward, so the filter reads the exception left on it'
        List<Throwable> notified = []
        def registry = registryReporting({ }, { notified << it })
        def resolver = resolverFor({ '500'(controller: 'errors', action: 'serverError') }, true)
        def request = new ForwardLeftOnRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(GRAILS, request, response)
        def original = new IllegalStateException('original')
        FilterChain application = { req, res ->
            resolver.resolveException((HttpServletRequest) req, (HttpServletResponse) res, null, original)
        } as FilterChain

        when:
        new ServerHttpObservationFilter(registry).doFilter(request, response, application)

        then:
        response.forwardedUrl == '/errors/serverError'
        request.dispatcherType == DispatcherType.FORWARD
        notified.size() == 1
        notified.first().is(original)
    }

    void 'the handlers hear once of an exception an error controller answers in a forward from a request dispatch, when #container'() {
        given: 'the Grails dispatcher servlet behind the filter, and a handler that forwards to one that fails'
        List<Throwable> notified = []
        def registry = registryReporting({ }, { notified << it })
        def resolver = resolverFor({ '500'(controller: 'errors', action: 'serverError') }, true)
        def original = new IllegalStateException('original')
        def servlet = dispatcherServletWith(resolver, [
                '/forwarding': { req, res -> req.getRequestDispatcher('/failing').forward(req, res); null } as Controller,
                '/failing'   : { req, res -> throw original } as Controller])
        def request = new ForwardingRequest(servletContext, '/forwarding', servlet, ['/forwarding', '/failing'] as Set,
                restoresForward)
        def response = new MockHttpServletResponse()

        when:
        new ServerHttpObservationFilter(registry).doFilter(request, response, { req, res ->
            servlet.service(req, res)
        } as FilterChain)

        then:
        response.forwardedUrl == '/errors/serverError'
        request.dispatcherType == (restoresForward ? DispatcherType.REQUEST : DispatcherType.FORWARD)
        notified.size() == 1
        notified.first().is(original)

        where:
        restoresForward | container
        true            | 'the container restores the request, as Tomcat and Jetty do'
        false           | 'the request still reads as the forward'
    }

    void 'the exception of an action stays the error of the observation when its error controller fails as well, and #resolvedBy resolves that'() {
        given: 'the Grails dispatcher servlet behind the filter, and an error controller that fails'
        ServerRequestObservationContext observed = null
        List<Throwable> notified = []
        def registry = registryReporting({ observed = it }, { notified << it })
        def resolver = resolverFor({ '500'(controller: 'errors', action: 'serverError') }, true)
        def original = new IllegalStateException('original')
        def servlet = dispatcherServletWith(resolver, [
                '/failing'           : { req, res -> throw original } as Controller,
                '/errors/serverError': { req, res -> throw later } as Controller],
                springResolves ? [new ResponseStatusExceptionResolver(order: Ordered.HIGHEST_PRECEDENCE)] : [])
        def request = new ForwardingRequest(servletContext, '/failing', servlet, ['/failing', '/errors/serverError'] as Set,
                true)
        def response = new MockHttpServletResponse()

        when:
        new ServerHttpObservationFilter(registry).doFilter(request, response, { req, res ->
            servlet.service(req, res)
        } as FilterChain)

        then: 'the request failed with what the action threw, not with what failed while answering it'
        observed.error.is(original)
        notified.size() == 1
        notified.first().is(original)

        where:
        resolvedBy    | springResolves | later
        'Grails'      | false          | new UnsupportedOperationException('error controller failed')
        'Spring MVC'  | true           | new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 'error controller failed')
    }

    void 'the exception of an action stays the error of the observation when #handler answers it, and #resolvedBy resolves a later failure'() {
        given: 'the Grails dispatcher servlet behind the filter, and an error page that includes a handler that fails'
        ServerRequestObservationContext observed = null
        List<Throwable> notified = []
        def registry = registryReporting({ observed = it }, { notified << it })
        def resolver = resolverFor(mappings, controllerMappings)
        def original = new IllegalStateException('original')
        def servlet = dispatcherServletWith(resolver, [
                '/failing': { req, res -> throw original } as Controller,
                (page)    : { req, res -> includeUnlessIncluded(req, res, '/later') } as Controller,
                '/later'  : { req, res -> throw later } as Controller],
                springResolves ? [new ResponseStatusExceptionResolver(order: Ordered.HIGHEST_PRECEDENCE)] : [])
        def request = new ForwardingRequest(servletContext, '/failing', servlet, ['/failing', page, '/later'] as Set, true)
        def response = new MockHttpServletResponse()

        when:
        new ServerHttpObservationFilter(registry).doFilter(request, response, { req, res ->
            servlet.service(req, res)
        } as FilterChain)

        then: 'the request failed with what the action threw, not with what failed once the error page answered it'
        observed.error.is(original)
        notified.size() == 1
        notified.first().is(original)

        where:
        handler               | mappings                                               | controllerMappings | page                  | resolvedBy   | springResolves | later
        'the error view'      | { '500'(view: '/error') }                              | false              | '/error'              | 'Grails'     | false          | new UnsupportedOperationException('later')
        'the error view'      | { '500'(view: '/error') }                              | false              | '/error'              | 'Spring MVC' | true           | new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 'later')
        'an error controller' | { '500'(controller: 'errors', action: 'serverError') } | true               | '/errors/serverError' | 'Grails'     | false          | new UnsupportedOperationException('later')
        'an error controller' | { '500'(controller: 'errors', action: 'serverError') } | true               | '/errors/serverError' | 'Spring MVC' | true           | new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 'later')
    }

    void 'the handlers hear once of an exception an error controller answers in a forward from an asynchronous dispatch'() {
        given: 'the Grails dispatcher servlet behind the filter, and a handler that forwards to one that fails'
        ServerRequestObservationContext observed = null
        List<Throwable> notified = []
        def registry = registryReporting({ observed = it }, { notified << it })
        def resolver = resolverFor({ '500'(controller: 'errors', action: 'serverError') }, true)
        def original = new IllegalStateException('original')
        def servlet = dispatcherServletWith(resolver, [
                '/forwarding': { req, res -> req.getRequestDispatcher('/failing').forward(req, res); null } as Controller,
                '/failing'   : { req, res -> throw original } as Controller])
        def request = new ForwardingRequest(servletContext, '/forwarding', servlet, ['/forwarding', '/failing'] as Set, true)
        def response = new MockHttpServletResponse()

        when:
        observeAsynchronousExchange(registry, request, response, { req, res -> servlet.service(req, res) } as FilterChain)

        then:
        response.forwardedUrl == '/errors/serverError'
        observed.error.is(original)
        notified.size() == 1
        notified.first().is(original)
    }

    void 'the handlers hear once of the exception of an action that started asynchronous processing, when #container'() {
        given: 'the filter, and an action that starts asynchronous processing before it fails'
        ServerRequestObservationContext observed = null
        List<Throwable> notified = []
        def registry = registryReporting({ observed = it }, { notified << it })
        def resolver = resolverFor({ '500'(controller: 'errors', action: 'serverError') }, true)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        request.asyncSupported = true
        def response = new MockHttpServletResponse()
        bind(GRAILS, request, response)
        def original = new IllegalStateException('original')

        when: 'the error controller answers, and the request dispatch returns with the exception left on the request'
        new ServerHttpObservationFilter(registry).doFilter(request, response, { req, res ->
            req.startAsync()
            resolver.resolveException((HttpServletRequest) req, (HttpServletResponse) res, null, original)
        } as FilterChain)

        and: 'the container reports it or not, a later failure follows, and the processing completes'
        if (reportsLeftException) {
            request.asyncContext.listeners.each { it.onError(new AsyncEvent(request.asyncContext, request, response, original)) }
        }
        if (springResolvesLater) {
            new DefaultHandlerExceptionResolver().resolveException(request, response, null, new AsyncRequestTimeoutException())
        }
        else {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException('response not usable'))
        }
        request.asyncContext.complete()

        then: 'what the action threw, once'
        response.forwardedUrl == '/errors/serverError'
        observed.error.is(original)
        notified.size() == 1
        notified.first().is(original)

        where:
        reportsLeftException | springResolvesLater | container
        true                 | false               | 'the container reports the exception left on the request, as Tomcat does'
        false                | false               | 'the container does not, as Jetty does'
        true                 | true                | 'the container reports it, and Spring MVC resolves a later failure to a server error'
        false                | true                | 'the container does not, and Spring MVC resolves a later failure to a server error'
    }

    void 'a failure that follows the one an error handler answered does not replace it, when #resolvedBy resolves it'() {
        given: 'the filter, and an action that starts asynchronous processing before it fails'
        ServerRequestObservationContext observed = null
        List<Throwable> notified = []
        def registry = registryReporting({ observed = it }, { notified << it })
        def resolver = resolverFor({ '500'(view: '/error') }, false)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(GRAILS, request, response)
        HandlerExceptionResolver timeoutResolver = resolvedBy == 'Spring MVC' ? new DefaultHandlerExceptionResolver() : resolver

        when: 'the error view answers, and the processing the action started times out afterwards'
        observeAsynchronousExchange(registry, request, response, { req, res ->
            timeoutResolver.resolveException((HttpServletRequest) req, (HttpServletResponse) res, null,
                    new AsyncRequestTimeoutException())
        } as FilterChain, { req, res ->
            resolver.resolveException((HttpServletRequest) req, (HttpServletResponse) res, null, FAILURE)
        } as FilterChain)

        then: 'the request failed with what the action threw'
        response.status == 503
        observed.error.is(FAILURE)
        notified.size() == 1
        notified.first().is(FAILURE)

        where:
        resolvedBy << ['Spring MVC', 'Grails']
    }

    void 'a filter can read the exception an error handler answered once the chain has returned, with no observation, when #description'() {
        given:
        def resolver = resolverFor({ '500'(controller: 'errors', action: 'serverError') }, true)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(GRAILS, request, response)

        when:
        resolver.resolveException(request, response, null, thrown)

        then: 'what the action threw, as the filter that observes the request reports an exception that reaches it'
        response.forwardedUrl == '/errors/serverError'
        request.getAttribute(RequestDispatcher.ERROR_EXCEPTION).is(left)

        where:
        description                                          | thrown                                                                         | left
        'an action threw an exception'                       | FAILURE                                                                        | FAILURE
        'an action threw an Error DispatcherServlet wrapped' | new ServletException("Handler dispatch failed: $FAILED_ASSERT", FAILED_ASSERT) | FAILED_ASSERT
    }

    void 'an exception #handler answers inside an include is not recorded on the observation of the page that included it'() {
        given: 'the Grails dispatcher servlet behind the filter, and a page that includes a handler that fails'
        ServerRequestObservationContext observed = null
        List<Throwable> notified = []
        def registry = registryReporting({ observed = it }, { notified << it })
        def resolver = resolverFor(mappings, controllerMappings)
        def servlet = dispatcherServletWith(resolver, [
                '/page'   : { req, res -> req.getRequestDispatcher('/failing').include(req, res); null } as Controller,
                '/failing': { req, res -> throw FAILURE } as Controller])
        def request = new ForwardingRequest(servletContext, '/page', servlet, ['/page', '/failing'] as Set, true)
        def response = new MockHttpServletResponse()

        when:
        new ServerHttpObservationFilter(registry).doFilter(request, response, { req, res ->
            servlet.service(req, res)
        } as FilterChain)

        then: 'the error page answered inside the page, included rather than forwarded to, which answered the request'
        response.status == 200
        response.includedUrls == [page]
        response.forwardedUrl == null

        and: 'so the request did not fail'
        observed.error == null
        notified.empty

        where:
        handler               | mappings                                               | controllerMappings | page
        'the error view'      | { '500'(view: '/error') }                              | false              | '/error'
        'an error controller' | { '500'(controller: 'errors', action: 'serverError') } | true               | '/errors/serverError'
    }

    void 'an exception is resolved as usual without an observation'() {
        when:
        def response = new MockHttpServletResponse()
        observedResolver().resolveException(new MockHttpServletRequest('GET', '/failing'), response, null, FAILURE)

        then:
        response.status == 500
    }

    /**
     * Runs an exchange as a container runs one whose first dispatch starts asynchronous processing: that dispatch
     * through the filter, which adds its listener once the dispatch returns, then the asynchronous dispatch, and then
     * the completion its listeners are told of.
     */
    private static void observeAsynchronousExchange(ObservationRegistry registry, MockHttpServletRequest request,
            MockHttpServletResponse response, FilterChain asyncDispatch,
            FilterChain requestDispatch = { req, res -> } as FilterChain) {
        def filter = new ServerHttpObservationFilter(registry)
        request.asyncSupported = true
        filter.doFilter(request, response, { req, res ->
            req.startAsync()
            requestDispatch.doFilter(req, res)
        } as FilterChain)
        request.asyncStarted = false
        request.dispatcherType = DispatcherType.ASYNC
        request.setAttribute(GrailsWebUtils.ASYNC_REQUEST_URI_ATTRIBUTE, request.requestURI)
        filter.doFilter(request, response, asyncDispatch)
        request.asyncContext.complete()
    }

    /** Includes a page, unless this is the include of an error page that answers a failure of that page. */
    private static ModelAndView includeUnlessIncluded(HttpServletRequest request, HttpServletResponse response, String page) {
        if (request.dispatcherType != DispatcherType.INCLUDE) {
            request.getRequestDispatcher(page).include(request, response)
        }
        null
    }

    private static ObservationRegistry registryReporting(Closure stopped, Closure errored) {
        def registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            boolean supportsContext(Observation.Context context) {
                context instanceof ServerRequestObservationContext
            }

            @Override
            void onError(Observation.Context context) {
                errored.call(context.error)
            }

            @Override
            void onStop(Observation.Context context) {
                stopped.call(context)
            }
        })
        registry
    }

    private GrailsExceptionResolver observedResolver() {
        webContext = new StaticWebApplicationContext()
        webContext.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, Stub(GrailsApplication))
        webContext.refresh()
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webContext)
        def resolver = new GrailsExceptionResolver()
        resolver.servletContext = servletContext
        resolver.defaultErrorView = '/error'
        resolver.stackFilterer = new DefaultStackTraceFilterer()
        resolver
    }

    void 'async timeouts resolve as 503 without an error stack trace'() {
        given:
        def resolverLog = new LogCapture(GrailsExceptionResolver)
        def stackLog = new LogCapture(DefaultStackTraceFilterer.STACK_LOG_NAME)
        def resolver = new GrailsExceptionResolver()
        def context = new StaticWebApplicationContext()
        context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, Stub(GrailsApplication))
        def mappings = Mock(UrlMappingsHolder)
        context.beanFactory.registerSingleton(UrlMappingsHolder.BEAN_ID, mappings)
        context.refresh()
        def servletContext = new MockServletContext()
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context)
        resolver.servletContext = servletContext
        resolver.defaultErrorView = '/error'
        def request = new MockHttpServletRequest('GET', '/slow')
        def response = new MockHttpServletResponse()

        when:
        def result = resolver.resolveException(request, response, null, new AsyncRequestTimeoutException())

        then:
        response.status == 503
        request.getAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE) == 503
        result.viewName == '/error'
        !resolverLog.events.any { it.level.toString() == 'ERROR' || it.throwableProxy != null }
        stackLog.events.empty
        2 * mappings.matchStatusCode(503, _ as Throwable) >> null
        1 * mappings.matchStatusCode(503) >> null

        cleanup:
        resolverLog.close()
        stackLog.close()
        context.close()
    }

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
        resolverLog.events.size() == 1
        resolverLog.events[0].formattedMessage.contains('[GET] /test')
        resolverLog.events[0].formattedMessage.contains('boom')
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
        resolverLog.events[0].formattedMessage.contains('[GET] /test')

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
        bind(GRAILS, request, response)

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
        bind(GRAILS, request, response)

        when: 'two errors are resolved in sequence, as an include and its enclosing request would'
        resolver.resolveViewOrForward(new RuntimeException('boom'), urlMappings, request, response,
                new ModelAndView())
        resolver.resolveViewOrForward(new RuntimeException('boom'), urlMappings, request, response,
                new ModelAndView())

        then: 'the guard only suppresses re-entry, so both are forwarded'
        forwards.size() == 2
    }

    void "an exception mapping is matched against the exception an action threw when it is #how"() {
        given:
        def resolver = resolverFor({
            '500'(controller: 'errors', action: 'ioFailure', exception: IOException)
            '500'(controller: 'errors', action: 'anyFailure')
        }, true)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(GRAILS, request, response)
        def thrown = new IOException('thrown by the action', new IllegalStateException('its own cause'))

        when:
        resolver.resolveException(request, response, null, wrap.call(thrown))

        then:
        response.forwardedUrl == '/errors/ioFailure'

        where:
        how                      | wrap
        'not wrapped'            | { Throwable it -> it }
        'wrapped by Groovy'      | { Throwable it -> new InvokerInvocationException(it) }
        'wrapped by Grails'      | { Throwable it -> new ControllerExecutionException('failed', it) }
        'wrapped by both'        | { Throwable it -> new ControllerExecutionException('failed', new InvokerInvocationException(it)) }
    }

    void "an exception mapping still matches the root cause of #what"() {
        given:
        def resolver = resolverFor({
            '500'(controller: 'errors', action: 'ioFailure', exception: IOException)
            '500'(controller: 'errors', action: 'anyFailure')
        }, true)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(GRAILS, request, response)

        when:
        resolver.resolveException(request, response, null, thrown)

        then:
        response.forwardedUrl == '/errors/ioFailure'

        where:
        what                                 | thrown
        'an exception an action threw'       | new IllegalStateException('thrown by the action', new IOException('the root cause'))
        'an exception wrapped by Groovy'     | new InvokerInvocationException(new IllegalStateException('thrown by the action', new IOException('the root cause')))
    }

    void "an Error that a wrapper holds leaves the wrapper as the exception resolved"() {
        given:
        def resolver = resolverFor({ '500'(controller: 'errors', action: 'anyFailure') }, true)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(GRAILS, request, response)

        when:
        resolver.resolveException(request, response, null, new InvokerInvocationException(new StackOverflowError('too deep')))

        then:
        response.forwardedUrl == '/errors/anyFailure'
        response.status == 500
    }

    @Issue('https://github.com/apache/grails-core/issues/16129')
    void "an error handler mapped to #handler is forwarded to with #attributes bound"() {
        given:
        def resolver = resolverFor(mappings, true)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(attributes, request, response)
        def original = new IllegalStateException('original')

        when:
        def result = resolver.resolveException(request, response, null, original)

        then: 'the error handler is dispatched with the exception being resolved'
        result.empty
        response.forwardedUrl == '/errors/serverError'
        (request.getAttribute(GrailsExceptionResolver.EXCEPTION_ATTRIBUTE) as Throwable).cause.is(original)

        where:
        handler                | attributes        | mappings
        'a controller'         | GRAILS            | { '500'(controller: 'errors', action: 'serverError') }
        'a controller'         | PLAIN_OVER_GRAILS | { '500'(controller: 'errors', action: 'serverError') }
        'a controller closure' | GRAILS            | { '500'(controller: { 'errors' }, action: 'serverError') }
        'a controller closure' | PLAIN_OVER_GRAILS | { '500'(controller: { 'errors' }, action: 'serverError') }
        'an HTTP method map'   | PLAIN_OVER_GRAILS | { '500'(controller: 'errors', action: [GET: 'serverError']) }
    }

    @Issue('https://github.com/apache/grails-core/issues/16129')
    void "the default error view renders the exception when #handler cannot be forwarded to with #attributes bound"() {
        given:
        def resolver = resolverFor(mappings, controllerMappings)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(attributes, request, response)
        def original = new IllegalStateException('original')

        when:
        def result = resolver.resolveException(request, response, null, original)

        then: 'the exception being resolved is not replaced by the failure to reach its error handler'
        result.viewName == '/error'
        response.forwardedUrl == null
        (result.model[GrailsExceptionResolver.EXCEPTION_ATTRIBUTE] as Throwable).cause.is(original)

        where:
        handler                           | attributes | controllerMappings | mappings
        'a controller'                    | PLAIN      | true               | { '500'(controller: 'errors', action: 'serverError') }
        'a controller'                    | NOTHING    | true               | { '500'(controller: 'errors', action: 'serverError') }
        'a controller closure'            | PLAIN      | true               | { '500'(controller: { 'errors' }, action: 'serverError') }
        'a controller closure'            | PLAIN      | false              | { '500'(controller: { 'errors' }, action: 'serverError') }
        'a controller closure'            | NOTHING    | true               | { '500'(controller: { 'errors' }, action: 'serverError') }
        'an HTTP method map'              | PLAIN      | true               | { '500'(controller: 'errors', action: [GET: 'serverError']) }
        'a controller closure that fails' | GRAILS     | true               | { '500'(controller: { throw new IllegalStateException('broken mapping') }) }
    }

    @Issue('https://github.com/apache/grails-core/issues/16129')
    void "an error handler mapped to a view renders with #attributes bound"() {
        given:
        def resolver = resolverFor({ '500'(view: '/serverError') }, true)
        def request = new MockHttpServletRequest(servletContext, 'GET', '/fail')
        def response = new MockHttpServletResponse()
        bind(attributes, request, response)
        def original = new IllegalStateException('original')

        when:
        def result = resolver.resolveException(request, response, null, original)

        then:
        (result.view as InternalResourceView).url == '/serverError'
        (result.model[GrailsExceptionResolver.EXCEPTION_ATTRIBUTE] as Throwable).cause.is(original)

        where:
        attributes << [GRAILS, PLAIN_OVER_GRAILS, PLAIN, NOTHING]
    }

    private GrailsExceptionResolver resolverFor(Closure mappings, boolean controllerMappings) {
        def grailsApplication = new DefaultGrailsApplication().tap {
            initialise()
        }
        def evaluatorContext = new StaticApplicationContext()
        evaluatorContext.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, grailsApplication)
        evaluatorContext.refresh()
        UrlMappingsHolder urlMappings = new DefaultUrlMappingsHolder(
                new DefaultUrlMappingEvaluator(evaluatorContext).evaluateMappings(mappings))
        if (controllerMappings) {
            urlMappings = new GrailsControllerUrlMappings(grailsApplication, urlMappings)
        }
        ViewResolver viewResolver = { String name, Locale locale -> new InternalResourceView(name) } as ViewResolver

        webContext = new StaticWebApplicationContext()
        webContext.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, grailsApplication)
        webContext.beanFactory.registerSingleton(UrlMappingsHolder.BEAN_ID, urlMappings)
        webContext.beanFactory.registerSingleton(CompositeViewResolver.BEAN_NAME,
                new CompositeViewResolver(viewResolvers: [viewResolver]))
        webContext.refresh()
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webContext)

        def resolver = new GrailsExceptionResolver()
        resolver.servletContext = servletContext
        resolver.grailsApplication = grailsApplication
        resolver.exceptionMappings = ['java.lang.Exception': '/error'] as Properties
        resolver
    }

    private GrailsDispatcherServlet dispatcherServletWith(GrailsExceptionResolver resolver, Map<String, Controller> handlers,
            List<HandlerExceptionResolver> springResolvers = []) {
        def handlerMapping = new SimpleUrlHandlerMapping(handlers)
        handlerMapping.applicationContext = webContext
        webContext.beanFactory.registerSingleton('handlerMapping', handlerMapping)
        webContext.beanFactory.registerSingleton('handlerAdapter', new SimpleControllerHandlerAdapter())
        webContext.beanFactory.registerSingleton('exceptionResolver', resolver)
        springResolvers.eachWithIndex { HandlerExceptionResolver springResolver, int i ->
            webContext.beanFactory.registerSingleton("springExceptionResolver$i", springResolver)
        }
        def servlet = new GrailsDispatcherServlet(webContext)
        servlet.init(new MockServletConfig(servletContext, 'grails'))
        servlet
    }

    /**
     * A request whose forwards and includes to the handlers of a servlet dispatch through it, and which reads as the
     * forward once it has returned, unless the container restores it, as Tomcat and Jetty do and Undertow does not.
     */
    private static class ForwardingRequest extends MockHttpServletRequest {

        final Servlet servlet
        final Set<String> handled
        final boolean restoresForward

        ForwardingRequest(ServletContext servletContext, String requestURI, Servlet servlet, Set<String> handled,
                boolean restoresForward) {
            super(servletContext, 'GET', requestURI)
            this.servlet = servlet
            this.handled = handled
            this.restoresForward = restoresForward
        }

        @Override
        RequestDispatcher getRequestDispatcher(String path) {
            ForwardingRequest forwarded = this
            new MockRequestDispatcher(path) {
                @Override
                void forward(ServletRequest request, ServletResponse response) {
                    DispatcherType dispatchedAs = forwarded.dispatcherType
                    String requestURI = forwarded.requestURI
                    forwarded.dispatcherType = DispatcherType.FORWARD
                    forwarded.requestURI = path
                    if (path in forwarded.handled) {
                        forwarded.servlet.service(request, response)
                    }
                    else {
                        super.forward(request, response)
                    }
                    if (forwarded.restoresForward) {
                        forwarded.dispatcherType = dispatchedAs
                        forwarded.requestURI = requestURI
                    }
                }

                @Override
                void include(ServletRequest request, ServletResponse response) {
                    if (!(path in forwarded.handled)) {
                        super.include(request, response)
                        return
                    }
                    DispatcherType dispatchedAs = forwarded.dispatcherType
                    forwarded.dispatcherType = DispatcherType.INCLUDE
                    forwarded.setAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE, path)
                    try {
                        forwarded.servlet.service(request, new IncludedResponse((HttpServletResponse) response))
                    }
                    finally {
                        forwarded.removeAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE)
                        forwarded.dispatcherType = dispatchedAs
                    }
                }
            }
        }
    }

    /** The response an include writes to, which cannot change the status of the response that includes it. */
    private static class IncludedResponse extends HttpServletResponseWrapper {

        IncludedResponse(HttpServletResponse response) {
            super(response)
        }

        @Override
        void setStatus(int status) {
        }
    }

    /** A request that still reads as a forward once a forward has returned, as Undertow leaves it. */
    private static class ForwardLeftOnRequest extends MockHttpServletRequest {

        ForwardLeftOnRequest(ServletContext servletContext, String method, String requestURI) {
            super(servletContext, method, requestURI)
        }

        @Override
        RequestDispatcher getRequestDispatcher(String path) {
            MockHttpServletRequest forwarded = this
            new MockRequestDispatcher(path) {
                @Override
                void forward(ServletRequest request, ServletResponse response) {
                    super.forward(request, response)
                    forwarded.dispatcherType = DispatcherType.FORWARD
                }
            }
        }
    }

    private void bind(String attributes, MockHttpServletRequest request, MockHttpServletResponse response) {
        switch (attributes) {
            case GRAILS:
                GrailsWebUtils.storeGrailsWebRequest(new GrailsWebRequest(request, response, servletContext))
                break
            case PLAIN_OVER_GRAILS:
                GrailsWebUtils.storeGrailsWebRequest(new GrailsWebRequest(request, response, servletContext))
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response))
                break
            case PLAIN:
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response))
                break
            default:
                RequestContextHolder.resetRequestAttributes()
        }
    }
}
