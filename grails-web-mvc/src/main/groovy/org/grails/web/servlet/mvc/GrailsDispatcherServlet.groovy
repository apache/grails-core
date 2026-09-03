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
package org.grails.web.servlet.mvc

import groovy.transform.CompileStatic

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.context.ApplicationContext
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.context.ServletContextAware
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.multipart.MultipartException
import org.springframework.web.servlet.DispatcherServlet

import grails.util.Holders
import org.grails.web.context.ServletEnvironmentGrailsApplicationDiscoveryStrategy
import org.grails.web.util.HiddenHttpMethod
import org.grails.web.util.WebUtils

/**
 * Simple extension to the Spring {@link DispatcherServlet} implementation that makes sure a {@link GrailsWebRequest} is bound
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsDispatcherServlet extends DispatcherServlet implements ServletContextAware {

    private volatile ObservationRegistry observationRegistry

    GrailsDispatcherServlet() {
    }

    GrailsDispatcherServlet(WebApplicationContext webApplicationContext) {
        super(webApplicationContext)
    }

    @Override
    protected ServletRequestAttributes buildRequestAttributes(HttpServletRequest request, HttpServletResponse response, RequestAttributes previousAttributes) {
        if (previousAttributes == null || !(previousAttributes instanceof GrailsWebRequest)) {
            return buildGrailsWebRequest(request, response)
        }
        else {
            GrailsWebRequest webRequest = (GrailsWebRequest) previousAttributes
            if (webRequest.isActive()) {
                return webRequest
            }
            else {
                return buildGrailsWebRequest(request, response)
            }
        }
    }

    protected GrailsWebRequest buildGrailsWebRequest(HttpServletRequest request, HttpServletResponse response) {
        def webRequest = new GrailsWebRequest(request, response, request.getServletContext())
        webRequest.informParameterCreationListeners()
        return webRequest
    }

    /**
     * Whether a POST carrying a "_method" parameter should be treated as the method it names. Set when the
     * hidden HTTP method filter is disabled.
     */
    boolean resolveHiddenHttpMethod = false

    @Override
    protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
        HttpServletRequest currentRequest = request
        boolean shouldProcessMultiPart = !WebUtils.isError(request) && !WebUtils.isForwardOrInclude(request)
        if (shouldProcessMultiPart) {
            HttpServletRequest processedRequest = super.checkMultipart(request)
            if (!processedRequest.is(request)) {
                // GrailsWebRequestFilter bound the request below this wrapper, so it cannot reach the
                // wrapper by unwrapping. Publish it for params and request.getFile(..) to find.
                currentRequest = processedRequest
                request.setAttribute(WebUtils.MULTIPART_HTTP_SERVLET_REQUEST_ATTRIBUTE, processedRequest)
                GrailsWebRequest.lookup(request)?.multipartRequestResolved()
            }
        }

        // Resolved here rather than in a servlet filter: after multipart resolution, so the body is parsed
        // once by the dispatcher instead of being forced open by a getParameter() ahead of it, and after the
        // filter chain, so Spring Security still sees the request's real POST method.
        if (resolveHiddenHttpMethod && shouldProcessMultiPart) {
            String override = HiddenHttpMethod.resolveOverride(currentRequest)
            if (override != null) {
                // Recorded as an attribute rather than by substituting the wrapper, because the request
                // Grails exposes is the outermost one. HiddenHttpMethod.effectiveMethod reads it, which is
                // how mapping resolution and allowedMethods see the override; request.method still reports
                // the POST that arrived.
                request.setAttribute(HiddenHttpMethod.OVERRIDDEN_METHOD_ATTRIBUTE, override)
                return HiddenHttpMethod.wrap(override, currentRequest)
            }
        }
        return currentRequest
    }

    /**
     * Wraps the view-render phase in a {@code grails.render} span — parent of the GSP render spans, so
     * "render excluding GSP" is {@code grails.render} minus its GSP children.
     */
    @Override
    protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {
        def observationRegistry = resolveObservationRegistry()
        if (observationRegistry == null || observationRegistry.isNoop()) {
            super.render(mv, request, response)
            return
        }
        def view = (mv != null && mv.viewName) ? mv.viewName : 'none'
        def observation = Observation.createNotStarted('grails.render', observationRegistry)
                .contextualName('grails.render ' + view)
                .lowCardinalityKeyValue('grails.view', view)
                .start()
        def observationScope = observation.openScope()
        try {
            super.render(mv, request, response)
        }
        catch (Throwable t) {
            observation.error(t)
            throw t
        }
        finally {
            observationScope.close()
            observation.stop()
        }
    }

    private ObservationRegistry resolveObservationRegistry() {
        def registry = this.observationRegistry
        if (registry == null) {
            def wac = getWebApplicationContext()
            if (wac == null) {
                // context not ready — return NOOP without caching so a later call re-resolves
                return ObservationRegistry.NOOP
            }
            registry = wac.getBeanProvider(ObservationRegistry).getIfAvailable({ -> ObservationRegistry.NOOP })
            this.observationRegistry = registry
        }
        registry
    }

    @Override
    void setServletContext(ServletContext servletContext) {
        Holders.setServletContext(servletContext)
        Holders.addApplicationDiscoveryStrategy(new ServletEnvironmentGrailsApplicationDiscoveryStrategy(servletContext))
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) {
        if (applicationContext instanceof WebApplicationContext) {
            WebApplicationContext wac = (WebApplicationContext) applicationContext
            Holders.setServletContext(wac.servletContext)
            Holders.addApplicationDiscoveryStrategy(new ServletEnvironmentGrailsApplicationDiscoveryStrategy(wac.servletContext, applicationContext))

        }
        super.setApplicationContext(applicationContext)
    }
}
