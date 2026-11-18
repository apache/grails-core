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
package grails.gsp.boot;

import java.net.MalformedURLException;

import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.AbstractUrlBasedView;

import org.grails.gsp.GroovyPagesTemplateEngine;
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator;
import org.grails.web.servlet.view.GroovyPageViewResolver;

/**
 * The GSP view resolver of a Spring Boot application, which falls back to a JSP of a view's name only where
 * that JSP exists.
 *
 * <p>Spring Boot renders its error page through the view named {@code error}, and this resolver is asked for
 * that view first. A fallback that answered for any name would return a view forwarding to {@code error} - the
 * URL the error is already being handled at - which the servlet container refuses as a loop, so neither the
 * application's error page nor Boot's is reached. Answering nothing leaves the name to the view resolvers after
 * this one, Boot's error page among them.
 *
 * <p>The JSP is looked for from the root of the servlet context, where the view name of a Spring Boot
 * application names one. A view is cached by its name, so the answer does not depend on the URL of the request
 * that first asked for it.
 *
 * <p>A Grails application keeps the resolver it always had: its errors are routed by its URL mappings rather
 * than by Boot's error controller, so it never asks for this view.
 *
 * @since 8.0
 */
class StandaloneGroovyPageViewResolver extends GroovyPageViewResolver {

    StandaloneGroovyPageViewResolver(GroovyPagesTemplateEngine templateEngine,
            GrailsConventionGroovyPageLocator groovyPageLocator) {
        super(templateEngine, groovyPageLocator);
    }

    @Override
    protected View createJstlView(String viewName) throws Exception {
        View view = super.createJstlView(viewName);
        if (view instanceof AbstractUrlBasedView urlBasedView && !isServletContextResource(urlBasedView.getUrl())) {
            return null;
        }
        return view;
    }

    private boolean isServletContextResource(String url) {
        if (url == null) {
            return false;
        }
        try {
            return getServletContext().getResource(url.startsWith("/") ? url : "/" + url) != null;
        } catch (MalformedURLException e) {
            return false;
        }
    }

}
