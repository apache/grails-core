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

import groovy.transform.CompileStatic

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.MethodParameter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.method.ControllerAdviceBean
import org.springframework.web.method.support.HandlerMethodReturnValueHandler
import org.springframework.web.servlet.mvc.method.annotation.HttpEntityMethodProcessor
import org.springframework.web.servlet.mvc.method.annotation.JsonViewResponseBodyAdvice
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

/**
 * Resolves the message converters Spring MVC ends up configured with at response time.
 *
 * <p>Renderers need the same converter list, in the same order, that the handler adapter uses.
 * Injecting the adapter to read them forces the whole MVC infrastructure to be created from a
 * renderer bean, which risks circular dependencies and defeats lazy startup. A provider delays
 * that lookup until a response is written and works without deprecated MVC callbacks.</p>
 *
 * @since 9.0
 */
@CompileStatic
class SpringMessageConverters implements ApplicationContextAware {

    /** The response body advice Spring MVC adds where Jackson is present, as its configuration adds it to the adapter. */
    static final List<Object> DEFAULT_RESPONSE_BODY_ADVICE = List.<Object>of(new JsonViewResponseBodyAdvice())

    @Autowired(required = false)
    @Qualifier('requestMappingHandlerAdapter')
    ObjectProvider<RequestMappingHandlerAdapter> handlerAdapter

    /** The manager a slice without the handler adapter negotiates with, which a web unit test registers. */
    @Autowired(required = false)
    @Qualifier('mvcContentNegotiationManager')
    ObjectProvider<ContentNegotiationManager> contentNegotiationManager

    private volatile List<HttpMessageConverter<?>> converters = List.of()

    private volatile HttpEntityMethodProcessor sliceEntityHandler

    private ApplicationContext applicationContext

    private volatile List<Object> responseBodyAdvice

    @Override
    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }

    /**
     * The advice Spring MVC's handler adapter writes the body of a response with: the application's
     * {@code @ControllerAdvice} beans that are {@code ResponseBodyAdvice}, found as the adapter finds them, which apply to
     * the controllers they are for, ahead of the advice Spring MVC adds where Jackson is present.
     *
     * @return the advice, found when a response first needs it
     */
    List<Object> getResponseBodyAdvice() {
        List<Object> advice = responseBodyAdvice
        if (advice == null) {
            advice = []
            if (applicationContext != null) {
                for (ControllerAdviceBean adviceBean : ControllerAdviceBean.findAnnotatedBeans(applicationContext)) {
                    Class<?> beanType = adviceBean.beanType
                    if (beanType != null && ResponseBodyAdvice.isAssignableFrom(beanType)) {
                        advice.add(adviceBean)
                    }
                }
            }
            advice.addAll(DEFAULT_RESPONSE_BODY_ADVICE)
            advice = Collections.unmodifiableList(advice)
            responseBodyAdvice = advice
        }
        return advice
    }

    void setConverters(List<HttpMessageConverter<?>> converters) {
        // Lightweight web test slices have no MVC handler adapter. They supply their converters
        // here; production always uses the handler adapter's final list when it is available.
        this.converters = Collections.unmodifiableList(converters)
        this.sliceEntityHandler = null
    }

    /**
     * @return the converters MVC is configured with, or an empty list before initialization; read
     * when a response is written so that every configurer's contribution is included
     */
    List<HttpMessageConverter<?>> getConverters() {
        RequestMappingHandlerAdapter adapter = handlerAdapter?.getIfAvailable()
        return adapter == null ? converters : Collections.unmodifiableList(adapter.messageConverters)
    }

    /**
     * The handler Spring MVC writes a {@code ResponseEntity} a handler method returns with: the first of the handler
     * adapter's return value handlers that supports the return type, as the adapter selects it, which is built with
     * MVC's converters, content negotiation manager, {@code ResponseBodyAdvice} and {@code ErrorResponse.Interceptor}
     * beans. A slice without the adapter gets one built with its converters and content negotiation manager.
     *
     * @param returnType the return type the value is written as
     * @return the handler, or {@code null} where there are no converters
     */
    HandlerMethodReturnValueHandler entityHandler(MethodParameter returnType) {
        List<HandlerMethodReturnValueHandler> handlers = handlerAdapter?.getIfAvailable()?.returnValueHandlers
        if (handlers != null) {
            return handlers.find { HandlerMethodReturnValueHandler handler -> handler.supportsReturnType(returnType) }
        }
        HttpEntityMethodProcessor handler = sliceEntityHandler
        if (handler == null && converters) {
            handler = entityHandler(converters, contentNegotiationManager?.getIfAvailable() ?: new ContentNegotiationManager())
            sliceEntityHandler = handler
        }
        return handler
    }

    /**
     * The handler Spring MVC writes a {@code ResponseEntity} with, built as its handler adapter builds it with the given
     * converters and content negotiation manager, without advice.
     *
     * @param converters the converters
     * @param contentNegotiationManager the content negotiation manager
     * @return the handler
     */
    static HttpEntityMethodProcessor entityHandler(List<HttpMessageConverter<?>> converters,
                                                   ContentNegotiationManager contentNegotiationManager) {
        return new HttpEntityMethodProcessor(converters, contentNegotiationManager)
    }
}
