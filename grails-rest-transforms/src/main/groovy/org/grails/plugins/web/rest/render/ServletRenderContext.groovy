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
import groovy.transform.TypeCheckingMode

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.ModelAndView

import grails.rest.render.AbstractRenderContext
import grails.web.mime.MimeType
import org.grails.core.util.IncludeExcludeSupport
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils

/**
 * RenderContext for the servlet environment
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class ServletRenderContext extends AbstractRenderContext {

    GrailsWebRequest webRequest
    Map<String, Object> arguments
    private String resourcePath
    private boolean writerObtained = false

    ServletRenderContext(GrailsWebRequest webRequest) {
        this(webRequest, Collections.<String, Object> emptyMap())
    }

    ServletRenderContext(GrailsWebRequest webRequest, Map<String, Object> arguments) {
        this.webRequest = webRequest
        if(arguments != null) {
            this.arguments = Collections.unmodifiableMap(arguments)
            final argsMap = arguments
            final incObject = argsMap != null ?  argsMap.get(IncludeExcludeSupport.INCLUDES_PROPERTY) : null
            final excObject = argsMap != null ? argsMap.get(IncludeExcludeSupport.EXCLUDES_PROPERTY) : null
            List<String> includes = incObject instanceof List ? (List<String>) incObject : null
            List<String> excludes = excObject instanceof List ? (List<String>) excObject : null
            if (includes != null) {
                this.includes = includes
            }
            if (excludes != null) {
                this.excludes = excludes
            }
        }
        else {
            this.arguments = Collections.<String, Object> emptyMap()
        }
    }


    @Override
    String getResourcePath() {
        if (resourcePath == null) {
            return WebUtils.getForwardURI(webRequest.request)
        }
        return resourcePath
    }

    void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath
    }

    @Override
    @CompileStatic(TypeCheckingMode.SKIP)
    MimeType getAcceptMimeType() {
        final response = webRequest.response
        if (response.hasProperty('mimeType'))
            return response.mimeType
        return null
    }

    @Override
    Locale getLocale() {
        webRequest.locale
    }

    @Override
    Writer getWriter() {
        writerObtained = true
        webRequest.currentResponse.writer
    }

    @Override
    HttpMethod getHttpMethod() {
        HttpMethod.valueOf(webRequest.currentRequest.method)
    }

    @Override
    void setStatus(HttpStatus status) {
        webRequest.currentResponse.setStatus(status.value())
    }

    @Override
    void setContentType(String contentType) {
        webRequest.currentResponse.contentType = contentType
    }

    @Override
    void setViewName(String viewName) {
        ModelAndView modelAndView = getModelAndView()
        modelAndView.setViewName(viewName)
    }

    @Override
    String getViewName() {
        final request = webRequest.currentRequest
        ModelAndView modelAndView = (ModelAndView) request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)
        if (modelAndView) {
            return modelAndView.viewName
        }
        return null
    }

    protected ModelAndView getModelAndView() {
        final request = webRequest.currentRequest
        ModelAndView modelAndView = (ModelAndView) request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)
        if (modelAndView == null) {
            modelAndView = new ModelAndView()
            request.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, modelAndView)
        }
        modelAndView
    }

    @Override
    void setModel(Map model) {
        ModelAndView modelAndView = getModelAndView()
        final viewModel = modelAndView.model
        if (arguments?.model instanceof Map) {
            viewModel.putAll((Map)arguments.model)
        }
        viewModel.putAll(model)
    }

    @Override
    String getActionName() {
        webRequest.actionName
    }

    @Override
    String getControllerName() {
        webRequest.controllerName
    }

    @Override
    String getControllerNamespace() {
        return webRequest.controllerNamespace
    }

    @Override
    boolean wasWrittenTo() {
        return writerObtained
    }
}
