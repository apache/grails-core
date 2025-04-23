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

import grails.artefact.Interceptor
import grails.interceptors.Matcher
import grails.util.GrailsNameUtils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.grails.datastore.mapping.services.ServiceRegistry
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.OrderComparator
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Adapts Grails {@link Interceptor} instances to the Spring {@link HandlerInterceptor} interface
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsInterceptorHandlerInterceptorAdapter implements HandlerInterceptor {

    private static final Log LOG = LogFactory.getLog(Interceptor)
    private static final String ATTRIBUTE_MATCHED_INTERCEPTORS = "org.grails.web.MATCHED_INTERCEPTORS"

    static final String INTERCEPTOR_RENDERED_VIEW = 'interceptor_rendered_view'

    protected List<Interceptor> interceptors = []
    protected List<Interceptor> reverseInterceptors = []

    @Autowired(required = false)
    ServiceRegistry[] serviceRegistry // inject the service registry to ensure data services are wired up

    @Autowired(required = false)
    @CompileDynamic
    void setInterceptors(Interceptor[] interceptors) {
        this.interceptors = interceptors.sort(new OrderComparator()) as List<Interceptor>
        this.reverseInterceptors = this.interceptors.reverse()
        if(LOG.isDebugEnabled()) {
            LOG.debug("Computed interceptor execution order:")
            for(Interceptor i in interceptors) {
                LOG.debug("- ${GrailsNameUtils.getLogicalPropertyName(i.getClass().name, "Interceptor")} (order: ${i.order}) ")
            }
        }
    }

    @Override
    boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(!interceptors.isEmpty()) {
            List<Interceptor> matchInterceptors = []
            request.setAttribute(ATTRIBUTE_MATCHED_INTERCEPTORS, matchInterceptors)
            for(i in interceptors) {
                if(i.doesMatch(request)) {
                    matchInterceptors.add(i)
                    if( !i.before() ) {
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
        if(matchedInterceptorsObject) {
            if (modelAndView != null) {
                request.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, modelAndView)
            }

            List<Interceptor> reversedInterceptors = ((List<Interceptor>) matchedInterceptorsObject).reverse()
            request.setAttribute(ATTRIBUTE_MATCHED_INTERCEPTORS, reversedInterceptors)
            for(i in reversedInterceptors) {
                if( !i.after() ) {
                    if(request.getAttribute(INTERCEPTOR_RENDERED_VIEW)) {
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
            ex = (Exception)request.getAttribute(WebUtils.EXCEPTION_ATTRIBUTE)
        }
        request.setAttribute(Matcher.THROWABLE, ex)
        Object matchedInterceptorsObject = request.getAttribute(ATTRIBUTE_MATCHED_INTERCEPTORS)
        if(matchedInterceptorsObject) {
            for(i in ((List<Interceptor>) matchedInterceptorsObject)) {
                i.afterView()
            }
        }
    }
}
