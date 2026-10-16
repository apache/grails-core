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
package org.apache.grails.web.layout

import groovy.transform.CompileStatic

import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletContext

import com.opensymphony.module.sitemesh.Config
import com.opensymphony.module.sitemesh.Factory
import com.opensymphony.module.sitemesh.factory.DefaultFactory
import com.opensymphony.sitemesh.ContentProcessor
import com.opensymphony.sitemesh.compatability.PageParser2ContentProcessor
import io.micrometer.observation.ObservationRegistry

import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.Ordered
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver

import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware

@CompileStatic
class GrailsLayoutViewResolver extends EmbeddedGrailsLayoutViewResolver implements GrailsApplicationAware, DisposableBean, Ordered, ApplicationListener<ContextRefreshedEvent> {

    private static final String FACTORY_SERVLET_CONTEXT_ATTRIBUTE = 'grails.layout.factory'
    private ContentProcessor contentProcessor
    protected GrailsApplication grailsApplication
    private boolean grailsLayoutConfigLoaded = false
    private int order = Ordered.LOWEST_PRECEDENCE - 50
    private ObservationRegistry observationRegistry = ObservationRegistry.NOOP

    GrailsLayoutViewResolver() {
        super()
    }

    GrailsLayoutViewResolver(ViewResolver innerViewResolver, GroovyPageLayoutFinder groovyPageLayoutFinder) {
        super(innerViewResolver, groovyPageLayoutFinder)
    }

    @Override
    protected View createLayoutView(View innerView) {
        var layoutView = new GrailsLayoutView(groovyPageLayoutFinder, innerView, contentProcessor)
        layoutView.setObservationRegistry(this.observationRegistry)
        return layoutView
    }

    void init() {
        if (servletContext == null) return

        Factory grailsLayoutFactory = (Factory) servletContext.getAttribute(FACTORY_SERVLET_CONTEXT_ATTRIBUTE)
        if (grailsLayoutFactory == null) {
            grailsLayoutFactory = loadGrailsLayoutConfig()
        }
        contentProcessor = new PageParser2ContentProcessor(grailsLayoutFactory)
    }

    protected Factory loadGrailsLayoutConfig() {
        FilterConfig filterConfig = new FilterConfig() {
            private Map<String, String> customConfig =
                    Collections.singletonMap('configFile',
                            'classpath:org/apache/grails/web/layout/grails-layout-default.xml')

            @Override
            ServletContext getServletContext() {
                return GrailsLayoutViewResolver.this.servletContext
            }

            @Override
            Enumeration<String> getInitParameterNames() {
                return Collections.enumeration(customConfig.keySet())
            }

            @Override
            String getInitParameter(String name) {
                return customConfig.get(name)
            }

            @Override
            String getFilterName() {
                return null
            }
        }
        Config config = new Config(filterConfig)

        DefaultFactory grailsLayoutFactory = new DefaultFactory(config)
        if (servletContext != null) {
            servletContext.setAttribute(FACTORY_SERVLET_CONTEXT_ATTRIBUTE, grailsLayoutFactory)
        }
        grailsLayoutFactory.refresh()
        FactoryHolder.setFactory(grailsLayoutFactory)
        grailsLayoutConfigLoaded = true
        return grailsLayoutFactory
    }

    @Override
    void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
    }

    @Override
    void destroy() throws Exception {
        clearGrailsLayoutConfig()
    }

    protected void clearGrailsLayoutConfig() {
        if (servletContext == null) return
        if (grailsLayoutConfigLoaded) {
            FactoryHolder.setFactory(null)
            if (servletContext != null) {
                servletContext.removeAttribute(FACTORY_SERVLET_CONTEXT_ATTRIBUTE)
            }
            grailsLayoutConfigLoaded = false
        }
    }

    int getOrder() {
        return order
    }

    void setOrder(int order) {
        this.order = order
    }

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        this.observationRegistry = event.getApplicationContext()
                .getBeanProvider(ObservationRegistry).getIfAvailable(() -> ObservationRegistry.NOOP)
        init()
    }
}
