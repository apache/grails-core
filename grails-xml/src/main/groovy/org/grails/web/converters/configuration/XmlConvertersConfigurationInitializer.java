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

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import grails.config.Config;
import grails.converters.XML;
import grails.core.GrailsApplication;
import grails.core.support.GrailsApplicationAware;
import grails.core.support.proxy.DefaultProxyHandler;
import grails.core.support.proxy.ProxyHandler;
import org.grails.config.PropertySourcesConfig;
import org.grails.web.converters.Converter;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.converters.marshaller.ProxyUnwrappingMarshaller;

/**
 * Initializes the legacy XML converter configurations when the optional
 * {@code grails-xml} module is present.
 *
 * @since 8.0
 */
public class XmlConvertersConfigurationInitializer
        implements ApplicationContextAware, GrailsApplicationAware, InitializingBean {

    public static final String SETTING_CONVERTERS_XML_DEEP = "grails.converters.xml.default.deep";

    private ApplicationContext applicationContext;
    private GrailsApplication grailsApplication;

    @Override
    public void afterPropertiesSet() {
        initialize();
    }

    public void initialize() {
        initXMLConfiguration();
        initDeepXMLConfiguration();
    }

    private void initXMLConfiguration() {
        Config grailsConfig = getGrailsConfig();
        ProxyHandler proxyHandler = getProxyHandler();
        List<ObjectMarshaller<XML>> marshallers = new ArrayList<>();
        marshallers.addAll(getPreviouslyConfiguredMarshallers());
        marshallers.add(new org.grails.web.converters.marshaller.xml.Base64ByteArrayMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.ArrayMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.CollectionMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.MapMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.SimpleEnumMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.DateMarshaller());
        marshallers.add(new ProxyUnwrappingMarshaller<>());
        marshallers.add(new org.grails.web.converters.marshaller.xml.ToStringBeanMarshaller());

        boolean includeDomainVersion = includeDomainVersionProperty(grailsConfig);
        if (grailsConfig.getProperty(SETTING_CONVERTERS_XML_DEEP, Boolean.class, false)) {
            marshallers.add(new org.grails.web.converters.marshaller.xml.DeepDomainClassMarshaller(
                    includeDomainVersion, proxyHandler, grailsApplication));
        }
        else {
            marshallers.add(new org.grails.web.converters.marshaller.xml.DomainClassMarshaller(
                    includeDomainVersion, proxyHandler, grailsApplication));
        }
        marshallers.add(new org.grails.web.converters.marshaller.xml.GroovyBeanMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.GenericJavaBeanMarshaller());

        DefaultConverterConfiguration<XML> configuration =
                new DefaultConverterConfiguration<>(marshallers, proxyHandler);
        configuration.setEncoding(grailsConfig.getProperty(
                ConvertersConfigurationInitializer.SETTING_CONVERTERS_ENCODING, "UTF-8"));
        String defaultCircularReferenceBehaviour = grailsConfig.getProperty(
                ConvertersConfigurationInitializer.SETTING_CONVERTERS_CIRCULAR_REFERENCE_BEHAVIOUR, "DEFAULT");
        configuration.setCircularReferenceBehaviour(Converter.CircularReferenceBehaviour.valueOf(
                grailsConfig.getProperty("grails.converters.xml.circular.reference.behaviour", String.class,
                        defaultCircularReferenceBehaviour, Converter.CircularReferenceBehaviour.allowedValues())));

        Boolean defaultPrettyPrint = grailsConfig.getProperty(
                ConvertersConfigurationInitializer.SETTING_CONVERTERS_PRETTY_PRINT, Boolean.class, false);
        configuration.setPrettyPrint(grailsConfig.getProperty(
                "grails.converters.xml.pretty.print", Boolean.class, defaultPrettyPrint));
        configuration.setCacheObjectMarshallerByClass(grailsConfig.getProperty(
                "grails.converters.xml.cacheObjectMarshallerSelectionByClass", Boolean.class, true));
        registerObjectMarshallersFromApplicationContext(configuration);
        ConvertersConfigurationHolder.setDefaultConfiguration(
                XML.class, new ChainedConverterConfiguration<>(configuration, proxyHandler));
    }

    private void initDeepXMLConfiguration() {
        ProxyHandler proxyHandler = getProxyHandler();
        DefaultConverterConfiguration<XML> deepConfiguration = new DefaultConverterConfiguration<>(
                ConvertersConfigurationHolder.getConverterConfiguration(XML.class), proxyHandler);
        deepConfiguration.registerObjectMarshaller(
                new org.grails.web.converters.marshaller.xml.DeepDomainClassMarshaller(
                        includeDomainVersionProperty(getGrailsConfig()),
                        includeDomainClassProperty(getGrailsConfig()),
                        proxyHandler,
                        grailsApplication));
        ConvertersConfigurationHolder.setNamedConverterConfiguration(XML.class, "deep", deepConfiguration);
    }

    private Config getGrailsConfig() {
        return grailsApplication == null ? new PropertySourcesConfig() : grailsApplication.getConfig();
    }

    private ProxyHandler getProxyHandler() {
        return applicationContext == null ?
                new DefaultProxyHandler() :
                applicationContext.getBean(ProxyHandler.class);
    }

    private boolean includeDomainVersionProperty(Config config) {
        return config.getProperty("grails.converters.xml.domain.include.version", Boolean.class,
                config.getProperty("grails.converters.domain.include.version", Boolean.class, false));
    }

    private boolean includeDomainClassProperty(Config config) {
        return config.getProperty("grails.converters.xml.domain.include.class", Boolean.class,
                config.getProperty("grails.converters.domain.include.class", Boolean.class, false));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void registerObjectMarshallersFromApplicationContext(DefaultConverterConfiguration<XML> configuration) {
        if (applicationContext == null) {
            return;
        }
        for (ObjectMarshallerRegisterer registerer :
                applicationContext.getBeansOfType(ObjectMarshallerRegisterer.class).values()) {
            if (registerer.getConverterClass() == XML.class) {
                configuration.registerObjectMarshaller(registerer.getMarshaller(), registerer.getPriority());
            }
        }
    }

    private List<ObjectMarshaller<XML>> getPreviouslyConfiguredMarshallers() {
        return ConvertersConfigurationHolder.getConverterConfiguration(XML.class).getOrderedObjectMarshallers();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }
}
