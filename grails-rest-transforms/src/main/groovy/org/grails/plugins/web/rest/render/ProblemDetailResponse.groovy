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
package org.grails.plugins.web.rest.render

import java.lang.reflect.Method

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import org.springframework.context.ApplicationContext
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.ErrorResponse
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.method.support.HandlerMethodReturnValueHandler
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping

import grails.rest.render.RenderContext
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Writes the response to a {@link ProblemDetail} as Spring MVC writes one a handler method returns, and prepares it
 * where a view or a renderer of the application writes it: the response takes the status the problem carries, unless
 * a {@code status} argument names another, as the status of a {@code ResponseEntity} does, and a problem without an
 * {@code instance} is given the path of the resource.
 *
 * @since 9.0
 */
@CompileStatic
class ProblemDetailResponse {

    /** The request attribute that holds the problem the response has been prepared for. */
    private static final String PREPARED = ProblemDetailResponse.name + '.prepared'

    /** The name of the bean Spring MVC's content negotiation manager is, which its handler adapter is given by name. */
    private static final String MVC_CONTENT_NEGOTIATION_MANAGER = 'mvcContentNegotiationManager'

    /** The method a problem is written as the return value of. */
    private static final Method RETURNED_PROBLEM = ProblemDetailResponse.getDeclaredMethod('respond')

    private ProblemDetailResponse() {
    }

