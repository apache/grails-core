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

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.util.Assert
import org.springframework.web.context.request.WebRequestInterceptor
import org.springframework.web.filter.ServerHttpObservationFilter
import org.springframework.web.servlet.HandlerExecutionChain
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.handler.AbstractHandlerMapping
import org.springframework.web.servlet.handler.MappedInterceptor
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter
import org.springframework.web.util.UrlPathHelper

import grails.web.http.HttpHeaders
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import grails.web.mapping.cors.GrailsCorsConfiguration
import grails.web.mime.MimeType
import grails.web.mime.MimeTypeResolver
import org.grails.exceptions.ExceptionUtils
import org.grails.web.errors.GrailsWrappedRuntimeException
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.HiddenHttpMethod
import org.grails.web.util.WebUtils

/**
 *
 * Spring MVC {@link org.springframework.web.servlet.HandlerMapping} to match requests onto Grails controllers
 *
 * @since 3.0
 */
@CompileStatic
class UrlMappingsHandlerMapping extends AbstractHandlerMapping {

    public static final String MATCHED_REQUEST = 'org.grails.url.match.info'

    /**
     * The mapping an include made by {@link org.grails.web.mapping.UrlMappingUtils#includeRequestForUrlMappingInfo} was
     * made for. The include runs the action of that mapping, whatever its URI would match and whatever error is being
     * handled: the exception resolver includes the error handler it chose, and an error handler can include another
     * action.
     *
     * @since 8.0.1
     */
    public static final String INCLUDED_MAPPING_INFO = 'org.grails.url.include.info'

    // Both are stateless, so one shared instance each rather than two allocations per request.
    private static final HandlerInterceptor OBSERVATION_ROUTE_HANDLER = new ObservationRouteHandler()
    private static final HandlerInterceptor ERROR_HANDLING_HANDLER = new ErrorHandlingHandler()
    private static final HandlerInterceptor HANDLED_REQUEST_HANDLER = new HandledRequestHandler()

    /**
     * Whether to resolve a "_method" parameter on a POST into the overridden request method while matching
     * URL mappings. Set when the hidden HTTP method filter is disabled. The dispatcher normally wraps the
     * request with the override before this runs, so this is the fallback for one that does not.
     */
    boolean resolveHiddenHttpMethod = false

    /**
     * Whether the mapping that handles a request is set as its
     * {@link org.springframework.web.servlet.HandlerMapping#BEST_MATCHING_HANDLER_ATTRIBUTE} until the dispatch
     * completes, so that what Spring MVC negotiates for the request can tell that a URL mapping handles it. Set with
     * {@code grails.mime.negotiation.spring}, whose strategies are all that read it; without it, the request and the
     * handler chain are left as Spring MVC's handler mapping leaves them.
     *
     * @since 9.0
     */
    boolean markHandledRequests = false

    protected UrlMappingsHolder urlMappingsHolder
    // Deliberately not UrlPathHelper.defaultInstance: that instance is read-only, and this field is
    // protected, so a subclass configuring it (alwaysUseFullPath and friends) must keep working.
    protected UrlPathHelper urlHelper = new UrlPathHelper()
    protected MimeTypeResolver mimeTypeResolver
    protected HandlerInterceptor[] webRequestHandlerInterceptors

    /**
     * The HTTP method to match URL mappings against.
     *
     * <p>An override the dispatcher already resolved is honoured wherever it applies, forwards and includes
     * included - the servlet filter's wrapper reports the overridden method for the whole of a request, and
     * an application is entitled to the same answer in either mode.</p>
     *
     * <p>Deriving a fresh override from the parameters is what an internal dispatch must not do: it inherits
     * the parameters of the request that started it, so a "_method" the dispatcher never acted on would go
     * on selecting an action for every forward after it.</p>
     */
    protected String resolveHttpMethod(HttpServletRequest request) {
        if (!resolveHiddenHttpMethod) {
            return request.getMethod()
        }
        String resolved = HiddenHttpMethod.effectiveMethod(request)
        if (resolved != request.getMethod() || WebUtils.isForwardOrInclude(request)) {
            return resolved
        }
        String override = HiddenHttpMethod.resolveOverride(request)
        if (override == null) {
            return resolved
        }
        // Published as well as returned. Routing on an override that allowedMethods cannot see would send
        // the request to the action the override names and then refuse it with a 405 for the method it
        // arrived as. The dispatcher normally publishes this before the mapping runs; this is the path
        // where it did not - a stock DispatcherServlet, or this mapping driven on its own.
        request.setAttribute(HiddenHttpMethod.OVERRIDDEN_METHOD_ATTRIBUTE, override)
        override
    }

    UrlMappingsHandlerMapping(UrlMappingsHolder urlMappingsHolder) {
        Assert.notNull(urlMappingsHolder, 'Argument [urlMappingsHolder] cannot be null')
        this.urlMappingsHolder = urlMappingsHolder
        setOrder(-5)
    }

    @Autowired
    void setHandlerInterceptors(HandlerInterceptor[] handlerInterceptors) {
        for (hi in handlerInterceptors) {
            if (!(hi instanceof MappedInterceptor)) {
                setInterceptors(hi)
            }
        }
    }

