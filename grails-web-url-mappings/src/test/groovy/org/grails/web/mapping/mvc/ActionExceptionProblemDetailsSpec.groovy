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
package org.grails.web.mapping.mvc

import groovy.json.JsonSlurper
import org.codehaus.groovy.runtime.InvokerInvocationException

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import ch.qos.logback.classic.Level
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry

import org.springframework.core.env.MapPropertySource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.ErrorResponse
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.context.request.async.AsyncRequestTimeoutException
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.filter.ServerHttpObservationFilter
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import org.apache.grails.core.testing.support.LogCapture
import org.grails.core.DefaultGrailsControllerClass
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import spock.lang.Specification

/**
 * An exception a controller action throws is written as a problem where {@code grails.exceptionresolver.problemDetails}
 * is set, by Spring MVC's translation and its handler adapter, unless the application's error page is for it.
 */
class ActionExceptionProblemDetailsSpec extends Specification {

    private GenericWebApplicationContext context

    private MockHttpServletRequest request

    private MockHttpServletResponse response

    private GrailsWebRequest webRequest

    private List<ErrorResponse.Interceptor> interceptors = []

    void setup() {
        request = new MockHttpServletRequest(new MockServletContext())
        request.requestURI = '/problem/index'
        response = new MockHttpServletResponse()
        webRequest = new GrailsWebRequest(request, response, request.servletContext)
        request.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, webRequest)
    }

    void cleanup() {
        context?.close()
    }

    void 'with the setting, an exception is written as a problem that does not expose its message, to a client that #client'() {
        given:
        def resolver = resolver([problemDetails: true])
        if (accept) {
            request.addHeader(HttpHeaders.ACCEPT, accept)
        }

        when:
        def modelAndView = resolver.resolveException(request, response, action(new ProblemController()), new IllegalStateException('secret'))

        then:
        modelAndView != null
        modelAndView.empty
        response.status == 500
        response.contentType == 'application/problem+json'
        problem() == [title: 'Internal Server Error', status: 500]
        !response.contentAsString.contains('secret')

        and: 'no view is rendered after it'
        !webRequest.renderView

        where:
        client               | accept
        'names no type'      | null
        'accepts any type'   | '*/*'
        'asks for JSON'      | 'application/json'
        'asks for text'      | 'text/plain'
        'asks for JSON first' | 'application/json, text/html;q=0.9'
    }

    void 'an exception is left to the other resolvers for a client that prefers HTML: #accept'() {
        given:
        def resolver = resolver([problemDetails: true])
        request.addHeader(HttpHeaders.ACCEPT, accept)

        expect:
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalStateException('secret')) == null
        response.status == 200
        !response.contentAsString

        where:
        accept << ['text/html', 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8', 'application/json;q=0.5, text/html']
    }

    void 'without the setting, an exception is left to the other resolvers'() {
        given:
        def resolver = resolver(setting)

        expect:
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalStateException('secret')) == null
        response.status == 200

        where:
        setting << [[:], [problemDetails: false]]
    }

    void 'a handler that is not an action is left to the other resolvers'() {
        given:
        def resolver = resolver([problemDetails: true])

        expect:
        resolver.resolveException(request, response, new Object(), new IllegalStateException('secret')) == null
    }

    void 'an exception after the response is committed is left to the other resolvers'() {
        given:
        def resolver = resolver([problemDetails: true])
        response.committed = true

        expect:
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalStateException('secret')) == null
    }

    void 'the problem has the status and detail #description declares'() {
        given:
        def resolver = resolver([problemDetails: true])

        when:
        resolver.resolveException(request, response, action(new ProblemController()), exception)

        then:
        response.status == status
        problem().detail == detail

        where:
        description               | exception                                                           | status | detail
        'a ResponseStatusException' | new ResponseStatusException(HttpStatus.GONE, 'Withdrawn')         | 410    | 'Withdrawn'
        '@ResponseStatus'          | new TeapotException()                                               | 418    | 'Short and stout'
        '@ResponseStatus of a cause' | new RuntimeException(new TeapotException())                       | 418    | 'Short and stout'
        'an ErrorResponse'          | new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, 'Later'), null) | 503 | 'Later'
    }

    void 'an exception wrapped by a dynamic invocation is written as the exception the action threw'() {
        given:
        def resolver = resolver([problemDetails: true])
        def thrown = new ErrorResponseException(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, 'Taken'), null)

        when:
        resolver.resolveException(request, response, action(new ProblemController()), new InvokerInvocationException(thrown))

        then:
        response.status == 409
        problem().detail == 'Taken'
    }

    void 'a request that can no longer be answered writes nothing'() {
        given:
        def resolver = resolver([problemDetails: true])

        when:
        def modelAndView = resolver.resolveException(request, response, action(new ProblemController()), new AsyncRequestNotUsableException('gone'))

        then:
        modelAndView.empty
        !response.contentAsString
    }

    void 'an exception a URL mapping names, or whose cause it names, is left to the Grails exception resolver'() {
        given:
        def resolver = resolver([problemDetails: true]) {
            "500"(controller: 'errors', action: 'denied', exception: SecurityException)
            "500"(controller: 'errors', action: 'serverError')
        }

        expect:
        resolver.resolveException(request, response, action(new ProblemController()), new SecurityException('denied')) == null
        resolver.resolveException(request, response, action(new ProblemController()), new RuntimeException(new SecurityException('denied'))) == null

        and: 'a mapping for the status alone is the error page of a client that prefers HTML'
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalStateException('secret')) != null
        response.status == 500
    }

    void 'the exception handlers of the advice go first, with #settings'() {
        given:
        def resolver = resolver(settings)

        when:
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalArgumentException('advised'))

        then:
        response.status == 422
        problem().detail == 'advised'

        when: 'none of them handles it'
        response = new MockHttpServletResponse()
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalStateException('secret'))

        then:
        response.status == 500
        problem() == [title: 'Internal Server Error', status: 500]

        where:
        settings << [[problemDetails: true], [springHandlers: true, problemDetails: true]]
    }

    void 'an application that turns the exception handlers off gets the problem without them'() {
        given:
        def resolver = resolver([springHandlers: false, problemDetails: true])

        when:
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalArgumentException('advised'))

        then:
        response.status == 500
        problem() == [title: 'Internal Server Error', status: 500]
    }

    void 'a controller the request has not created yet does not stop an exception being written'() {
        given:
        def resolver = resolver([problemDetails: true])
        def info = new GrailsControllerUrlMappingInfo(new DefaultGrailsControllerClass(ProblemController), Stub(UrlMappingInfo) {
            getActionName() >> 'index'
        })

        when:
        resolver.resolveException(request, response, info, new IllegalStateException('secret'))

        then:
        response.status == 500
        problem().status == 500
    }

    void 'a server error is recorded on the HTTP server observation, and a client error is not'() {
        given:
        def resolver = resolver([problemDetails: true])
        def observation = new ServerRequestObservationContext(request, response)
        request.setAttribute(ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observation)
        def serverError = new IllegalStateException('secret')

        when:
        resolver.resolveException(request, response, action(new ProblemController()), new ResponseStatusException(HttpStatus.GONE))

        then:
        observation.error == null

        when:
        response = new MockHttpServletResponse()
        resolver.resolveException(request, response, action(new ProblemController()), serverError)

        then:
        observation.error.is(serverError)
    }

    void 'the handlers of the observation, one of which marks the span, are told of a server error and not of a client error'() {
        given: 'the filter a running application has around its requests'
        List<Throwable> notified = []
        def registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            boolean supportsContext(Observation.Context context) {
                context instanceof ServerRequestObservationContext
            }

            @Override
            void onError(Observation.Context context) {
                notified << context.error
            }
        })
        def resolver = resolver([problemDetails: true])
        def handler = action(new ProblemController())
        FilterChain application = { req, res ->
            resolver.resolveException((HttpServletRequest) req, (HttpServletResponse) res, handler, thrown)
        } as FilterChain

        when:
        new ServerHttpObservationFilter(registry).doFilter(request, response, application)

        then:
        notified == (told ? [thrown] : [])

        where:
        thrown                                              | told
        new IllegalStateException('secret')                 | true
        new ResponseStatusException(HttpStatus.BAD_REQUEST) | false
    }

    void 'the request of an unexpected exception is logged, and that of an exception raised on purpose is not'() {
        given:
        def resolver = resolver([problemDetails: true])
        def logCapture = new LogCapture(ActionExceptionHandlerResolver)
        request.method = 'GET'

        when:
        resolver.resolveException(request, response, action(new ProblemController()),
                new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE))

        then:
        response.status == 503
        logCapture.events.empty

        when:
        response = new MockHttpServletResponse()
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalStateException('secret'))

        then:
        logCapture.events*.formattedMessage == ['IllegalStateException occurred when processing request: [GET] /problem/index']

        cleanup:
        logCapture.close()
    }

    void 'an ErrorResponse.Interceptor sees the problem, as it sees one a handler method returns'() {
        given:
        interceptors = [new ErrorResponse.Interceptor() {
            @Override
            void handleError(ProblemDetail detail, ErrorResponse errorResponse) {
                detail.instance = URI.create('urn:problem:1')
            }
        }]
        def resolver = resolver([problemDetails: true])

        when:
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalStateException('secret'))

        then:
        problem().instance == 'urn:problem:1'
    }

    void 'a failure to write the problem leaves the exception to the other resolvers, which can still render a view'() {
        given:
        def resolver = resolver([problemDetails: true])
        def failing = Spy(response) {
            getOutputStream() >> { throw new IOException('closed') }
        }

        expect:
        resolver.resolveException(request, failing, action(new ProblemController()), new IllegalStateException('secret')) == null
        webRequest.renderView
    }

    void 'a client that disconnects while the problem is written gets nothing more, and nothing is logged as a failure'() {
        given:
        def resolver = resolver([problemDetails: true])
        def logCapture = new LogCapture(ActionExceptionHandlerResolver)
        def disconnected = Spy(response) {
            getOutputStream() >> { throw new IOException('Broken pipe') }
        }

        when:
        def modelAndView = resolver.resolveException(request, disconnected, action(new ProblemController()), new IllegalStateException('secret'))

        then:
        modelAndView.empty
        !logCapture.events.any { it.level.isGreaterOrEqual(Level.WARN) && !it.formattedMessage.contains('occurred when processing request') }

        cleanup:
        logCapture.close()
    }

    void 'a disconnected client is not logged as a request that failed'() {
        given:
        def resolver = resolver([problemDetails: true])
        def logCapture = new LogCapture(ActionExceptionHandlerResolver)

        when:
        resolver.resolveException(request, response, action(new ProblemController()), new IOException('Broken pipe'))

        then:
        response.status == 500
        !logCapture.events.any { it.level.isGreaterOrEqual(Level.WARN) }

        cleanup:
        logCapture.close()
    }

    void 'the request of a forwarded action is logged by the URI the client asked for'() {
        given:
        def resolver = resolver([problemDetails: true])
        def logCapture = new LogCapture(ActionExceptionHandlerResolver)
        request.method = 'GET'
        request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, '/asked/for')

        when:
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalStateException('secret'))

        then:
        logCapture.events*.formattedMessage == ['IllegalStateException occurred when processing request: [GET] /asked/for']

        cleanup:
        logCapture.close()
    }

    void 'an action a page includes is left to the other resolvers, so that a problem is not written into the page'() {
        given:
        def resolver = resolver([problemDetails: true])
        request.setAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE, '/problem/index')

        expect:
        resolver.resolveException(request, response, action(new ProblemController()), new IllegalStateException('secret')) == null
        !response.contentAsString
    }

    void 'an exception handler method that fails does not stop the problem being written in another type'() {
        given:
        def resolver = resolver([problemDetails: true])

        when:
        resolver.resolveException(request, response, action(new ProblemController()), new UnsupportedOperationException('csv'))

        then:
        response.status == 500
        response.contentType == 'application/problem+json'
    }

    void 'an asynchronous timeout a URL mapping names under its own status is left to the Grails exception resolver'() {
        given:
        def resolver = resolver([problemDetails: true]) {
            "503"(controller: 'errors', action: 'timedOut', exception: AsyncRequestTimeoutException)
        }

        expect:
        resolver.resolveException(request, response, action(new ProblemController()), new AsyncRequestTimeoutException()) == null
    }

    void 'an asynchronous timeout no URL mapping names is written as a problem'() {
        given:
        def resolver = resolver([problemDetails: true])

        when:
        resolver.resolveException(request, response, action(new ProblemController()), new AsyncRequestTimeoutException())

        then:
        response.status == 503
        problem().status == 503
    }

    private Map problem() {
        new JsonSlurper().parseText(response.contentAsString) as Map
    }

    private ActionExceptionHandlerResolver resolver(Map<String, Boolean> settings, Closure mappings = null) {
        context = new GenericWebApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test',
                settings.collectEntries { key, value -> ["grails.exceptionresolver.$key".toString(), value] } as Map<String, Object>))
        List<HttpMessageConverter<?>> converters = [new JacksonJsonHttpMessageConverter()]
        context.registerBean(ProblemControllerAdvice)
        context.registerBean(ExceptionHandlerExceptionResolver, { new ExceptionHandlerExceptionResolver(messageConverters: converters) })
        context.registerBean('requestMappingHandlerAdapter', RequestMappingHandlerAdapter, {
            new RequestMappingHandlerAdapter(messageConverters: converters, errorResponseInterceptors: interceptors)
        })
        if (mappings != null) {
            def grailsContext = new MockApplicationContext()
            grailsContext.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
            def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(grailsContext).evaluateMappings(mappings))
            context.registerBean(UrlMappingsHolder.BEAN_ID, UrlMappingsHolder, { holder })
        }
        context.registerBean(ActionExceptionHandlerResolver)
        context.refresh()
        context.getBean(ActionExceptionHandlerResolver)
    }

    private GrailsControllerUrlMappingInfo action(Object controller) {
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller)
        new GrailsControllerUrlMappingInfo(new DefaultGrailsControllerClass(controller.getClass()), Stub(UrlMappingInfo) {
            getActionName() >> 'index'
        })
    }
}

class ProblemController {

    def index() {
    }
}

@ResponseStatus(code = HttpStatus.I_AM_A_TEAPOT, reason = 'Short and stout')
class TeapotException extends RuntimeException {
}

@ControllerAdvice(assignableTypes = ProblemController)
class ProblemControllerAdvice {

    @ExceptionHandler(IllegalArgumentException)
    ProblemDetail unprocessable(IllegalArgumentException e) {
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.message)
    }

    @ExceptionHandler(value = UnsupportedOperationException, produces = 'text/csv')
    String failing(UnsupportedOperationException e) {
        throw e
    }
}
