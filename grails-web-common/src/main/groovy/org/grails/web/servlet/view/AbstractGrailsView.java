/*
 * Copyright 2014-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.servlet.view;

import groovy.text.Template;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.grails.web.servlet.WrappedResponseHolder;
import org.grails.web.util.GrailsApplicationAttributes;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.WebUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.view.AbstractUrlBasedView;


/**
 * A view applied to a Grails application that ensures an appropriate web request is bound
 *
 * @author Lari Hotari
 * @since 2.4
 */
public abstract class AbstractGrailsView extends AbstractUrlBasedView {
    /**
     * Delegates to renderMergedOutputModel(..)
     *
     * @see #renderMergedOutputModel(java.util.Map, jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse)
     *
     * @param model The view model
     * @param request The HttpServletRequest
     * @param response The HttpServletResponse
     * @throws Exception When an error occurs rendering the view
     */
    @Override
    protected final void renderMergedOutputModel(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        exposeModelAsRequestAttributes(model, request);
        renderWithinGrailsWebRequest(model, request, response);
    }

    private void renderWithinGrailsWebRequest(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        boolean attributesChanged = false;
        try {
            GrailsWebRequest webRequest;
            if(!(requestAttributes instanceof GrailsWebRequest)) {
                webRequest = createGrailsWebRequest(request, response, request.getServletContext());
                attributesChanged = true;
                WebUtils.storeGrailsWebRequest(webRequest);
            } else {
                webRequest = (GrailsWebRequest)requestAttributes;
            }
            renderTemplate(model, webRequest, request, response);
        } finally {
            if(attributesChanged) {
                request.removeAttribute(GrailsApplicationAttributes.WEB_REQUEST);
                RequestContextHolder.setRequestAttributes(requestAttributes);
            }
        }
    }    
    
    /**
     * Renders a page with the specified TemplateEngine, mode and response.
     * @param model The model to use
     * @param webRequest The {@link org.grails.web.servlet.mvc.GrailsWebRequest}
     * @param request The {@link jakarta.servlet.http.HttpServletRequest}
     * @param response The {@link jakarta.servlet.http.HttpServletResponse} instance
     *
     * @throws java.io.IOException Thrown when an error occurs writing the response
     */
    abstract protected void renderTemplate(Map<String, Object> model, GrailsWebRequest webRequest, HttpServletRequest request, HttpServletResponse response) throws Exception;
    
    protected GrailsWebRequest createGrailsWebRequest(HttpServletRequest request, HttpServletResponse response,
            ServletContext servletContext) {
        return new GrailsWebRequest(request, response, servletContext);
    }    

    public void rethrowRenderException(Throwable ex, String message) {
        if (ex instanceof Error) {
            throw (Error) ex;
        }        
        if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
        }
        throw new UndeclaredThrowableException(ex, message);
    }
    
    abstract public Template getTemplate();
}
