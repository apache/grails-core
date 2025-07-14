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
package org.grails.web.servlet.context.support;

import grails.core.GrailsApplication;
import grails.web.servlet.context.GrailsWebApplicationContext;
import jakarta.servlet.ServletContext;
import org.grails.spring.DefaultRuntimeSpringConfiguration;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.Assert;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ServletContextAware;

/**
 * Subclasses DefaultRuntimeSpringConfiguration to provide construction of WebApplicationContext instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class WebRuntimeSpringConfiguration extends DefaultRuntimeSpringConfiguration implements ServletContextAware {

    private GrailsApplication grailsApplication;

    public WebRuntimeSpringConfiguration(ApplicationContext parent) {
        super(parent);
    }

    public WebRuntimeSpringConfiguration(ApplicationContext parent, ClassLoader cl) {
        super(parent, cl);
    }

    public WebRuntimeSpringConfiguration(ApplicationContext parent, ClassLoader cl, GrailsApplication grailsApplication) {
        super(parent, cl);
        this.grailsApplication = grailsApplication;
    }

    public WebRuntimeSpringConfiguration(ApplicationContext parent, GrailsApplication grailsApplication) {
        super(parent);
        this.grailsApplication = grailsApplication;
    }

    @Override
    protected GenericApplicationContext createApplicationContext(ApplicationContext parentCtx) {
        if (parentCtx != null && beanFactory != null) {
            Assert.isInstanceOf(DefaultListableBeanFactory.class, beanFactory,
                    "ListableBeanFactory set must be a subclass of DefaultListableBeanFactory");

            GrailsWebApplicationContext ctx = new GrailsWebApplicationContext((DefaultListableBeanFactory) beanFactory, grailsApplication);
            ctx.setParent(parentCtx);
            return ctx;
        }

        if (beanFactory != null) {
            Assert.isInstanceOf(DefaultListableBeanFactory.class, beanFactory,
                    "ListableBeanFactory set must be a subclass of DefaultListableBeanFactory");

            return new GrailsWebApplicationContext((DefaultListableBeanFactory) beanFactory, grailsApplication);
        }

        if (parentCtx != null) {
            GrailsWebApplicationContext ctx = new GrailsWebApplicationContext(grailsApplication);
            ctx.setParent(parentCtx);
            return ctx;
        }

        return new GrailsWebApplicationContext();
    }

    public void setServletContext(ServletContext servletContext) {
        initialiseApplicationContext();

        if (context instanceof ConfigurableWebApplicationContext) {
            ((ConfigurableWebApplicationContext) context).setServletContext(servletContext);
        }
    }
}
