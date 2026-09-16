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
package org.grails.plugins.web.interceptors

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.OrderComparator
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView

import grails.artefact.Interceptor
import grails.interceptors.Matcher
import grails.util.GrailsNameUtils
import org.grails.datastore.mapping.services.ServiceRegistry
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils

/**
 * Adapts Grails {@link Interceptor} instances to the Spring {@link HandlerInterceptor} interface
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsInterceptorHandlerInterceptorAdapter implements HandlerInterceptor {

    private static final Log LOG = LogFactory.getLog(Interceptor)
    private static final String ATTRIBUTE_MATCHED_INTERCEPTORS = 'org.grails.web.MATCHED_INTERCEPTORS'
    private static final String OBSERVATION_NAME = 'grails.interceptor'
    private static final String OBSERVATION_CONTEXTUAL_NAME_PREFIX = 'grails.interceptor '
    private static final String OBSERVATION_PHASE_KEY = 'grails.interceptor.phase'
    private static final String UNKNOWN_INTERCEPTOR_NAME = 'unknown'

    static final String INTERCEPTOR_RENDERED_VIEW = 'interceptor_rendered_view'

    protected List<Interceptor> interceptors = []
    protected List<Interceptor> reverseInterceptors = []

    /**
     * Caches the logical interceptor name per interceptor class, so that it is derived once rather
     * than on every observed callback. Held per adapter instance (the adapter is an application
     * singleton) so the cached class references die with the application context.
     */
    private final Map<Class<?>, String> logicalInterceptorNames = new ConcurrentHashMap<>()

    @Autowired(required = false)
    ServiceRegistry[] serviceRegistry // inject the service registry to ensure data services are wired up

    @Autowired(required = false)
    ObservationRegistry observationRegistry = ObservationRegistry.NOOP

    @Autowired(required = false)
    @CompileDynamic
    void setInterceptors(Interceptor[] interceptors) {
        this.interceptors = interceptors.sort(new OrderComparator()) as List<Interceptor>
        this.reverseInterceptors = this.interceptors.reverse()
        if (LOG.isDebugEnabled()) {
            LOG.debug('Computed interceptor execution order:')
            for (Interceptor i in interceptors) {
                LOG.debug("- ${GrailsNameUtils.getLogicalPropertyName(i.getClass().name, 'Interceptor')} (order: ${i.order}) ")
            }
        }
    }

    @Override
    boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!interceptors.isEmpty()) {
            List<Interceptor> matchInterceptors = []
            request.setAttribute(ATTRIBUTE_MATCHED_INTERCEPTORS, matchInterceptors)
            for (Interceptor i in interceptors) {
                if (i.doesMatch(request)) {
                    matchInterceptors.add(i)
                    if (!observe(i, Phase.BEFORE)) {
                        return false
                    }
                }
            }
        }
        return true
    }

    @Override
    void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        Object matchedInterceptorsObject = request.getAttribute(ATTRIBUTE_MATCHED_INTERCEPTORS)
        if (matchedInterceptorsObject) {
            if (modelAndView != null) {
                request.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, modelAndView)
            }

            List<Interceptor> reversedInterceptors = (List<Interceptor>) matchedInterceptorsObject
            Collections.reverse(reversedInterceptors)
            request.setAttribute(ATTRIBUTE_MATCHED_INTERCEPTORS, reversedInterceptors)
            for (Interceptor i in reversedInterceptors) {
                if (!observe(i, Phase.AFTER)) {
                    if (request.getAttribute(INTERCEPTOR_RENDERED_VIEW)) {
                        ModelAndView interceptorsModelAndView = i.modelAndView
                        modelAndView.viewName = interceptorsModelAndView.viewName
                        modelAndView.model.clear()
                        modelAndView.model.putAll(interceptorsModelAndView.model)
                    } else {
                        modelAndView?.clear()
                    }
                    break
                }
            }
        }
    }

    @Override
    void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        if (!ex) {
            //Attempting to find an existing exception in the request
            ex = (Exception) request.getAttribute(WebUtils.EXCEPTION_ATTRIBUTE)
        }
        request.setAttribute(Matcher.THROWABLE, ex)
        Object matchedInterceptorsObject = request.getAttribute(ATTRIBUTE_MATCHED_INTERCEPTORS)
        if (matchedInterceptorsObject) {
            for (Interceptor i in ((List<Interceptor>) matchedInterceptorsObject)) {
                i.afterView()
            }
        }
    }

    /**
     * Records a {@code grails.interceptor} span around a single interceptor callback, preserving its
     * boolean result and control flow. No-op (invokes the callback directly) when observation is disabled.
     */
    private boolean observe(Interceptor interceptor, Phase phase) {
        var registry = this.observationRegistry
        if (registry == null || registry.isNoop()) {
            return phase.invoke(interceptor)
        }
        var name = logicalNameOf(interceptor)
        var observation = Observation.createNotStarted(OBSERVATION_NAME, registry)
                .contextualName(OBSERVATION_CONTEXTUAL_NAME_PREFIX + name)
                .lowCardinalityKeyValue(OBSERVATION_NAME, name)
                .lowCardinalityKeyValue(OBSERVATION_PHASE_KEY, phase.tag)
                .start()
        var scope = observation.openScope()
        try {
            return phase.invoke(interceptor)
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

    /**
     * Returns the logical name reported for the given interceptor, deriving it at most once per
     * interceptor class.
     */
    private String logicalNameOf(Interceptor interceptor) {
        Class<?> interceptorClass = interceptor.getClass()
        String name = this.logicalInterceptorNames.get(interceptorClass)
        if (name == null) {
            name = GrailsNameUtils.getLogicalPropertyName(interceptorClass.name, 'Interceptor') ?: UNKNOWN_INTERCEPTOR_NAME
            this.logicalInterceptorNames.put(interceptorClass, name)
        }
        name
    }

    /**
     * The interceptor callback being invoked. Dispatching through this enum keeps the observation
     * plumbing shared between the two phases without a per-call callback object having to be
     * allocated for every matched interceptor on every request.
     */
    private enum Phase {

        BEFORE('before'),
        AFTER('after')

        final String tag

        Phase(String tag) {
            this.tag = tag
        }

        boolean invoke(Interceptor interceptor) {
            this.is(BEFORE) ? interceptor.before() : interceptor.after()
        }
    }
}
