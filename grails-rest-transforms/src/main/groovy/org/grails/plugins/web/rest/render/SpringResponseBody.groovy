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
import java.lang.reflect.Type

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.core.MethodParameter
import org.springframework.core.annotation.SynthesizingMethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.util.ReflectionUtils
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.accept.FixedContentNegotiationStrategy
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor

import grails.core.GrailsControllerClass
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Writes the body of a response as Spring MVC writes the value a {@code @ResponseBody} handler method of the controller
 * returns, with its response body handler, in the type {@code respond} negotiated, with the converter given, and with the
 * application's {@code ResponseBodyAdvice} beans, which apply as to a method of the controller. The handler method is the
 * action's, where the request names one, so that its annotations, such as {@code @JsonView}, apply as to a Spring MVC
 * controller method.
 *
 * @since 9.0
 */
@CompileStatic
final class SpringResponseBody {

    /** The method a value is written as the return value of where the request names no action of the controller. */
    private static final Method RETURNED_VALUE = SpringResponseBody.getDeclaredMethod('respond')

    private SpringResponseBody() {
    }

    /**
     * Writes a value with a converter, in a type: the response keeps its status, and Spring MVC sets its content type
     * and writes the body, the response body advice of the application having been given the value first.
     *
     * @param value the value
     * @param context the context of the response
     * @param converter the converter that writes the value
     * @param type the type it is written in, which the converter writes it in
     */
    static void write(Object value, ServletRenderContext context, HttpMessageConverter<?> converter, MediaType type) {
        GrailsWebRequest webRequest = context.webRequest
        SpringMessageConverters springMessageConverters =
                webRequest.applicationContext?.getBeanProvider(SpringMessageConverters)?.getIfAvailable()
        List<Object> advice = springMessageConverters?.responseBodyAdvice ?: SpringMessageConverters.DEFAULT_RESPONSE_BODY_ADVICE
        RequestResponseBodyMethodProcessor handler = new RequestResponseBodyMethodProcessor(
                [converter] as List<HttpMessageConverter<?>>,
                new ContentNegotiationManager(new FixedContentNegotiationStrategy(type)), advice)
        Object controller = webRequest.attributes?.getController(webRequest.currentRequest)
        MethodParameter returnType = returnType(webRequest, controller)
        HttpServletRequest request = webRequest.currentRequest
        HttpServletResponse response = webRequest.currentResponse
        // the type and encoding a renderer set for its format are the ones Spring MVC sets for the converter
        response.setContentType(null)
        response.setCharacterEncoding((String) null)
        Object previous = request.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE)
        request.setAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, Collections.singleton(type))
        try {
            handler.handleReturnValue(value, returnType, new ModelAndViewContainer(), new ServletWebRequest(request, response))
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
    }

    /**
     * The return type the value is written as: of the action the request names, with the action's annotations, or of
     * a method that stands for it, where the request names none, as a unit test that calls an action may not.
     */
    private static MethodParameter returnType(GrailsWebRequest webRequest, Object controller) {
        Class<?> controllerType = controller?.getClass()
        String actionName = webRequest.actionName
        if (controllerType != null && actionName) {
            Object controllerClass = webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS)
            // the action as its URL names it, such as show-book with the hyphenated URL converter, to the name of its method
            String methodName = controllerClass instanceof GrailsControllerClass ?
                    ((GrailsControllerClass) controllerClass).actionUriToViewName(actionName) : actionName
            Method method = ReflectionUtils.findMethod(controllerType, methodName)
            if (method != null) {
                return new ActionReturnType(method, controllerType)
            }
        }
        return new MethodParameter(RETURNED_VALUE, -1).withContainingClass(controllerType ?: SpringResponseBody)
    }

    /**
     * The return type of an action as {@code respond} writes a value: the action's method, without parameters, which
     * has the action's annotations, of the controller, and declared to return {@code Object}, whatever the action
     * declares, since the value is the one {@code respond} is given rather than what the action returns.
     */
    @CompileStatic
    private static final class ActionReturnType extends SynthesizingMethodParameter {

        private final Class<?> controllerType

        ActionReturnType(Method method, Class<?> controllerType) {
            super(method, -1)
            this.controllerType = controllerType
        }

        private ActionReturnType(ActionReturnType original) {
            super(original)
            this.controllerType = original.controllerType
        }

        @Override
        Class<?> getContainingClass() {
            return controllerType
        }

        @Override
        Class<?> getParameterType() {
            return Object
        }

        @Override
        Type getGenericParameterType() {
            return Object
        }

        @Override
        ActionReturnType clone() {
            return new ActionReturnType(this)
        }
    }

    /** Stands for the handler method of the controller that responds with the value. */
    @SuppressWarnings('UnusedPrivateMethod')
    private static Object respond() {
        null
    }
}
