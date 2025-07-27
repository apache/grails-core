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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.util.Assert
import org.springframework.web.context.request.WebRequestInterceptor
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
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils

/**
 *
 * Spring MVC {@link org.springframework.web.servlet.HandlerMapping} to match requests onto Grails controllers
 *
 * @since 3.0
 */
@CompileStatic
class UrlMappingsHandlerMapping extends AbstractHandlerMapping {

    public static final String MATCHED_REQUEST = "org.grails.url.match.info"

    protected UrlMappingsHolder urlMappingsHolder
    protected UrlPathHelper urlHelper = new UrlPathHelper()
    protected MimeTypeResolver mimeTypeResolver
    protected HandlerInterceptor[] webRequestHandlerInterceptors

    UrlMappingsHandlerMapping(UrlMappingsHolder urlMappingsHolder) {
        Assert.notNull(urlMappingsHolder, "Argument [urlMappingsHolder] cannot be null")
        this.urlMappingsHolder = urlMappingsHolder
        setOrder(-5)
    }

    @Autowired
    void setHandlerInterceptors(HandlerInterceptor[] handlerInterceptors) {
        for(hi in handlerInterceptors) {
            if(!(hi instanceof MappedInterceptor)) {
                setInterceptors(hi)
            }
        }
    }

    @Autowired(required = false)
    void setWebRequestInterceptors(WebRequestInterceptor[] webRequestInterceptors) {
        webRequestHandlerInterceptors = webRequestInterceptors.collect( { WebRequestInterceptor wri ->
             new WebRequestHandlerInterceptorAdapter(wri)
         } ) as HandlerInterceptor[]
    }

    @Autowired(required = false)
    void setMimeTypeResolver(MimeTypeResolver mimeTypeResolver) {
        this.mimeTypeResolver = mimeTypeResolver
    }

    @Override
    protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
        HandlerExecutionChain chain = (handler instanceof HandlerExecutionChain ?
                (HandlerExecutionChain) handler : new HandlerExecutionChain(handler))

        // WebRequestInterceptor need to come first, as these include things like Hibernate OSIV
        if(webRequestHandlerInterceptors) {
            chain.addInterceptors webRequestHandlerInterceptors
        }

        String lookupPath = this.urlPathHelper.getLookupPathForRequest(request)
        for (HandlerInterceptor interceptor in this.adaptedInterceptors) {
            if (interceptor instanceof MappedInterceptor) {
                MappedInterceptor mappedInterceptor = mappedInterceptor(interceptor)
                if (mappedInterceptor.matches(lookupPath, this.pathMatcher)) {
                    chain.addInterceptor(mappedInterceptor.getInterceptor())
                }
            }
            else {
                chain.addInterceptor(interceptor)
            }
        }

        chain.addInterceptor(new ErrorHandlingHandler())
        return chain
    }

    @CompileDynamic
    protected MappedInterceptor mappedInterceptor(HandlerInterceptor interceptor) {
        (MappedInterceptor) interceptor
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) throws Exception {

        def matchedInfo = request.getAttribute(MATCHED_REQUEST)
        def errorStatus = request.getAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE)
        if(matchedInfo != null && errorStatus == null) return matchedInfo

        String uri = urlHelper.getPathWithinApplication(request)
        def webRequest = GrailsWebRequest.lookup(request)

        Assert.notNull(webRequest, "HandlerMapping requires a Grails web request")

        String version = findRequestedVersion(webRequest)


        if(errorStatus && !WebUtils.isInclude(request)) {
            def exception = request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE)
            UrlMappingInfo info
            if(exception instanceof Throwable) {
                exception = ExceptionUtils.getRootCause(exception)
                def exceptionSpecificMatch = urlMappingsHolder.matchStatusCode(errorStatus.toString().toInteger(), (Throwable) exception)
                if(exceptionSpecificMatch) {
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

            def infos = urlMappingsHolder.matchAll(uri, request.getMethod(), version != null ? version : UrlMapping.ANY_VERSION)

            for(UrlMappingInfo info in infos) {
                if(info) {
                    if(info.redirectInfo) return info

                    webRequest.resetParams()
                    info.configure(webRequest)
                    if(info instanceof GrailsControllerUrlMappingInfo) {
                        request.setAttribute(MATCHED_REQUEST, info)
                        request.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, ((GrailsControllerUrlMappingInfo)info).controllerClass)
                        return info
                    }
                    else if(info.viewName || info.URI) {
                        return info
                    }
                }
            }

            return null
        }

    }

    protected String findRequestedVersion(GrailsWebRequest currentRequest) {
        String version = currentRequest.getHeader(HttpHeaders.ACCEPT_VERSION)
        if(!version && mimeTypeResolver) {
            MimeType mimeType = mimeTypeResolver.resolveResponseMimeType(currentRequest)
            version = mimeType.version
        }
        return version
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
