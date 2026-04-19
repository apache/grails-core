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
import java.util.Locale;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.sitemesh.content.ContentProcessor;
import org.sitemesh.webapp.contentfilter.ResponseMetaData;
import org.sitemesh.webmvc.SiteMeshViewContext;

import org.springframework.web.servlet.ViewResolver;

/**
 * Grails-flavoured {@link SiteMeshViewContext} that pushes a fresh
 * {@link Sitemesh3CapturedPage} onto the request for the duration of each
 * decorator dispatch. The layout GSP's own capture taglibs populate that
 * page, which {@link CaptureAwareContentProcessor} then returns as the
 * decorated {@code Content} (enabling chained decoration without a second
 * HTML parse). The previous attribute is restored on exit so the caller's
 * captured page remains intact.
 */
public class GrailsSiteMeshViewContext extends SiteMeshViewContext {

    public GrailsSiteMeshViewContext(String contentType,
                                     HttpServletRequest request,
                                     HttpServletResponse response,
                                     ServletContext servletContext,
                                     ContentProcessor contentProcessor,
                                     ResponseMetaData metaData,
                                     boolean includeErrorPages,
                                     ViewResolver viewResolver,
                                     Locale locale) {
        super(contentType, request, response, servletContext, contentProcessor, metaData,
                includeErrorPages, viewResolver, locale);
    }

    @Override
    public void dispatch(HttpServletRequest request, HttpServletResponse response, String path)
            throws ServletException, IOException {
        // Push a fresh Sitemesh3CapturedPage for the decorator render but
        // DO NOT restore the previous value on exit. Chained decoration
        // relies on reading the freshly-captured page after dispatch
        // returns — the layout's own <g:capture*> taglibs populate it,
        // and CaptureAwareContentProcessor then returns it as the
        // "decorated Content" without a second HTML parse. The outer
        // GrailsSiteMeshView.postRender is responsible for clearing the
        // attribute at the end of the top-level render.
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, new Sitemesh3CapturedPage());
        super.dispatch(request, response, path);
    }
}
