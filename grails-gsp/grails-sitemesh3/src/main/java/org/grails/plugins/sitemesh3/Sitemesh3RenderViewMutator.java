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

import java.util.Locale;

import org.sitemesh.webmvc.SiteMeshView;

import org.springframework.web.servlet.View;

import grails.web.pages.GrailsRenderViewMutator;

/**
 * Unwraps the SiteMesh decorating view for partial renders. A controller's
 * {@code render template:} resolves its view through the same view-resolver
 * chain as full views, so the resolved view arrives wrapped in a
 * {@link SiteMeshView}. Partials must not be decorated with a layout (matching
 * the SiteMesh 2 plugin's {@code GrailsLayoutRenderViewMutator}, which unwraps
 * its {@code EmbeddedGrailsLayoutView} in the same situation), so the inner
 * view is rendered directly. When an explicit layout was requested
 * ({@code render template: 'x', layout: 'y'}) the wrapping is kept and the
 * layout is applied as usual.
 */
public class Sitemesh3RenderViewMutator implements GrailsRenderViewMutator {

    @Override
    public View mutateView(boolean renderWithLayout, String templateUri, Locale locale, View existingView) {
        if (!renderWithLayout && existingView instanceof SiteMeshView) {
            return ((SiteMeshView) existingView).getInnerView();
        }
        return existingView;
    }
}
