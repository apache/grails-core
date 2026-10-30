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

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.Ordered
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver

import grails.config.Settings
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Resolves an exception a controller action throws with the {@code @ExceptionHandler} methods Spring MVC resolves those
 * of a handler method with: those of the controller's class, and those of the {@code @ControllerAdvice} beans that apply
 * to it, Spring Boot's handler of {@code spring.mvc.problemdetails.enabled} among them. Spring MVC's resolver passes over
 * a handler that is not a handler method, as a Grails action is, so this resolver gives it the action as one. It is
 * ordered ahead of Spring MVC's resolvers, as that resolver is ahead of Spring MVC's others, so that an exception none of
 * those methods handles is resolved by them and by the Grails exception resolver, as without it.
 *
 * @since 9.0
 */
@CompileStatic
class ActionExceptionHandlerResolver implements HandlerExceptionResolver, Ordered, ApplicationContextAware {

    /** Ahead of Spring MVC's resolvers, which its configuration orders at 0, behind Spring Boot's error attributes. */
    int order = -1

    /**
     * Whether it resolves exceptions, as {@code grails.exceptionresolver.springHandlers} says, which it reads from the
     * environment of its application context where it is not set.
     */
    Boolean enabled

    private ApplicationContext applicationContext

    private volatile ExceptionHandlerExceptionResolver resolver

    @Override
    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
        if (enabled == null) {
            enabled = applicationContext.environment.getProperty(Settings.SETTING_EXCEPTION_RESOLVER_SPRING_HANDLERS, Boolean,
                    Boolean.valueOf(Settings.SETTING_EXCEPTION_RESOLVER_SPRING_HANDLERS_DEFAULT))
        }
    }

    @Override
    ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!enabled || !(handler instanceof GrailsControllerUrlMappingInfo)) {
            return null
        }
        HandlerMethod action = action(request, (GrailsControllerUrlMappingInfo) handler)
        ExceptionHandlerExceptionResolver springResolver = action != null ? exceptionHandlerResolver() : null
        return springResolver?.resolveException(request, response, action, ex)
    }

    /** The action as a handler method: the controller that handles the request, and its method of the action. */
    private static HandlerMethod action(HttpServletRequest request, GrailsControllerUrlMappingInfo info) {
        String actionName = GrailsWebRequest.lookup(request)?.actionName ?: info.actionName ?: info.controllerClass.defaultAction
        // the action as its URL names it, such as show-book with the hyphenated URL converter, to the name of its method
        return ActionReturnValues.handlerMethod(request.getAttribute(GrailsApplicationAttributes.CONTROLLER),
                actionName ? info.controllerClass.actionUriToViewName(actionName) : null)
    }

    /** Spring MVC's resolver of exception handler methods, which its configuration registers among its resolvers. */
    private ExceptionHandlerExceptionResolver exceptionHandlerResolver() {
        ExceptionHandlerExceptionResolver found = resolver
        if (found == null && applicationContext != null) {
            for (HandlerExceptionResolver candidate : applicationContext.getBeansOfType(HandlerExceptionResolver).values()) {
                found = exceptionHandlerResolver(candidate)
                if (found != null) {
                    break
                }
            }
            resolver = found
        }
        return found
    }

    private static ExceptionHandlerExceptionResolver exceptionHandlerResolver(HandlerExceptionResolver candidate) {
        if (candidate instanceof ExceptionHandlerExceptionResolver) {
            return (ExceptionHandlerExceptionResolver) candidate
        }
        if (candidate instanceof HandlerExceptionResolverComposite) {
            return (ExceptionHandlerExceptionResolver) ((HandlerExceptionResolverComposite) candidate).exceptionResolvers
                    .find { HandlerExceptionResolver resolver -> resolver instanceof ExceptionHandlerExceptionResolver }
        }
        return null
    }
}
