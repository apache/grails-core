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
package org.grails.web.servlet.view;

import java.util.Enumeration;
import java.util.Map;
import java.util.Collections;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

import grails.core.GrailsApplication;
import grails.core.support.GrailsApplicationAware;
import org.apache.grails.web.layout.FactoryHolder;
import org.apache.grails.web.layout.GroovyPageLayoutFinder;
import org.apache.grails.web.layout.GrailsLayoutView;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import com.opensymphony.module.sitemesh.Config;
import com.opensymphony.module.sitemesh.Factory;
import com.opensymphony.module.sitemesh.factory.DefaultFactory;
import com.opensymphony.sitemesh.ContentProcessor;
import com.opensymphony.sitemesh.compatability.PageParser2ContentProcessor;

public class GrailsLayoutViewResolver extends EmbeddedGrailsLayoutViewResolver implements GrailsApplicationAware, DisposableBean, Ordered, ApplicationListener<ContextRefreshedEvent> {
    private static final String FACTORY_SERVLET_CONTEXT_ATTRIBUTE = "grails.layout.factory";
    private ContentProcessor contentProcessor;
    protected GrailsApplication grailsApplication;
    private boolean grailsLayoutConfigLoaded = false;
    private int order = Ordered.LOWEST_PRECEDENCE - 50;

    public GrailsLayoutViewResolver() {
        super();
    }

    public GrailsLayoutViewResolver(ViewResolver innerViewResolver, GroovyPageLayoutFinder groovyPageLayoutFinder) {
        super(innerViewResolver, groovyPageLayoutFinder);
    }

    @Override
    protected View createLayoutView(View innerView) {
        return new GrailsLayoutView(groovyPageLayoutFinder, innerView, contentProcessor);
    }

    public void init() {
        if (servletContext == null) return;

        Factory grailsLayoutFactory = (Factory) servletContext.getAttribute(FACTORY_SERVLET_CONTEXT_ATTRIBUTE);
        if (grailsLayoutFactory == null) {
            grailsLayoutFactory = loadGrailsLayoutConfig();
        }
        contentProcessor = new PageParser2ContentProcessor(grailsLayoutFactory);
    }

    protected Factory loadGrailsLayoutConfig() {
        FilterConfig filterConfig = new FilterConfig() {
            private Map<String, String> customConfig =
                    Collections.singletonMap("configFile",
                            "classpath:org/apache/grails/web/layout/grails-layout-default.xml");

            @Override
            public ServletContext getServletContext() {
                return servletContext;
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return Collections.enumeration(customConfig.keySet());
            }

            @Override
            public String getInitParameter(String name) {
                return customConfig.get(name);
            }

            @Override
            public String getFilterName() {
                return null;
            }
        };
        Config config = new Config(filterConfig);

        DefaultFactory grailsLayoutFactory = new DefaultFactory(config);
        if (servletContext != null) {
            servletContext.setAttribute(FACTORY_SERVLET_CONTEXT_ATTRIBUTE, grailsLayoutFactory);
        }
        grailsLayoutFactory.refresh();
        FactoryHolder.setFactory(grailsLayoutFactory);
        grailsLayoutConfigLoaded = true;
        return grailsLayoutFactory;
    }

    @Override
    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    @Override
    public void destroy() throws Exception {
        clearGrailsLayoutConfig();
    }

    protected void clearGrailsLayoutConfig() {
        if (servletContext == null) return;
        if (grailsLayoutConfigLoaded) {
            FactoryHolder.setFactory(null);
            if (servletContext != null) {
                servletContext.removeAttribute(FACTORY_SERVLET_CONTEXT_ATTRIBUTE);
            }
            grailsLayoutConfigLoaded = false;
        }
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        init();
    }
}