    @Autowired(required = false)
    void setWebRequestInterceptors(WebRequestInterceptor[] webRequestInterceptors) {
        webRequestHandlerInterceptors = webRequestInterceptors.collect({ WebRequestInterceptor wri ->
            new WebRequestHandlerInterceptorAdapter(wri)
        }) as HandlerInterceptor[]
    }

    @Autowired(required = false)
    void setMimeTypeResolver(MimeTypeResolver mimeTypeResolver) {
        this.mimeTypeResolver = mimeTypeResolver
    }

    @Override
    protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
        // Let Spring assemble the chain. Re-implementing it here meant Grails-mapped requests silently
        // missed whatever AbstractHandlerMapping added later - currently the API version deprecation
        // interceptor behind spring.mvc.apiversion.*.
        HandlerExecutionChain chain = super.getHandlerExecutionChain(handler, request)

        // WebRequestInterceptors have to come first, as these include things like Hibernate OSIV.
        int webRequestInterceptorCount = webRequestHandlerInterceptors ? webRequestHandlerInterceptors.length : 0
        for (int i = 0; i < webRequestInterceptorCount; i++) {
            chain.addInterceptor(i, webRequestHandlerInterceptors[i])
        }

        // The route goes on the observation ahead of the interceptors that can fail, such as a Grails interceptor's
        // before(), so that a failure in one is reported under the route that failed
        chain.addInterceptor(webRequestInterceptorCount, OBSERVATION_ROUTE_HANDLER)
        chain.addInterceptor(ERROR_HANDLING_HANDLER)
        if (markHandledRequests) {
            // First, so that it completes last, whichever interceptor stops the request
            chain.addInterceptor(0, HANDLED_REQUEST_HANDLER)
        }
        return chain
    }

    /**
     * The mapping for an exception, found as the Grails exception resolver finds it: for the exception that was thrown,
     * which a forward from that resolver has wrapped, and then for its root cause.
     */
    private UrlMappingInfo matchException(int status, Throwable exception) {
        Throwable thrown = exception instanceof GrailsWrappedRuntimeException ? exception.cause : exception
        UrlMappingInfo match = urlMappingsHolder.matchStatusCode(status, thrown)
        Throwable rootCause = ExceptionUtils.getRootCause(thrown)
        return match == null && !rootCause.is(thrown) ? urlMappingsHolder.matchStatusCode(status, rootCause) : match
    }

    /**
     * The mapping that handles the request, which is also the request's
     * {@link org.springframework.web.servlet.HandlerMapping#BEST_MATCHING_HANDLER_ATTRIBUTE}, as each of Spring MVC's
     * handler mappings sets it for the handler it finds, until the dispatch completes, so that what Spring MVC
     * negotiates for the request while a URL mapping handles it can tell that one does, where
     * {@link #markHandledRequests} says so.
     */
    @Override
    protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
        Object handler = matchHandler(request)
        if (!markHandledRequests) {
            return handler
        }
        if (handler != null) {
            request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, handler)
        }
        else if (request.getAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof UrlMappingInfo) {
            // A dispatch no mapping handles, such as a forward an action makes to a Spring MVC endpoint, is not handled by
            // the mapping of the dispatch that made it, whose attribute the handler mapping that matches it reads first
            request.removeAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE)
        }
        return handler
    }

    private Object matchHandler(HttpServletRequest request) {

        def includedInfo = WebUtils.isInclude(request) ? request.getAttribute(INCLUDED_MAPPING_INFO) : null
        if (includedInfo != null) {
            request.removeAttribute(INCLUDED_MAPPING_INFO)
        }
        def matchedInfo = request.getAttribute(MATCHED_REQUEST)
        def errorStatus = request.getAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE)
        if (includedInfo instanceof UrlMappingInfo) {
            UrlMappingInfo info = withController((UrlMappingInfo) includedInfo)
            // as for a match by URI, the action sees the id of its mapping rather than the page's
            GrailsWebRequest includeRequest = GrailsWebRequest.lookup(request)
            if (includeRequest != null) {
                includeRequest.resetParams()
                info.configure(includeRequest)
            }
            if (info instanceof GrailsControllerUrlMappingInfo) {
                request.setAttribute(MATCHED_REQUEST, info)
                request.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, ((GrailsControllerUrlMappingInfo) info).controllerClass)
                return info
            }
            if (info.viewName || info.URI) return info
        }

        // The dispatch that returns the result of asynchronous processing answers for the action that started it. Any
        // other dispatch, such as a forward a view makes, matches its own URI, whatever an earlier dispatch left here.
        if (matchedInfo != null && errorStatus == null && request.dispatcherType == DispatcherType.ASYNC) return matchedInfo

        String uri = urlHelper.getPathWithinApplication(request)
        def webRequest = GrailsWebRequest.lookup(request)

        Assert.notNull(webRequest, 'HandlerMapping requires a Grails web request')

        String version = findRequestedVersion(webRequest)

        if (errorStatus && !WebUtils.isInclude(request)) {
            def exception = request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE)
            UrlMappingInfo info
            if (exception instanceof Throwable) {
                def exceptionSpecificMatch = matchException(errorStatus.toString().toInteger(), (Throwable) exception)
                if (exceptionSpecificMatch) {
                    info = exceptionSpecificMatch
                }
                else {
                    info = urlMappingsHolder.matchStatusCode(errorStatus.toString().toInteger())
                }
            }
            else {
                info = urlMappingsHolder.matchStatusCode(errorStatus.toString().toInteger())
            }

            request.setAttribute(MATCHED_REQUEST, info)
            return info
        }
        else {

            def infos = urlMappingsHolder.matchAll(uri, resolveHttpMethod(request), version != null ? version : UrlMapping.ANY_VERSION)

            for (UrlMappingInfo info in infos) {
                if (info) {
                    if (info.redirectInfo) return info

                    webRequest.resetParams()
                    info.configure(webRequest)
                    if (info instanceof GrailsControllerUrlMappingInfo) {
                        request.setAttribute(MATCHED_REQUEST, info)
                        request.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, ((GrailsControllerUrlMappingInfo) info).controllerClass)
                        return info
                    }
                    else if (info.viewName || info.URI) {
                        return info
                    }
                }
            }

            return null
        }

    }

    /**
     * The mapping with the controller it names, as a match by URI answers, so that its action runs. A mapping built
     * from a controller and action, rather than matched, does not carry the controller.
     */
    private UrlMappingInfo withController(UrlMappingInfo info) {
        if (info instanceof GrailsControllerUrlMappingInfo || !(urlMappingsHolder instanceof AbstractGrailsControllerUrlMappings)) {
            return info
        }
        return ((AbstractGrailsControllerUrlMappings) urlMappingsHolder).collectControllerMapping(info)
    }

    protected String findRequestedVersion(GrailsWebRequest currentRequest) {
        String version = currentRequest.getHeader(HttpHeaders.ACCEPT_VERSION)
        if (!version && mimeTypeResolver) {
            // none where the request accepts no configured type, as with grails.mime.negotiation.spring
            MimeType mimeType = mimeTypeResolver.resolveResponseMimeType(currentRequest)
            version = mimeType?.version
        }
        return version
    }

    /**
     * Sets the HTTP server observation's route from the matched controller/action, so the
     * {@code http.server.requests} {@code uri} tag is a low-cardinality route (e.g. {@code /book/show})
     * instead of {@code UNKNOWN}. Runs at {@code preHandle}: at URL-mapping match the observation
     * context is not yet on the request. A request routed to an error handler keeps the route that failed, so
     * that the failures an error handler answers are not all reported as its own route. That includes a handler
     * that forwards: a forward takes the status code off the request, and leaves the exception. An action a page
     * includes is part of that page, so it leaves the route of the page.
     */
    @CompileStatic
    static class ObservationRouteHandler implements HandlerInterceptor {

        @Override
        boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            if (WebUtils.isError(request) || request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null ||
                    WebUtils.isInclude(request)) {
                return true
            }
            try {
                String controllerName = null
                String actionName = null
                def webRequest = GrailsWebRequest.lookup(request)
                if (webRequest != null) {
                    controllerName = webRequest.controllerName
                    actionName = webRequest.actionName
                }
                if ((controllerName == null || controllerName.isEmpty()) && handler instanceof UrlMappingInfo) {
                    def info = (UrlMappingInfo) handler
                    controllerName = info.controllerName
                    if (actionName == null || actionName.isEmpty()) {
                        actionName = info.actionName
                    }
                }
                if (controllerName != null && !controllerName.isEmpty()) {
                    if ((actionName == null || actionName.isEmpty()) && handler instanceof GrailsControllerUrlMappingInfo) {
                        actionName = ((GrailsControllerUrlMappingInfo) handler).controllerClass?.defaultAction
                    }
                    if (actionName == null || actionName.isEmpty()) {
                        actionName = 'index'
                    }
                    def context = ServerHttpObservationFilter.findObservationContext(request).orElse(null)
                    if (context != null) {
                        context.setPathPattern('/' + controllerName + '/' + actionName)
                    }
                }
            }
            catch (Throwable ignored) {
            }
            return true
        }
    }

    /**
     * Takes the mapping off the request as its best matching handler once the dispatch it handles completes, so that
     * what negotiates for the request afterwards, such as Spring Security's filters for an exception the dispatch left
     * unresolved, or the handler mapping of an error dispatch, does not take it for a request a URL mapping handles.
     */
    @CompileStatic
    static class HandledRequestHandler implements HandlerInterceptor {

        @Override
        void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
            if (request.getAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE).is(handler)) {
                request.removeAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE)
            }
        }
    }

    static class ErrorHandlingHandler implements HandlerInterceptor {

        @Override
        boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            return true
        }

        @Override
        void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
            // no-op
        }

        @Override
        void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
            request.removeAttribute(MATCHED_REQUEST)
        }
    }

    void setGrailsCorsConfiguration(GrailsCorsConfiguration grailsCorsConfiguration) {
        this.corsConfigurations = grailsCorsConfiguration.corsConfigurations
    }
}
