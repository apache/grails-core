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

import org.sitemesh.DecoratorSelector;
import org.sitemesh.SiteMeshContext;
import org.sitemesh.content.ContentProcessor;
import org.sitemesh.webmvc.SiteMeshView;
import org.sitemesh.webmvc.SiteMeshViewResolver;

import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.view.InternalResourceView;

/**
 * Grails-flavoured {@link SiteMeshViewResolver} that wraps each inner view
 * with a {@link GrailsSiteMeshView} rather than the upstream default.
 */
public class GrailsSiteMeshViewResolver extends SiteMeshViewResolver {

    private final ContentProcessor contentProcessor;
    private final DecoratorSelector<SiteMeshContext> decoratorSelector;
    private final ServletContext servletContext;

    public GrailsSiteMeshViewResolver(ViewResolver innerViewResolver,
                                      ContentProcessor contentProcessor,
                                      DecoratorSelector<SiteMeshContext> decoratorSelector,
                                      ServletContext servletContext) {
        super(innerViewResolver, contentProcessor, decoratorSelector, servletContext);
        this.contentProcessor = contentProcessor;
        this.decoratorSelector = decoratorSelector;
        this.servletContext = servletContext;
    }

    @Override
    protected SiteMeshView createSiteMeshView(View innerView) {
        if (innerView instanceof InternalResourceView) {
            // JSP views render through RequestDispatcher.forward() by default.
            // Recent Tomcat versions (suspendWrappedResponseAfterForward, on by
            // default) suspend the underlying response once a forward returns,
            // even when the view was handed SiteMesh's buffering wrapper — so
            // the decorated page written afterwards would be discarded.
            // Rendering the JSP via include leaves the response usable.
            ((InternalResourceView) innerView).setAlwaysInclude(true);
        }
        return new GrailsSiteMeshView(innerView, contentProcessor, decoratorSelector, servletContext,
                getInnerViewResolver());
    }
}
