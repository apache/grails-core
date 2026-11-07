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
import groovy.util.logging.Slf4j
import org.codehaus.groovy.runtime.InvokerInvocationException

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import io.micrometer.observation.Observation

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.web.ErrorResponse
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.async.AsyncRequestTimeoutException
import org.springframework.web.filter.ServerHttpObservationFilter
import org.springframework.web.method.HandlerMethod
import org.springframework.web.method.annotation.ProblemDetailExceptionHandler
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver
import org.springframework.web.servlet.support.RequestContextUtils
import org.springframework.web.util.DisconnectedClientHelper

import grails.config.Settings
import grails.web.mapping.UrlMappingsHolder
import grails.web.mime.MimeType
import grails.web.mime.MimeTypeResolver
import org.grails.exceptions.ExceptionUtils
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.mvc.exceptions.GrailsMVCException
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils

/**
 * Resolves an exception a controller action throws with the {@code @ExceptionHandler} methods Spring MVC resolves those
 * of a handler method with: those of the controller's class, and those of the {@code @ControllerAdvice} beans that apply
 * to it, Spring Boot's handler of {@code spring.mvc.problemdetails.enabled} among them. Spring MVC's resolver passes over
 * a handler that is not a handler method, as a Grails action is, so this resolver gives it the action as one. It is
 * ordered ahead of Spring MVC's resolvers, as that resolver is ahead of Spring MVC's others, so that an exception none of
 * those methods handles is resolved by them and by the Grails exception resolver, as without it.
 *
 * <p>With {@code grails.exceptionresolver.problemDetails}, it writes an exception none of those methods handles as an
 * RFC 9457 problem, as Spring MVC writes one with {@code ProblemDetailsConfigurer.renderUnhandledExceptions}, except to
 * a client that prefers HTML, or where a URL mapping names the exception. Those are left to the Grails exception
 * resolver and the application's error page, which turning on that switch of Spring MVC would take every exception from.
 *
 * @since 9.0
 */
@CompileStatic
@Slf4j
class ActionExceptionHandlerResolver implements HandlerExceptionResolver, Ordered, ApplicationContextAware {

    /**
     * Where {@link ServerHttpObservationFilter} keeps the observation of a request. The filter only offers its context,
     * which cannot tell the handlers of an error, and a registry cannot be relied on to find it, because a filter can be
     * built with a registry the application does not know. A public way to get it is requested in
     * <a href="https://github.com/spring-projects/spring-framework/issues/37658">spring-framework#37658</a>.
     */
    private static final String SERVER_OBSERVATION_ATTRIBUTE = ServerHttpObservationFilter.name + '.observation'

    /** The name of the bean Spring MVC's content negotiation manager is. */
    private static final String MVC_CONTENT_NEGOTIATION_MANAGER = 'mvcContentNegotiationManager'

    /** Logs a client that went away while a problem was written, in a category of its own, as Spring MVC does. */
    private static final DisconnectedClientHelper DISCONNECTED_CLIENTS =
            new DisconnectedClientHelper(ActionExceptionHandlerResolver.name + '.DisconnectedClient')

    /** Ahead of Spring MVC's resolvers, which its configuration orders at 0, behind Spring Boot's error attributes. */
    int order = -1

    /**
     * Whether it resolves exceptions, as {@code grails.exceptionresolver.springHandlers} says, which it reads from the
     * environment of its application context where it is not set, and which {@code grails.exceptionresolver.problemDetails}
     * turns on unless the application sets it.
     */
    Boolean enabled

    /**
     * Whether it writes an exception no handler method handles as a problem, as
     * {@code grails.exceptionresolver.problemDetails} says, which it reads from the environment of its application
     * context where it is not set.
     *
     * @since 9.1
     */
    Boolean problemDetails

    private ApplicationContext applicationContext

    private volatile ExceptionHandlerExceptionResolver resolver

    private ActionReturnValues returnValues

    private ProblemDetailExceptionHandler problemDetailHandler

