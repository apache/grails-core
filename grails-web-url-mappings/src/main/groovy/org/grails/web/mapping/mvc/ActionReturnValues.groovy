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

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.context.ApplicationContext
import org.springframework.context.MessageSource
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.util.ConcurrentReferenceHashMap
import org.springframework.util.ReflectionUtils
import org.springframework.util.StringUtils
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.async.AsyncWebRequest
import org.springframework.web.context.request.async.CallableProcessingInterceptor
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils
import org.springframework.web.method.HandlerMethod
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.View
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

import grails.config.Settings
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Writes the value a controller action returns as Spring MVC writes the value a handler method returns, where Spring
 * MVC writes it as the response: an {@code HttpEntity}, such as a {@code ResponseEntity}, {@code HttpHeaders}, a
 * {@code ProblemDetail}, an {@code ErrorResponse}, a {@code StreamingResponseBody} or a {@code ResponseBodyEmitter},
 * and, with {@code grails.controllers.responseBody}, any value of an action annotated {@code @ResponseBody}, or of an
 * action of a controller annotated so. The action
 * is the handler method, typed by the value it returns, as Spring MVC types the value of a handler method declared to
 * return {@code Object}, and the value is handed to the return value handlers of Spring MVC's handler adapter, with the
 * status of the action's {@code @ResponseStatus}, so that the advice for the controller and the annotations of the
 * action, such as {@code @JsonView}, apply as they do to a Spring MVC controller. An asynchronous value, such as a
 * {@code CompletableFuture} or a {@code DeferredResult}, is processed as Spring MVC processes one, with the executor,
 * timeout and interceptors of the handler adapter, and the value it completes with is written as the action's.
 *
 * @since 9.0
 */
@CompileStatic
class ActionReturnValues {

    /** The name of the bean Spring MVC's handler adapter is. */
    private static final String MVC_HANDLER_ADAPTER = 'requestMappingHandlerAdapter'

    /** The request attribute that holds the action whose asynchronous value the request completes. */
    private static final String ASYNC_ACTION = ActionReturnValues.name + '.ASYNC_ACTION'

    /** The method of each action of each controller class, by its name. */
    private static final Map<Class<?>, Map<String, ActionMethod>> ACTIONS = new ConcurrentReferenceHashMap<>()

    private final ApplicationContext applicationContext

    /** Whether Spring MVC writes the values of actions annotated {@code @ResponseBody}, as the setting says. */
    private final boolean responseBody

    private volatile MvcAdapter mvcAdapter

    ActionReturnValues(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
        this.responseBody = applicationContext != null && applicationContext.environment.getProperty(
                Settings.CONTROLLERS_RESPONSE_BODY, Boolean, Boolean.valueOf(Settings.CONTROLLERS_RESPONSE_BODY_DEFAULT))
    }

    /**
     * The handler method of an action: its method of the controller, without parameters, which Grails generates for
     * an action that takes some, with its annotations.
     *
     * @param controller the controller
     * @param methodName the name of the action's method, which the controller class maps the name its URL gives it to
     * @return the handler method, or {@code null} where the controller has no method of the action
     */
    static HandlerMethod handlerMethod(Object controller, String methodName) {
        Method method = actionMethod(controller, methodName)?.method
        return method != null ? new HandlerMethod(controller, method) : null
    }

