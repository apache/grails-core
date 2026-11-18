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
package org.grails.web.converters.configuration;

import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import grails.config.Config;
import grails.core.GrailsApplication;
import grails.core.support.GrailsApplicationAware;
import grails.core.support.proxy.DefaultProxyHandler;
import grails.core.support.proxy.ProxyHandler;
import org.grails.config.PropertySourcesConfig;
import org.grails.web.converters.Converter;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * What the initializers of the JSON converter and of the XML converter, which {@code grails-xml} provides, share: the
 * application's configuration, its proxy handler and the marshallers it registers.
 *
 * @since 9.0
 */
public abstract class AbstractConvertersConfigurationInitializer
        implements ApplicationContextAware, GrailsApplicationAware, InitializingBean {

    private ApplicationContext applicationContext;

    private GrailsApplication grailsApplication;

    /**
     * Initializes the default and named configurations of the converter.
     */
    public abstract void initialize();

    @Override
    public void afterPropertiesSet() {
        initialize();
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    protected GrailsApplication getGrailsApplication() {
        return grailsApplication;
    }

    @Override
    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    /**
     * @return the application's configuration, or an empty one, so that every setting has its default
     */
    protected Config getGrailsConfig() {
        return grailsApplication != null ? grailsApplication.getConfig() : new PropertySourcesConfig();
    }

    protected ProxyHandler getProxyHandler() {
        return applicationContext != null ? applicationContext.getBean(ProxyHandler.class) : new DefaultProxyHandler();
    }

    /**
     * @param config the application's configuration
     * @param converterType {@code json} or {@code xml}
     * @return whether the converter writes the version of a domain class instance
     */
    protected static boolean includeDomainVersionProperty(Config config, String converterType) {
        return config.getProperty(String.format("grails.converters.%s.domain.include.version", converterType), Boolean.class,
                config.getProperty("grails.converters.domain.include.version", Boolean.class, false));
    }

    /**
     * @param config the application's configuration
     * @param converterType {@code json} or {@code xml}
     * @return whether the converter writes the class name of a domain class instance
     */
    protected static boolean includeDomainClassProperty(Config config, String converterType) {
        return config.getProperty(String.format("grails.converters.%s.domain.include.class", converterType), Boolean.class,
                config.getProperty("grails.converters.domain.include.class", Boolean.class, false));
    }

    /**
     * Registers the marshallers of the application's {@link ObjectMarshallerRegisterer} beans for the converter.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected <C extends Converter> void registerObjectMarshallersFromApplicationContext(
            DefaultConverterConfiguration<C> configuration, Class<C> converterClass) {
        if (applicationContext == null) {
            return;
        }
        for (ObjectMarshallerRegisterer registerer : applicationContext.getBeansOfType(ObjectMarshallerRegisterer.class).values()) {
            if (registerer.getConverterClass() == converterClass) {
                configuration.registerObjectMarshaller(registerer.getMarshaller(), registerer.getPriority());
            }
        }
    }

    /**
     * @return the marshallers the converter's default configuration has, registered before it is initialized
     */
    protected <C extends Converter> List<ObjectMarshaller<C>> getPreviouslyConfiguredMarshallers(Class<C> converterClass) {
        return ConvertersConfigurationHolder.getConverterConfiguration(converterClass).getOrderedObjectMarshallers();
    }
}
