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

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.sitemesh.DecoratorSelector;
import org.sitemesh.SiteMeshContext;
import org.sitemesh.content.ContentProcessor;
import org.sitemesh.webapp.contentfilter.ResponseMetaData;
import org.sitemesh.webmvc.SiteMeshView;
import org.sitemesh.webmvc.SiteMeshViewContext;

import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

/**
 * Grails-flavoured {@link SiteMeshView}. Pushes a fresh
 * {@link Sitemesh3CapturedPage} on the request for the duration of the
 * main-view render so the GSP capture taglibs (<code>&lt;g:captureHead&gt;</code>
 * etc.) populate a page attached to the current request. The attribute is
 * restored on exit.
 *
 * <p>Also overrides {@link #createContext} to return a
 * {@link GrailsSiteMeshViewContext}, which performs the same push/pop
 * around each decorator dispatch. The combination lets the main view and
 * each layout level capture into their own {@link Sitemesh3CapturedPage}
 * without clobbering each other, even for nested
 * <code>&lt;g:applyLayout&gt;</code>.</p>
 */
public class GrailsSiteMeshView extends SiteMeshView {

    public GrailsSiteMeshView(View innerView,
                              ContentProcessor contentProcessor,
                              DecoratorSelector<SiteMeshContext> decoratorSelector,
                              ServletContext servletContext,
                              ViewResolver viewResolver) {
        super(innerView, contentProcessor, decoratorSelector, servletContext, viewResolver);
    }

    @Override
    protected Object preRender(HttpServletRequest request) {
        Object previousCaptured = request.getAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE);
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, new Sitemesh3CapturedPage());
        return previousCaptured;
    }

    @Override
    protected void postRender(HttpServletRequest request, Object token) {
        if (token != null) {
            request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, token);
        } else {
            request.removeAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE);
        }
    }

    @Override
    protected SiteMeshViewContext createContext(HttpServletRequest request,
                                                HttpServletResponse response,
                                                String contentType,
                                                ResponseMetaData metaData) {
        return new GrailsSiteMeshViewContext(
                contentType, request, response, getServletContext(),
                getContentProcessor(), metaData, false,
                getViewResolver(), request.getLocale());
    }
}
