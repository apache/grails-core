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

import org.springframework.core.env.MapPropertySource
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver

import grails.web.Action
import grails.web.HyphenatedUrlConverter
import grails.web.mapping.UrlMappingInfo
import org.grails.core.DefaultGrailsControllerClass
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Specification

/**
 * An exception a controller action throws is resolved by the {@code @ExceptionHandler} methods Spring MVC resolves those
 * of a handler method with, where {@code grails.exceptionresolver.springHandlers} is set.
 */
class ActionExceptionHandlerResolverSpec extends Specification {

    private GenericWebApplicationContext context

    private MockHttpServletRequest request

    private MockHttpServletResponse response

    void setup() {
        request = new MockHttpServletRequest(new MockServletContext())
        request.requestURI = '/handled/index'
        response = new MockHttpServletResponse()
        new GrailsWebRequest(request, response, request.servletContext)
    }

    void cleanup() {
        context?.close()
    }

    void 'with the setting, the advice that applies to the controller handles an exception its action throws'() {
        given:
        def resolver = resolver(true)

        when:
        def modelAndView = resolver.resolveException(request, response, action(new HandledController()), new IllegalStateException('taken'))

        then: 'as Spring MVC writes what an exception handler method returns'
        modelAndView != null
        response.status == 409
        response.contentType == 'application/problem+json'
        new JsonSlurper().parseText(response.contentAsString).detail == 'taken'
    }

    void 'an exception handler method of the controller handles an exception its action throws'() {
        given:
        def resolver = resolver(true)

        when:
        resolver.resolveException(request, response, action(new HandledController()), new UnsupportedOperationException('local'))

        then:
        response.status == 501
        new JsonSlurper().parseText(response.contentAsString).detail == 'local'
    }

    void 'without the setting, the Grails 9 default, an exception is left to the other resolvers'() {
        given:
        def resolver = resolver(null)

        expect:
        resolver.resolveException(request, response, action(new HandledController()), new IllegalStateException('taken')) == null
        response.status == 200
    }

    void 'advice for another controller does not handle an exception of this one, which is left to the other resolvers'() {
        given:
        def resolver = resolver(true)

        expect:
        resolver.resolveException(request, response, action(new OtherController()), new IllegalStateException('taken')) == null
    }

    void 'an exception no handler method handles, and a handler that is not an action, are left to the other resolvers'() {
        given:
        def resolver = resolver(true)

        expect:
        resolver.resolveException(request, response, action(new HandledController()), new IllegalArgumentException()) == null
        resolver.resolveException(request, response, new Object(), new IllegalStateException('taken')) == null
    }

    void 'the advice handles an exception of an action named in a URL with the hyphenated URL converter'() {
        given:
        def resolver = resolver(true)
        def controllerClass = new DefaultGrailsControllerClass(HandledController)
        controllerClass.registerUrlConverter(new HyphenatedUrlConverter())
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, new HandledController())
        def info = new GrailsControllerUrlMappingInfo(controllerClass, Stub(UrlMappingInfo) {
            getActionName() >> 'show-book'
        })

        when:
        resolver.resolveException(request, response, info, new IllegalStateException('taken'))

        then:
        response.status == 409
    }

    private ActionExceptionHandlerResolver resolver(Boolean setting) {
        context = new GenericWebApplicationContext()
        if (setting != null) {
            context.environment.propertySources.addFirst(new MapPropertySource('test',
                    ['grails.exceptionresolver.springHandlers': setting]))
        }
        context.registerBean(HandledControllerAdvice)
        context.registerBean(ExceptionHandlerExceptionResolver, {
            new ExceptionHandlerExceptionResolver().tap {
                messageConverters = [new JacksonJsonHttpMessageConverter()] as List<HttpMessageConverter<?>>
            }
        })
        context.registerBean(ActionExceptionHandlerResolver)
        context.refresh()
        context.getBean(ActionExceptionHandlerResolver)
    }

    private GrailsControllerUrlMappingInfo action(Object controller) {
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller)
        def info = Stub(UrlMappingInfo) {
            getActionName() >> 'index'
        }
        new GrailsControllerUrlMappingInfo(new DefaultGrailsControllerClass(controller.getClass()), info)
    }
}

class HandledController {

    def index() {
    }

    @Action
    def showBook() {
    }

    @ExceptionHandler(UnsupportedOperationException)
    ProblemDetail unsupported(UnsupportedOperationException e) {
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, e.message)
    }
}

class OtherController {

    def index() {
    }
}

@ControllerAdvice(assignableTypes = HandledController)
class HandledControllerAdvice {

    @ExceptionHandler(IllegalStateException)
    ProblemDetail conflict(IllegalStateException e) {
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message)
    }
}
