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
package org.apache.grails.web.layout;

import java.util.Properties;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import com.opensymphony.module.sitemesh.Config;
import com.opensymphony.module.sitemesh.Decorator;
import com.opensymphony.module.sitemesh.DecoratorMapper;
import com.opensymphony.module.sitemesh.Page;
import com.opensymphony.module.sitemesh.mapper.AbstractDecoratorMapper;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import grails.core.GrailsApplication;

/**
 * Implements the SiteMesh decorator mapper interface and allows grails views to map to grails layouts.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 */
public class GrailsLayoutDecoratorMapper extends AbstractDecoratorMapper {

    private GroovyPageLayoutFinder groovyPageLayoutFinder;

    @Override
    public void init(Config c, Properties properties, DecoratorMapper parentMapper) throws InstantiationException {
        super.init(c, properties, parentMapper);
        ServletContext servletContext = c.getServletContext();
        WebApplicationContext applicationContext = WebApplicationContextUtils.getWebApplicationContext(servletContext);
        if (applicationContext != null) {
            GrailsApplication grailsApplication = applicationContext.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication.class);
            groovyPageLayoutFinder = grailsApplication.getMainContext().getBean("groovyPageLayoutFinder", GroovyPageLayoutFinder.class);
        }
    }

    @Override
    public Decorator getDecorator(HttpServletRequest request, Page page) {
        if (groovyPageLayoutFinder == null) {
            return super.getDecorator(request, page);
        }

        Decorator layout = groovyPageLayoutFinder.findLayout(request, page);
        if (layout != null) {
            return layout;
        }
        layout = parent != null ? super.getDecorator(request, page) : null;
        if (layout == null || layout.getPage() == null) {
            layout = new GrailsNoDecorator();
        }
        return layout;
    }

    @Override
    public Decorator getNamedDecorator(HttpServletRequest request, String name) {
        if (groovyPageLayoutFinder == null) {
            return super.getNamedDecorator(request, name);
        }

        Decorator layout = groovyPageLayoutFinder.getNamedDecorator(request, name);
        if (layout != null) {
            return layout;
        }
        layout = parent != null ? super.getNamedDecorator(request, name) : null;
        if (layout == null || layout.getPage() == null) {
            layout = new GrailsNoDecorator();
        }
        return layout;
    }
}
