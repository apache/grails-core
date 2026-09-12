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
package org.apache.grails.benchmarks.controllers

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver

import grails.web.mapping.LinkGenerator
import org.apache.grails.benchmarks.urlmappings.UrlMappingsFixture
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.servlet.view.CompositeViewResolver

/**
 * Builds the collaborators the {@code redirect} and {@code render(template:)} controller paths
 * resolve from the application context.
 *
 * The link generator is the real {@link DefaultLinkGenerator} over the shared URL mappings fixture,
 * so a benchmarked redirect performs the same reverse URL creation a running application performs.
 * The view resolver, by contrast, resolves to a view that renders nothing: template rendering is
 * already covered by the {@code views} and {@code gsp} benchmarks, and leaving it out here makes the
 * measurement as sensitive as it can be to the framework work {@code render(template:)} itself does.
 */
@CompileStatic
class ControllerResponseFixture {

    static LinkGenerator createLinkGenerator() {
        DefaultLinkGenerator linkGenerator = new DefaultLinkGenerator('http://localhost:8080')
        linkGenerator.urlMappingsHolder = UrlMappingsFixture.createHolder()
        linkGenerator
    }

    static CompositeViewResolver createViewResolver(View view) {
        CompositeViewResolver viewResolver = new CompositeViewResolver()
        viewResolver.viewResolvers = [new FixedViewResolver(view)] as List<ViewResolver>
        viewResolver
    }

    static CountingView createCountingView() {
        new CountingView()
    }
}

/**
 * Resolves every view name to the same view, so that view lookup contributes a constant to the
 * measurement rather than a resolver-specific cost.
 */
@CompileStatic
class FixedViewResolver implements ViewResolver {

    private final View view

    FixedViewResolver(View view) {
        this.view = view
    }

    @Override
    View resolveViewName(String viewName, Locale locale) {
        this.view
    }
}

/**
 * A view that writes nothing and only counts the calls, so the measured region contains the
 * framework's path to the view but not the cost of producing markup. The count is returned from the
 * benchmark method so the call cannot be optimised away.
 */
@CompileStatic
class CountingView implements View {

    private long renderCount

    @Override
    String getContentType() {
        'text/html;charset=UTF-8'
    }

    @Override
    void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
        this.renderCount++
    }

    long getRenderCount() {
        this.renderCount
    }
}
