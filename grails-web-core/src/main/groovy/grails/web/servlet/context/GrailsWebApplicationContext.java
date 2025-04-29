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
package grails.web.servlet.context;

import grails.spring.BeanBuilder;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import grails.core.GrailsApplication;
import grails.web.servlet.context.support.GrailsEnvironment;
import org.grails.spring.GrailsApplicationContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.ui.context.ThemeSource;
import org.springframework.util.Assert;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.support.ServletContextAwareProcessor;
import org.springframework.web.context.support.ServletContextResource;
import org.springframework.web.context.support.ServletContextResourcePatternResolver;
import org.springframework.web.context.support.StandardServletEnvironment;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * A WebApplicationContext that extends StaticApplicationContext to allow for programmatic
 * configuration at runtime. The code is adapted from StaticWebApplicationContext.
 *
 * @author Graeme
 * @since 0.3
 */
public class GrailsWebApplicationContext extends GrailsApplicationContext
        implements ConfigurableWebApplicationContext, ThemeSource {

    private ServletContext servletContext;
    private String namespace;
    private ServletConfig servletConfig;
    private String[] configLocations = new String[0];
    private GrailsApplication grailsApplication;

    public GrailsWebApplicationContext() throws BeansException {
        super();
    }

    public GrailsWebApplicationContext(GrailsApplication grailsApplication) {
        this();
        this.grailsApplication = grailsApplication;
    }

    public GrailsWebApplicationContext(ApplicationContext parent) throws BeansException {
        super(parent);
    }

    public GrailsWebApplicationContext(DefaultListableBeanFactory defaultListableBeanFactory, GrailsApplication grailsApplication) {
        this(defaultListableBeanFactory);
        this.grailsApplication = grailsApplication;
    }

    public GrailsWebApplicationContext(ApplicationContext parent, GrailsApplication grailsApplication) throws BeansException {
        super(parent);
        this.grailsApplication = grailsApplication;
    }

    public GrailsWebApplicationContext(DefaultListableBeanFactory defaultListableBeanFactory) {
        super(defaultListableBeanFactory);
    }

    public GrailsWebApplicationContext(DefaultListableBeanFactory defaultListableBeanFactory, ApplicationContext parent) {
        super(defaultListableBeanFactory, parent);
    }

    public GrailsWebApplicationContext(DefaultListableBeanFactory defaultListableBeanFactory, ApplicationContext parent, GrailsApplication grailsApplication) {
        super(defaultListableBeanFactory, parent);
        this.grailsApplication = grailsApplication;
    }

    @Override
    public ClassLoader getClassLoader() {
        GrailsApplication application = getGrailsApplication();
        return application == null ? super.getClassLoader() : application.getClassLoader();
    }

    private GrailsApplication getGrailsApplication() {
        if (grailsApplication == null && containsBean(GrailsApplication.APPLICATION_ID)) {
            grailsApplication = getBean(GrailsApplication.APPLICATION_ID, GrailsApplication.class);
        }
        return grailsApplication;
    }

    /**
     * Set the ServletContext that this WebApplicationContext runs in.
     */
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
        if (namespace != null) {
            setDisplayName("WebApplicationContext for namespace '" + namespace + "'");
        }
    }

    public String getNamespace() {
        return namespace;
    }

    public void setConfigLocation(String configLocation) {
        Assert.notNull(configLocation, "Argument [configLocation] cannot be null");
        configLocations = new String[] { configLocation };
    }

    public void setConfigLocations(String[] configLocations) {
        Assert.notNull(configLocations, "Argument [configLocations] cannot be null");
        this.configLocations = configLocations;
    }

    public String[] getConfigLocations() {
        return configLocations;
    }

    /**
     * Register ServletContextAwareProcessor.
     * @see ServletContextAwareProcessor
     */
    @Override
    protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        beanFactory.addBeanPostProcessor(new ServletContextAwareProcessor(servletContext));
        beanFactory.ignoreDependencyInterface(ServletContextAware.class);
        beanFactory.registerResolvableDependency(ServletContext.class, servletContext);

        WebApplicationContextUtils.registerWebApplicationScopes(beanFactory);
    }

    /**
     * This implementation supports file paths beneath the root of the ServletContext.
     * @see ServletContextResource
     */
    @Override
    protected Resource getResourceByPath(String path) {
        return new ServletContextResource(servletContext, path);
    }

    /**
     * This implementation supports pattern matching in unexpanded WARs too.
     * @see ServletContextResourcePatternResolver
     */
    @Override
    protected ResourcePatternResolver getResourcePatternResolver() {
        return new ServletContextResourcePatternResolver(this);
    }

    @Override
    protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (configLocations.length > 0) {
            for (String configLocation : configLocations) {
                BeanBuilder beanBuilder = new BeanBuilder(getParent(),getClassLoader());
                final ServletContextResource resource = new ServletContextResource(getServletContext(), configLocation);
                beanBuilder.loadBeans(resource);
                beanBuilder.registerBeans(this);
            }
        }
        super.prepareBeanFactory(beanFactory);
    }

    public void setServletConfig(ServletConfig servletConfig) {
        this.servletConfig = servletConfig;
    }

    public ServletConfig getServletConfig() {
        return servletConfig;
    }

    @Override
    protected ConfigurableEnvironment createEnvironment() {
        GrailsApplication grailsApplication = getGrailsApplication();
        return grailsApplication == null ? new StandardServletEnvironment() : new GrailsEnvironment(grailsApplication);
    }

    @Override
    public ConfigurableWebEnvironment getEnvironment() {
        ConfigurableEnvironment env = super.getEnvironment();
        Assert.isInstanceOf(ConfigurableWebEnvironment.class, env,
                "ConfigurableWebApplication environment must be of type ConfigurableWebEnvironment");
        return (ConfigurableWebEnvironment) env;
    }
}
