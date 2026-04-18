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
package org.grails.plugins.sitemesh3;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.sitemesh.content.ContentProcessor;
import org.sitemesh.webapp.WebAppContext;
import org.sitemesh.webapp.contentfilter.ResponseMetaData;

import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

/**
 * {@link WebAppContext} that dispatches decorator rendering through the
 * Spring MVC {@link ViewResolver} / {@link View} pipeline instead of
 * {@link jakarta.servlet.RequestDispatcher}. This lets the layout GSP be
 * rendered by the same view resolver chain as the main view, preserving
 * Grails' taglib bindings, encoding setup, and error handling.
 */
public class GrailsViewDispatchContext extends WebAppContext {

    private final ViewResolver viewResolver;
    private final Locale locale;

    public GrailsViewDispatchContext(String contentType,
                                     HttpServletRequest request,
                                     HttpServletResponse response,
                                     ServletContext servletContext,
                                     ContentProcessor contentProcessor,
                                     ResponseMetaData metaData,
                                     boolean includeErrorPages,
                                     ViewResolver viewResolver,
                                     Locale locale) {
        super(contentType, request, response, servletContext, contentProcessor, metaData, includeErrorPages);
        this.viewResolver = viewResolver;
        this.locale = locale;
    }

    @Override
    public void dispatch(HttpServletRequest request, HttpServletResponse response, String path)
            throws ServletException, IOException {
        View view;
        try {
            view = viewResolver.resolveViewName(path, locale);
        } catch (Exception e) {
            throw new ServletException("Failed to resolve layout view: " + path, e);
        }
        if (view == null) {
            throw new ServletException("Layout view not found: " + path);
        }
        // The layout GSP is also preprocessed, so its own <head>/<body>/etc.
        // trigger capture taglibs at render time. Push a fresh
        // Sitemesh3CapturedPage so the layout's captures populate a separate
        // page (kept on the request post-render so CaptureAwareContentProcessor
        // can return it as the decorated Content — enabling chained
        // decoration without a second HTML parse). Sitemesh3LayoutView's
        // outer finally restores the original attribute.
        Sitemesh3CapturedPage layoutPage = new Sitemesh3CapturedPage();
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, layoutPage);
        try {
            view.render(Collections.emptyMap(), request, response);
        } catch (IOException | ServletException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException("Failed to render layout view: " + path, e);
        }
    }
}
