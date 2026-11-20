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
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.sitemesh.content.ContentProcessor;
import org.sitemesh.webapp.contentfilter.ResponseMetaData;
import org.sitemesh.webmvc.SiteMeshViewContext;

import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

/**
 * Grails-flavoured {@link SiteMeshViewContext} that pushes a fresh
 * {@link Sitemesh3CapturedPage} onto the request for the duration of each
 * decorator dispatch. The layout GSP's own capture taglibs populate that
 * page, which {@link CaptureAwareContentProcessor} then returns as the
 * decorated {@code Content} (enabling chained decoration without a second
 * HTML parse).
 *
 * <p>Overrides {@link #dispatch} to route absolute paths (those starting with
 * {@code /}) through the {@link ViewResolver} rather than falling back to
 * {@code RequestDispatcher.forward()}. All Grails layout paths are absolute
 * (e.g. {@code /layouts/application}), but {@link SiteMeshViewContext#dispatch}
 * only uses the ViewResolver for relative names — paths that start with {@code /}
 * are handed off to {@code WebAppContext.dispatch()} which re-enters the servlet
 * pipeline via a forward. Routing through the ViewResolver keeps decoration
 * entirely within the Spring MVC view-resolver chain and avoids re-entering
 * the filter stack.</p>
 */
public class GrailsSiteMeshViewContext extends SiteMeshViewContext {

    // Model handed to the layout view render. SiteMesh 2's <g:applyLayout>
    // renders the decorator template with the supplied model
    // (template.make(viewModel)); callers that need the same behavior set
    // this before decorating. Defaults to an empty map so the standard
    // decoration path is unchanged.
    private Map<String, ?> viewModel = Collections.emptyMap();

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

    public void setViewModel(Map<String, ?> viewModel) {
        this.viewModel = viewModel != null ? viewModel : Collections.emptyMap();
    }

    @Override
    public void dispatch(HttpServletRequest request, HttpServletResponse response, String path)
            throws ServletException, IOException {
        // Push a fresh Sitemesh3CapturedPage for the decorator render.
        // DO NOT restore the previous value on exit — chained decoration
        // relies on reading the freshly-captured page after dispatch returns:
        // the layout's own <g:capture*> taglibs populate it, and
        // CaptureAwareContentProcessor returns it as the decorated Content
        // without a second HTML parse. The outer GrailsSiteMeshView.postRender
        // is responsible for clearing the attribute at the end of the
        // top-level render.
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, new Sitemesh3CapturedPage());

        // SiteMeshViewContext.dispatch() only uses the ViewResolver for paths
        // that do NOT start with "/"; absolute paths fall through to
        // WebAppContext.dispatch() (a RequestDispatcher.forward()). All Grails
        // layout paths are absolute (/layouts/...), so we resolve them via the
        // ViewResolver directly to stay within the Spring MVC view chain.
        if (path != null && path.startsWith("/")) {
            try {
                View view = getViewResolver().resolveViewName(path, getLocale());
                if (view != null) {
                    view.render(viewModel, request, response);
                    return;
                }
            } catch (IOException | ServletException e) {
                throw e;
            } catch (Exception e) {
                throw new ServletException("Error rendering layout view: " + path, e);
            }
        }
        super.dispatch(request, response, path);
    }
}