    /**
     * Whether Spring MVC writes the value an action returns as the response, where its handler adapter is there to. A
     * {@code null} value is left to Grails, and so is the value of an action that rendered the response itself, with
     * {@code render} or {@code respond}, redirected, forwarded or processes the request asynchronously already, whatever
     * the status of the response, so that what an action of an error page returns is written.
     *
     * @param value the value the action returns
     * @param controller the controller
     * @param methodName the name of the action's method
     * @param webRequest the request
     * @return whether it does
     */
    boolean isBody(Object value, Object controller, String methodName, GrailsWebRequest webRequest) {
        if (value == null || webRequest.responseRendered ||
                webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.FORWARD_ISSUED) != null ||
                WebAsyncUtils.getAsyncManager(webRequest.currentRequest).concurrentHandlingStarted) {
            return false
        }
        ActionMethod action = actionMethod(controller, methodName)
        return action?.method != null && ((responseBody && action.responseBody) || value instanceof HttpEntity ||
                value instanceof HttpHeaders || value instanceof ProblemDetail || value instanceof ErrorResponse ||
                value instanceof StreamingResponseBody || value instanceof ResponseBodyEmitter) && adapter() != null
    }

    /**
     * Whether the request completes the asynchronous value of an action, which Spring MVC writes the value it completed
     * with as the action's.
     *
     * @param request the request
     * @return whether it does
     */
    static boolean isAsync(HttpServletRequest request) {
        return request.getAttribute(ASYNC_ACTION) instanceof ActionHandlerMethod
    }

    /**
     * Writes the value an action returns with the return value handler Spring MVC's handler adapter selects for it.
     *
     * @param value the value
     * @param controller the controller
     * @param methodName the name of the action's method
     * @param request the request
     * @param response the response
     * @return the view a handler selected, or {@code null} where it wrote the response
     */
    ModelAndView write(Object value, Object controller, String methodName, HttpServletRequest request, HttpServletResponse response) {
        return write(value, new ActionHandlerMethod(controller, actionMethod(controller, methodName).method, applicationContext),
                request, response)
    }

    /**
     * Writes the value the asynchronous value of an action completed with, as Spring MVC writes the value the
     * asynchronous value of a handler method completed with: typed by the value, so that a {@code ResponseEntity} it
     * completed with is written as one an action declared to return {@code Object} returns.
     *
     * @param value the value it completed with
     * @param request the request, which {@link #isAsync} is true of
     * @param response the response
     * @return the view a handler selected, or {@code null} where it wrote the response
     */
    ModelAndView writeAsync(Object value, HttpServletRequest request, HttpServletResponse response) {
        ActionHandlerMethod action = (ActionHandlerMethod) request.getAttribute(ASYNC_ACTION)
        request.removeAttribute(ASYNC_ACTION)
        return write(value, action, request, response)
    }

    private ModelAndView write(Object value, ActionHandlerMethod action, HttpServletRequest request, HttpServletResponse response) {
        MvcAdapter adapter = adapter()
        GrailsWebRequest.lookup(request)?.renderView = false
        // as Spring MVC's handler adapter prepares a request for the asynchronous value of a handler method
        AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response)
        asyncWebRequest.timeout = adapter.asyncRequestTimeout
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request)
        if (adapter.taskExecutor != null) {
            asyncManager.taskExecutor = adapter.taskExecutor
        }
        asyncManager.asyncWebRequest = asyncWebRequest
        asyncManager.registerCallableInterceptors(adapter.callableInterceptors)
        asyncManager.registerDeferredResultInterceptors(adapter.deferredResultInterceptors)
        HttpStatusCode status = action.status
        if (status != null) {
            if (StringUtils.hasText(action.statusReason)) {
                response.sendError(status.value(), action.statusReason)
                return null
            }
            response.status = status.value()
            request.setAttribute(View.RESPONSE_STATUS_ATTRIBUTE, status)
        }
        ModelAndViewContainer container = new ModelAndViewContainer()
        adapter.returnValueHandlers.handleReturnValue(value, action.getReturnValueType(value), container,
                new ServletWebRequest(request, response))
        if (asyncManager.concurrentHandlingStarted) {
            request.setAttribute(ASYNC_ACTION, action)
            return null
        }
        if (container.requestHandled) {
            return null
        }
        // a value Spring MVC renders a view for, such as a ModelAndView an action annotated @ResponseBody returns
        ModelAndView modelAndView = container.viewReference ? new ModelAndView(container.viewName, container.model) :
                new ModelAndView((View) container.view, container.model)
        if (container.status != null) {
            modelAndView.status = container.status
        }
        return modelAndView
    }

    private MvcAdapter adapter() {
        MvcAdapter adapter = mvcAdapter
        if (adapter == null && applicationContext != null) {
            RequestMappingHandlerAdapter handlerAdapter = applicationContext.containsBean(MVC_HANDLER_ADAPTER) ?
                    applicationContext.getBean(MVC_HANDLER_ADAPTER, RequestMappingHandlerAdapter) :
                    applicationContext.getBeanProvider(RequestMappingHandlerAdapter).getIfUnique()
            if (handlerAdapter?.returnValueHandlers) {
                adapter = new MvcAdapter(handlerAdapter)
                mvcAdapter = adapter
            }
        }
        return adapter
    }

    private static ActionMethod actionMethod(Object controller, String methodName) {
        if (controller == null || !methodName) {
            return null
        }
        Class<?> type = controller.getClass()
        Map<String, ActionMethod> methods = ACTIONS.computeIfAbsent(type, (Class<?> key) -> new ConcurrentHashMap<String, ActionMethod>())
        return methods.computeIfAbsent(methodName, (String name) -> new ActionMethod(type, ReflectionUtils.findMethod(type, name)))
    }

    /**
     * Spring MVC's handler adapter as it handles the value of a handler method: its return value handlers, and the
     * executor, timeout and interceptors of its asynchronous requests, which it has no accessors for, as Spring MVC's
     * configuration and the application's {@code WebMvcConfigurer} beans set them.
     *
     * <p>Those four are read from the adapter's private fields, and a field a release of Spring renames reads as
     * {@code null}, which leaves the request with the default executor, the container's timeout and no interceptors.
     * {@code ActionReturnValuesSpec} pins each of the four, by the name of the executor's threads, the timeout and an
     * attribute each kind of interceptor sets, so that a rename fails the build rather than a request; keep that
     * coverage where this changes.</p>
     */
    @CompileStatic
    private static final class MvcAdapter {

        final HandlerMethodReturnValueHandlerComposite returnValueHandlers

        final AsyncTaskExecutor taskExecutor

        final Long asyncRequestTimeout

        final CallableProcessingInterceptor[] callableInterceptors

        final DeferredResultProcessingInterceptor[] deferredResultInterceptors

        MvcAdapter(RequestMappingHandlerAdapter adapter) {
            returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(adapter.returnValueHandlers)
            taskExecutor = (AsyncTaskExecutor) field(adapter, 'taskExecutor')
            asyncRequestTimeout = (Long) field(adapter, 'asyncRequestTimeout')
            callableInterceptors = (CallableProcessingInterceptor[]) field(adapter, 'callableInterceptors') ?:
                    new CallableProcessingInterceptor[0]
            deferredResultInterceptors = (DeferredResultProcessingInterceptor[]) field(adapter, 'deferredResultInterceptors') ?:
                    new DeferredResultProcessingInterceptor[0]
        }

        private static Object field(RequestMappingHandlerAdapter adapter, String name) {
            Field field = ReflectionUtils.findField(RequestMappingHandlerAdapter, name)
            if (field == null) {
                return null
            }
            ReflectionUtils.makeAccessible(field)
            return ReflectionUtils.getField(field, adapter)
        }
    }

    /** The method of an action, and whether the action, or its controller, is annotated {@code @ResponseBody}. */
    @CompileStatic
    private static final class ActionMethod {

        final Method method

        final boolean responseBody

        ActionMethod(Class<?> controllerClass, Method method) {
            this.method = method
            this.responseBody = method != null && (AnnotatedElementUtils.hasAnnotation(method, ResponseBody) ||
                    AnnotatedElementUtils.hasAnnotation(controllerClass, ResponseBody))
        }
    }

    /** An action as a handler method, with the status and reason of its {@code @ResponseStatus}. */
    @CompileStatic
    private static final class ActionHandlerMethod extends HandlerMethod {

        ActionHandlerMethod(Object controller, Method method, MessageSource messageSource) {
            super(controller, method, messageSource)
        }

        HttpStatusCode getStatus() {
            return responseStatus
        }

        String getStatusReason() {
            return responseStatusReason
        }
    }
}