    @Override
    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
        if (problemDetails == null) {
            problemDetails = applicationContext.environment.getProperty(Settings.SETTING_EXCEPTION_RESOLVER_PROBLEM_DETAILS,
                    Boolean, false)
        }
        if (enabled == null) {
            // a problem is the last resort after the exception handler methods, as it is in Spring MVC
            enabled = applicationContext.environment.getProperty(Settings.SETTING_EXCEPTION_RESOLVER_SPRING_HANDLERS, Boolean,
                    problemDetails || Boolean.valueOf(Settings.SETTING_EXCEPTION_RESOLVER_SPRING_HANDLERS_DEFAULT))
        }
        returnValues = new ActionReturnValues(applicationContext)
        problemDetailHandler = new ProblemDetailExceptionHandler(applicationContext)
    }

    @Override
    ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(handler instanceof GrailsControllerUrlMappingInfo)) {
            return null
        }
        ModelAndView resolved = enabled ? resolveWithHandlerMethods(request, response, (GrailsControllerUrlMappingInfo) handler, ex) : null
        return resolved == null && problemDetails ? resolveAsProblem(request, response, ex) : resolved
    }

    private ModelAndView resolveWithHandlerMethods(HttpServletRequest request, HttpServletResponse response,
                                                   GrailsControllerUrlMappingInfo handler, Exception ex) {
        HandlerMethod action = action(request, handler)
        ExceptionHandlerExceptionResolver springResolver = action != null ? exceptionHandlerResolver() : null
        return springResolver?.resolveException(request, response, action, ex)
    }

    /**
     * Writes an exception as Spring MVC writes one no handler method handles, with Spring MVC's translation of it, which
     * logs an unexpected one and never exposes its message. A failure to write it leaves the exception to the other
     * resolvers, as a failure of an exception handler method leaves it in Spring MVC. An action a page includes is left
     * to them too, since a problem written into the page is no answer to the client.
     */
    private ModelAndView resolveAsProblem(HttpServletRequest request, HttpServletResponse response, Exception thrown) {
        Exception ex = unwrap(thrown)
        try {
            if (response.committed || WebUtils.isIncludeRequest(request) || !returnValues.writesErrorResponses() ||
                    prefersHtml(request) || isMappedToErrorHandler(ex)) {
                return null
            }
            ErrorResponse errorResponse = problemDetailHandler.handleException(ex, RequestContextUtils.getLocale(request))
            // what an exception handler method that failed produces, as Spring MVC removes it for its own last resort
            request.removeAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE)
            ModelAndView written = errorResponse == null ? new ModelAndView() : returnValues.writeErrorResponse(errorResponse,
                    request.getAttribute(GrailsApplicationAttributes.CONTROLLER), request, response)
            if (isServerError(response)) {
                recordServerError(request, ex)
                // Spring MVC's translation logs an unexpected exception without the request it failed
                if (!(ex instanceof ErrorResponse) && !DisconnectedClientHelper.isClientDisconnectedException(ex)) {
                    log.error('{} occurred when processing request: [{}] {}', ex.class.simpleName, request.method, requestUri(request))
                }
            }
            return written
        }
        catch (Exception e) {
            if (DISCONNECTED_CLIENTS.checkAndLogClientDisconnectedException(e)) {
                if (isServerError(response)) {
                    recordServerError(request, ex)
                }
                return new ModelAndView()
            }
            log.warn('Could not write a problem for an exception of [{}], so leaving it to the other exception resolvers',
                    requestUri(request), e)
            return null
        }
    }

    private static boolean isServerError(HttpServletResponse response) {
        return HttpStatusCode.valueOf(response.status).is5xxServerError()
    }

    /** The URI the client asked for, where the action was forwarded to, as the Grails exception resolver logs it. */
    private static Object requestUri(HttpServletRequest request) {
        return request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE) ?: request.requestURI
    }

    /**
     * The exception an action threw, out of what Groovy and Grails wrap it in. Only those come off: what that exception
     * wraps itself is its own cause, and the status it answers with is its own.
     */
    private static Exception unwrap(Exception ex) {
        Throwable found = ex
        while ((found instanceof InvokerInvocationException || found instanceof GrailsMVCException) && found.cause != null) {
            found = found.cause
        }
        return found instanceof Exception ? (Exception) found : ex
    }

    /**
     * Whether the application's error page is what the client asks for: the format Grails resolves for the response,
     * from a {@code format} parameter or the {@code Accept} header, or else, where that is any format, as for a browser
     * whose {@code Accept} header {@code grails.mime.disable.accept.header.userAgents} disregards, whether the first
     * concrete type of the {@code Accept} header is HTML, as Spring MVC decides to render a problem as a view.
     */
    private boolean prefersHtml(HttpServletRequest request) {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup(request)
        MimeType format = webRequest != null ? applicationContext.getBeanProvider(MimeTypeResolver).getIfAvailable()
                ?.resolveResponseMimeType(webRequest) : null
        if (format != null && format.extension != MimeType.ALL.extension) {
            return format.extension == MimeType.HTML.extension
        }
        try {
            MediaType first = contentNegotiationManager().resolveMediaTypes(new ServletWebRequest(request))
                    .find { MediaType type -> type.concrete }
            return first != null && MediaType.TEXT_HTML.isCompatibleWith(first)
        }
        catch (HttpMediaTypeNotAcceptableException ignored) {
            return false
        }
    }

    private ContentNegotiationManager contentNegotiationManager() {
        return applicationContext.containsBean(MVC_CONTENT_NEGOTIATION_MANAGER) ?
                applicationContext.getBean(MVC_CONTENT_NEGOTIATION_MANAGER, ContentNegotiationManager) :
                new ContentNegotiationManager()
    }

    /**
     * Whether the application maps the exception, as a {@code "500"} mapping with an {@code exception} names it, under
     * the status the Grails exception resolver looks for it with, which for an asynchronous timeout is its own.
     */
    private boolean isMappedToErrorHandler(Exception ex) {
        if (!applicationContext.containsBean(UrlMappingsHolder.BEAN_ID)) {
            return false
        }
        UrlMappingsHolder urlMappings = applicationContext.getBean(UrlMappingsHolder.BEAN_ID, UrlMappingsHolder)
        int status = ex instanceof AsyncRequestTimeoutException ?
                ((AsyncRequestTimeoutException) ex).statusCode.value() : HttpStatus.INTERNAL_SERVER_ERROR.value()
        return urlMappings.matchStatusCode(status, ex) != null || urlMappings.matchStatusCode(status, ExceptionUtils.getRootCause(ex)) != null
    }

    /**
     * As Spring MVC records an exception it translates to a server error, which the observation would not see otherwise.
     * Only the observation tells its handlers, one of which marks the span.
     */
    private static void recordServerError(HttpServletRequest request, Exception ex) {
        Object observation = request.getAttribute(SERVER_OBSERVATION_ATTRIBUTE)
        if (observation instanceof Observation) {
            ((Observation) observation).error(ex)
        }
        else {
            ServerHttpObservationFilter.findObservationContext(request).ifPresent { ServerRequestObservationContext context ->
                context.error = ex
            }
        }
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