    /**
     * Writes a problem as Spring MVC writes a {@code ResponseEntity} of one that a handler method of the controller
     * returns, with the handler its handler adapter writes it with: in the type its content negotiation selects among
     * those given that a message converter writes the problem in, with the application's {@code ResponseBodyAdvice}
     * and {@code ErrorResponse.Interceptor} beans, which apply as to a method of the controller, and with the status the
     * problem carries, unless a {@code status} is given, which Spring MVC logs a warning for where it differs. A
     * request whose {@code Accept} header Spring MVC cannot read, or that accepts none of the types where the problem's
     * status is not an error status, fails with the exception Spring MVC fails it with. A renderer given converters of
     * its own, other than Spring MVC's, writes with a handler Spring MVC would build with them.
     *
     * @param problem the problem
     * @param context the context of the response
     * @param status the status the response is sent with, or {@code null} for that of the problem
     * @param types the types the problem may be written in, in their order
     * @param converters the converters to write with, or {@code null} for Spring MVC's
     * @param errorResponse the {@code ErrorResponse} the problem is the body of, whose status and headers the response
     *        has and which the {@code ErrorResponse.Interceptor} beans are given, or {@code null}
     * @return {@code false}, with nothing written, where no message converter writes the problem in any of the types
     */
    static boolean write(ProblemDetail problem, ServletRenderContext context, Integer status, List<MediaType> types,
                         List<HttpMessageConverter<?>> converters = null, ErrorResponse errorResponse = null) {
        GrailsWebRequest webRequest = context.webRequest
        ApplicationContext applicationContext = webRequest.applicationContext
        SpringMessageConverters springMessageConverters =
                applicationContext?.getBeanProvider(SpringMessageConverters)?.getIfAvailable()
        List<HttpMessageConverter<?>> mvcConverters = springMessageConverters?.converters
        List<HttpMessageConverter<?>> writers = converters != null ? converters : mvcConverters
        Class<?> problemType = problem.getClass()
        Set<MediaType> producible = new LinkedHashSet<MediaType>(types.findAll { MediaType type ->
            writers?.any { HttpMessageConverter<?> converter -> converter.canWrite(problemType, type) }
        })
        if (producible.isEmpty()) {
            return false
        }
        Object controller = webRequest.attributes?.getController(webRequest.currentRequest)
        MethodParameter returnType = new MethodParameter(RETURNED_PROBLEM, -1)
                .withContainingClass(controller?.getClass() ?: ProblemDetailResponse)
        HandlerMethodReturnValueHandler handler = springMessageConverters != null && writers == mvcConverters ?
                springMessageConverters.entityHandler(returnType) :
                SpringMessageConverters.entityHandler(writers, contentNegotiationManager(applicationContext))
        if (handler == null) {
            return false
        }
        if (problem.instance == null && context.resourcePath) {
            problem.instance = URI.create(context.resourcePath)
        }
        HttpServletRequest request = webRequest.currentRequest
        // the type and encoding a renderer set for its format are not the problem's, which Spring MVC selects
        webRequest.currentResponse.setContentType(null)
        webRequest.currentResponse.setCharacterEncoding((String) null)
        Object previous = request.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE)
        request.setAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, producible)
        try {
            handler.handleReturnValue(returnValue(problem, status, errorResponse), returnType, new ModelAndViewContainer(),
                    new ServletWebRequest(request, webRequest.currentResponse))
        }
        finally {
            if (previous == null) {
                request.removeAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE)
            }
            else {
                request.setAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, previous)
            }
        }
        webRequest.renderView = false
        return true
    }

    /**
     * What a handler method returns for the problem: the {@code ErrorResponse} it is the body of, or the problem, or else a
     * {@code ResponseEntity} of it with the status given, and the headers of the {@code ErrorResponse}.
     */
    private static Object returnValue(ProblemDetail problem, Integer status, ErrorResponse errorResponse) {
        if (status == null) {
            return errorResponse ?: problem
        }
        ResponseEntity.status(status).headers(errorResponse?.headers ?: HttpHeaders.EMPTY).body(problem)
    }

    /** The manager Spring MVC negotiates with, or one that reads the {@code Accept} header alone, as Spring MVC's default. */
    private static ContentNegotiationManager contentNegotiationManager(ApplicationContext context) {
        context != null && context.containsBean(MVC_CONTENT_NEGOTIATION_MANAGER) ?
                context.getBean(MVC_CONTENT_NEGOTIATION_MANAGER, ContentNegotiationManager) : new ContentNegotiationManager()
    }

    /**
     * Prepares the response to a problem a view or a renderer of the application writes: it takes the status a
     * {@code status} argument gives, or else the one the problem carries.
     *
     * @param problem the problem a response is written for
     * @param context the context of the response
     */
    static void prepare(ProblemDetail problem, RenderContext context) {
        if (context instanceof ServletRenderContext) {
            // respond prepares a problem for whichever renderer writes it, and a renderer that prepares it again leaves it
            HttpServletRequest request = ((ServletRenderContext) context).webRequest.currentRequest
            if (request.getAttribute(PREPARED).is(problem)) {
                return
            }
            request.setAttribute(PREPARED, problem)
        }
        prepare(problem, context, requestedStatus(context) ?: problem.status)
    }

    /**
     * Prepares the response to a problem a renderer builds for the response, such as one for validation errors, which
     * carries the status the response is sent with.
     *
     * @param problem the problem a response is written for
     * @param context the context of the response
     */
    static void prepareBuilt(ProblemDetail problem, RenderContext context) {
        prepare(problem, context, problem.status)
    }

    /**
     * The status {@code respond} is given, read as {@code respond} reads it.
     *
     * @param context the context of the response
     * @return the status, or {@code null} where none is given
     */
    static Integer requestedStatus(RenderContext context) {
        Object status = context.arguments?.get('status')
        if (!status) {
            return null
        }
        if (status instanceof Number) {
            return ((Number) status).intValue()
        }
        if (status instanceof HttpStatusCode) {
            return ((HttpStatusCode) status).value()
        }
        status.toString().toInteger()
    }

    private static void prepare(ProblemDetail problem, RenderContext context, int status) {
        setStatus(context, status)
        if (problem.instance == null && context.resourcePath) {
            problem.instance = URI.create(context.resourcePath)
        }
    }

    /**
     * A problem may carry any status code, as in Spring MVC, while {@link RenderContext} takes an
     * {@link HttpStatus}; a code without a constant is written to the servlet response directly.
     */
    private static void setStatus(RenderContext context, int code) {
        HttpStatus status = HttpStatus.resolve(code)
        if (status != null) {
            context.setStatus(status)
        }
        else if (context instanceof ServletRenderContext) {
            ((ServletRenderContext) context).webRequest.currentResponse.setStatus(code)
        }
        else {
            throw new IllegalArgumentException(
                    "Status [${code}] has no HttpStatus constant, which ${context.getClass().name} requires.")
        }
    }

    /** Stands for the handler method of the controller that responds with a problem. */
    @SuppressWarnings('UnusedPrivateMethod')
    private static ResponseEntity<ProblemDetail> respond() {
        null
    }
}
