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

import java.util.function.Supplier

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
import org.grails.web.observation.DefaultRenderObservationConvention
import org.grails.web.observation.GrailsObservationDocumentation
import org.grails.web.observation.RenderObservationContext
import org.grails.web.observation.RenderObservationConvention
import org.grails.web.util.WebUtils

/**
 * Simple extension to the Spring {@link DispatcherServlet} implementation that makes sure a {@link GrailsWebRequest} is bound
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsDispatcherServlet extends DispatcherServlet implements ServletContextAware {

    private ObservationRegistry observationRegistry

    private static final DefaultRenderObservationConvention DEFAULT_RENDER_CONVENTION = new DefaultRenderObservationConvention()

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

    @Override
    protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
        boolean shouldProcessMultiPart = !WebUtils.isError(request) && !WebUtils.isForwardOrInclude(request)
        if (shouldProcessMultiPart) {
            HttpServletRequest processedRequest = super.checkMultipart(request)
            if (!processedRequest.is(request)) {
                def webRequest = GrailsWebRequest.lookup(request)
                if (webRequest != null) {
                    webRequest.multipartRequest = processedRequest
                }
            }
        }
        return request
    }

    /**
     * Wraps the view-render phase in a {@code grails.render} span — parent of the GSP render spans, so
     * "render excluding GSP" is {@code grails.render} minus its GSP children.
     */
    @Override
    protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {
        ObservationRegistry registry = resolveObservationRegistry()
        if (registry == null || registry.isNoop()) {
            super.render(mv, request, response)
            return
        }
        Observation observation = GrailsObservationDocumentation.RENDER.observation(
                (RenderObservationConvention) null, DEFAULT_RENDER_CONVENTION,
                { -> new RenderObservationContext((mv != null && mv.viewName) ? mv.viewName : null) } as Supplier<RenderObservationContext>,
                registry).start()
        Observation.Scope scope = observation.openScope()
        try {
            super.render(mv, request, response)
        }
        catch (Throwable t) {
            observation.error(t)
            throw t
        }
        finally {
            scope.close()
            observation.stop()
        }
    }

    private ObservationRegistry resolveObservationRegistry() {
        ObservationRegistry registry = this.observationRegistry
        if (registry == null) {
            WebApplicationContext wac = getWebApplicationContext()
            registry = (wac != null) ? wac.getBeanProvider(ObservationRegistry).getIfAvailable({ -> ObservationRegistry.NOOP }) : ObservationRegistry.NOOP
            this.observationRegistry = registry
        }
        return registry
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
