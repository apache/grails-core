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

import java.sql.Time;
import java.time.Duration;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import javax.xml.datatype.XMLGregorianCalendar;

import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import grails.config.Config;
import grails.converters.JSON;
import grails.converters.XML;
import grails.core.GrailsApplication;
import grails.core.support.GrailsApplicationAware;
import grails.core.support.proxy.DefaultProxyHandler;
import grails.core.support.proxy.ProxyHandler;
import org.apache.grails.converters.internal.json.SimpleTypeMarshaller;
import org.grails.config.PropertySourcesConfig;
import org.grails.web.converters.Converter;
import org.grails.web.converters.jackson.DomainClassRendering;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.converters.marshaller.ProxyUnwrappingMarshaller;
import org.grails.web.json.JsonDateFormat;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class ConvertersConfigurationInitializer implements ApplicationContextAware, GrailsApplicationAware, InitializingBean {

    public static final String SETTING_CONVERTERS_JSON_DATE = "grails.converters.json.date";
    public static final String SETTING_CONVERTERS_JSON_DEFAULT_DEEP = "grails.converters.json.default.deep";
    /**
     * Whether domain classes are registered with the application's {@code JsonMapper}, so that it renders them as the
     * JSON converter does, including when a Spring MVC controller returns one. Defaults to {@code false}, so that the
     * application's {@code JsonMapper} renders domain classes as Jackson beans, as it did in Grails 8. The JSON converter
     * renders domain classes with its own serializer either way.
     *
     * @since 9.0
     */
    public static final String SETTING_CONVERTERS_JSON_DOMAIN_JACKSON_ENABLED = "grails.converters.json.domain.jackson.enabled";
    /**
     * Whether the JSON converter renders values as Grails 8 did, with the marshallers of Grails 8 rather than the
     * application's {@code JsonMapper}. Defaults to {@code false}.
     *
     * @since 9.0
     */
    public static final String SETTING_CONVERTERS_JSON_LEGACY = "grails.converters.json.legacy";
    public static final String SETTING_CONVERTERS_ENCODING = "grails.converters.encoding";
    public static final String SETTING_CONVERTERS_CIRCULAR_REFERENCE_BEHAVIOUR = "grails.converters.default.circular.reference.behaviour";
    public static final String SETTING_CONVERTERS_JSON_CIRCULAR_REFERENCE_BEHAVIOUR = "grails.converters.json.circular.reference.behaviour";
    public static final String SETTING_CONVERTERS_PRETTY_PRINT = "grails.converters.default.pretty.print";
    public static final String SETTING_CONVERTERS_JSON_PRETTY_PRINT = "grails.converters.json.pretty.print";
    public static final String SETTING_CONVERTERS_JSON_CACHE_OBJECTS = "grails.converters.json.cacheObjectMarshallerSelectionByClass";
    public static final String SETTING_CONVERTERS_XML_DEEP = "grails.converters.xml.default.deep";

    private ApplicationContext applicationContext;
    private GrailsApplication grailsApplication;

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public final Log LOG = LogFactory.getLog(ConvertersConfigurationInitializer.class);

    public void afterPropertiesSet() {
        initialize();
    }

    public void initialize() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initializing Converters Default Configurations...");
        }
        if (applicationContext != null) {
            ConvertersConfigurationHolder.setObservationRegistry(
                    applicationContext.getBeanProvider(ObservationRegistry.class).getIfAvailable(() -> ObservationRegistry.NOOP));
            // the application's JsonMapper, or a default one when there is none, or more than one and none is primary.
            // Rendering as Grails 8 did, a default one, so that the application's Jackson settings do not change the text
            boolean legacy = getGrailsConfig().getProperty(SETTING_CONVERTERS_JSON_LEGACY, Boolean.class, false);
            ConvertersConfigurationHolder.setJsonMapper(legacy ? null : applicationContext.getBeanProvider(JsonMapper.class).getIfUnique());
        }
        initJSONConfiguration();
        initXMLConfiguration();
        initDeepJSONConfiguration();
        initDeepXMLConfiguration();
    }

    private void initJSONConfiguration() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initializing default JSON Converters Configuration...");
        }

        List<ObjectMarshaller<JSON>> marshallers = new ArrayList<>();
        marshallers.addAll(getPreviouslyConfiguredMarshallers(JSON.class));

        Config grailsConfig = getGrailsConfig();
        ProxyHandler proxyHandler = getProxyHandler();
        boolean legacy = grailsConfig.getProperty(SETTING_CONVERTERS_JSON_LEGACY, Boolean.class, false);
        ConvertersConfigurationHolder.setLegacyJson(legacy);
        if (legacy) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using the JSON marshallers of Grails 8 (" + SETTING_CONVERTERS_JSON_LEGACY + ").");
            }
            addLegacyJsonMarshallers(marshallers, grailsConfig, proxyHandler);
        }
        else {
            addJsonMarshallers(marshallers, grailsConfig, proxyHandler);
        }

        DefaultConverterConfiguration<JSON> cfg = new DefaultConverterConfiguration<>(marshallers, proxyHandler);
        cfg.setEncoding(grailsConfig.getProperty(SETTING_CONVERTERS_ENCODING, "UTF-8"));
        String defaultCirRefBehaviour = grailsConfig.getProperty(SETTING_CONVERTERS_CIRCULAR_REFERENCE_BEHAVIOUR, "DEFAULT");
        cfg.setCircularReferenceBehaviour(Converter.CircularReferenceBehaviour.valueOf(
                grailsConfig.getProperty(SETTING_CONVERTERS_JSON_CIRCULAR_REFERENCE_BEHAVIOUR, String.class,
                      defaultCirRefBehaviour, Converter.CircularReferenceBehaviour.allowedValues())));

        Boolean defaultPrettyPrint = grailsConfig.getProperty(SETTING_CONVERTERS_PRETTY_PRINT, Boolean.class, false);
        Boolean prettyPrint = grailsConfig.getProperty(SETTING_CONVERTERS_JSON_PRETTY_PRINT, Boolean.class, defaultPrettyPrint);
        cfg.setPrettyPrint(prettyPrint);
        cfg.setCacheObjectMarshallerByClass(grailsConfig.getProperty(SETTING_CONVERTERS_JSON_CACHE_OBJECTS, Boolean.class, true));

        registerObjectMarshallersFromApplicationContext(cfg, JSON.class);

        ConvertersConfigurationHolder.setDefaultConfiguration(JSON.class, new ChainedConverterConfiguration<>(cfg, proxyHandler));
    }

    private void addJsonMarshallers(List<ObjectMarshaller<JSON>> marshallers, Config grailsConfig, ProxyHandler proxyHandler) {
        if ("javascript".equals(grailsConfig.getProperty(SETTING_CONVERTERS_JSON_DATE, String.class, "default", Arrays.asList("javascript", "default")))) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using Javascript JSON Date Marshaller.");
            }
            marshallers.add(new org.grails.web.converters.marshaller.json.JavascriptDateMarshaller());
        }
        // domain classes, and proxies of them, are claimed first: the application's JsonMapper may have a serializer for
        // them, and they are rendered with the converter's includes, excludes and deep setting
        marshallers.add(new org.grails.web.converters.marshaller.ProxyUnwrappingMarshaller<>());
        marshallers.add(domainClassMarshaller(grailsConfig, proxyHandler));
        // dates and times and the other single values the application's JsonMapper has a serializer for are written
        // by the mapper, as Spring Boot writes them
        marshallers.add(new org.grails.web.converters.marshaller.json.JsonMapperValueMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.SimpleEnumMarshaller(true));
        marshallers.add(new org.grails.web.converters.marshaller.json.RecordMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.OptionalMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.ArrayMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.CollectionMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.MapMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.GroovyBeanMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.GenericJavaBeanMarshaller());
    }

    /**
     * The marshallers of Grails 8, which render values as Grails 8 did.
     */
    @SuppressWarnings("deprecation")
    private void addLegacyJsonMarshallers(List<ObjectMarshaller<JSON>> marshallers, Config grailsConfig, ProxyHandler proxyHandler) {
        marshallers.add(new org.grails.web.converters.marshaller.json.ArrayMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.ByteArrayMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.CollectionMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.MapMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.SimpleEnumMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.ProxyUnwrappingMarshaller<>());
        if ("javascript".equals(grailsConfig.getProperty(SETTING_CONVERTERS_JSON_DATE, String.class, "default", Arrays.asList("javascript", "default")))) {
            marshallers.add(new org.grails.web.converters.marshaller.json.JavascriptDateMarshaller());
        }
        else {
            // ahead of DateMarshaller, which also supports java.sql.Time
            marshallers.add(new SimpleTypeMarshaller<>(Time.class, Time::toString));
            marshallers.add(new org.grails.web.converters.marshaller.json.DateMarshaller());
        }
        marshallers.add(new org.grails.web.converters.marshaller.json.CalendarMarshaller());
        marshallers.add(new SimpleTypeMarshaller<>(XMLGregorianCalendar.class,
                calendar -> JsonDateFormat.format(calendar.toGregorianCalendar().getTimeInMillis())));
        marshallers.add(new org.grails.web.converters.marshaller.json.InstantMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.LocalDateMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.LocalDateTimeMarshaller());
        marshallers.add(new SimpleTypeMarshaller<>(LocalTime.class, DateTimeFormatter.ISO_LOCAL_TIME::format));
        marshallers.add(new org.grails.web.converters.marshaller.json.OffsetDateTimeMarshaller());
        marshallers.add(new SimpleTypeMarshaller<>(OffsetTime.class, DateTimeFormatter.ISO_OFFSET_TIME::format));
        marshallers.add(new org.grails.web.converters.marshaller.json.ZonedDateTimeMarshaller());
        marshallers.add(new SimpleTypeMarshaller<>(Year.class, Year::getValue));
        marshallers.add(new SimpleTypeMarshaller<>(YearMonth.class, YearMonth::toString));
        marshallers.add(new SimpleTypeMarshaller<>(MonthDay.class, MonthDay::toString));
        marshallers.add(new SimpleTypeMarshaller<>(Duration.class, Duration::toString));
        marshallers.add(new SimpleTypeMarshaller<>(Period.class, Period::toString));
        marshallers.add(new SimpleTypeMarshaller<>(ZoneId.class, ZoneId::getId));
        marshallers.add(new SimpleTypeMarshaller<>(TimeZone.class, TimeZone::getID));
        marshallers.add(new SimpleTypeMarshaller<>(javax.xml.datatype.Duration.class, javax.xml.datatype.Duration::toString));
        marshallers.add(new org.grails.web.converters.marshaller.json.ToStringBeanMarshaller());
        marshallers.add(domainClassMarshaller(grailsConfig, proxyHandler));
        marshallers.add(new org.grails.web.converters.marshaller.json.GroovyBeanMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.json.GenericJavaBeanMarshaller());
    }

    private ObjectMarshaller<JSON> domainClassMarshaller(Config grailsConfig, ProxyHandler proxyHandler) {
        boolean includeDomainVersion = includeDomainVersionProperty(grailsConfig, "json");
        boolean includeDomainClassName = includeDomainClassProperty(grailsConfig, "json");
        if (grailsConfig.getProperty(SETTING_CONVERTERS_JSON_DEFAULT_DEEP, Boolean.class, false)) {
            LOG.debug("Using DeepDomainClassMarshaller as default.");
            return new org.grails.web.converters.marshaller.json.DeepDomainClassMarshaller(includeDomainVersion, includeDomainClassName, proxyHandler, grailsApplication);
        }
        return new org.grails.web.converters.marshaller.json.DomainClassMarshaller(includeDomainVersion, includeDomainClassName, proxyHandler, grailsApplication);
    }

    private Config getGrailsConfig() {
        Config grailsConfig;
        if (grailsApplication != null) {
            grailsConfig = grailsApplication.getConfig();
        }
        else {
            // empty config, will trigger defaults
            grailsConfig = new PropertySourcesConfig();
        }
        return grailsConfig;
    }

    private void initDeepJSONConfiguration() {
        DefaultConverterConfiguration<JSON> deepConfig = new DefaultConverterConfiguration<>(ConvertersConfigurationHolder.getConverterConfiguration(JSON.class), getProxyHandler());
        deepConfig.registerObjectMarshaller(new org.grails.web.converters.marshaller.json.DeepDomainClassMarshaller(includeDomainVersionProperty(getGrailsConfig(), "json"), includeDomainClassProperty(getGrailsConfig(), "json"), getProxyHandler(), grailsApplication));
        ConvertersConfigurationHolder.setNamedConverterConfiguration(JSON.class, "deep", deepConfig);
    }

    private void initXMLConfiguration() {
        LOG.debug("Initializing default XML Converters Configuration...");

        List<ObjectMarshaller<XML>> marshallers = new ArrayList<>();
        marshallers.addAll(getPreviouslyConfiguredMarshallers(XML.class));
        marshallers.add(new org.grails.web.converters.marshaller.xml.Base64ByteArrayMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.ArrayMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.CollectionMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.MapMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.SimpleEnumMarshaller());

        Config grailsConfig = getGrailsConfig();

        marshallers.add(new org.grails.web.converters.marshaller.xml.DateMarshaller());
        marshallers.add(new ProxyUnwrappingMarshaller<>());
        marshallers.add(new org.grails.web.converters.marshaller.xml.ToStringBeanMarshaller());
        ProxyHandler proxyHandler = getProxyHandler();

        boolean includeDomainVersion = includeDomainVersionProperty(grailsConfig, "xml");
        if (grailsConfig.getProperty(SETTING_CONVERTERS_XML_DEEP, Boolean.class, false)) {
            marshallers.add(new org.grails.web.converters.marshaller.xml.DeepDomainClassMarshaller(includeDomainVersion, proxyHandler, grailsApplication));
        }
        else {
            marshallers.add(new org.grails.web.converters.marshaller.xml.DomainClassMarshaller(includeDomainVersion, proxyHandler, grailsApplication));
        }
        marshallers.add(new org.grails.web.converters.marshaller.xml.GroovyBeanMarshaller());
        marshallers.add(new org.grails.web.converters.marshaller.xml.GenericJavaBeanMarshaller());

        DefaultConverterConfiguration<XML> cfg = new DefaultConverterConfiguration<>(marshallers, proxyHandler);
        cfg.setEncoding(grailsConfig.getProperty(SETTING_CONVERTERS_ENCODING, "UTF-8"));
        String defaultCirRefBehaviour = grailsConfig.getProperty(SETTING_CONVERTERS_CIRCULAR_REFERENCE_BEHAVIOUR, "DEFAULT");
        cfg.setCircularReferenceBehaviour(Converter.CircularReferenceBehaviour.valueOf(
                grailsConfig.getProperty("grails.converters.xml.circular.reference.behaviour", String.class,
                      defaultCirRefBehaviour, Converter.CircularReferenceBehaviour.allowedValues())));

        Boolean defaultPrettyPrint = grailsConfig.getProperty(SETTING_CONVERTERS_PRETTY_PRINT, Boolean.class, false);
        Boolean prettyPrint = grailsConfig.getProperty("grails.converters.xml.pretty.print", Boolean.class, defaultPrettyPrint);
        cfg.setPrettyPrint(prettyPrint);
        cfg.setCacheObjectMarshallerByClass(grailsConfig.getProperty("grails.converters.xml.cacheObjectMarshallerSelectionByClass", Boolean.class, true));
        registerObjectMarshallersFromApplicationContext(cfg, XML.class);
        ConvertersConfigurationHolder.setDefaultConfiguration(XML.class, new ChainedConverterConfiguration<>(cfg, proxyHandler));
    }

    private ProxyHandler getProxyHandler() {
        ProxyHandler proxyHandler;
        if (applicationContext != null) {
            proxyHandler = applicationContext.getBean(ProxyHandler.class);
        }
        else {
            proxyHandler = new DefaultProxyHandler();
        }
        return proxyHandler;
    }

    private void initDeepXMLConfiguration() {
        DefaultConverterConfiguration<XML> deepConfig = new DefaultConverterConfiguration<>(ConvertersConfigurationHolder.getConverterConfiguration(XML.class), getProxyHandler());
        deepConfig.registerObjectMarshaller(new org.grails.web.converters.marshaller.xml.DeepDomainClassMarshaller(includeDomainVersionProperty(getGrailsConfig(), "xml"), includeDomainClassProperty(getGrailsConfig(), "xml"), getProxyHandler(), grailsApplication));
        ConvertersConfigurationHolder.setNamedConverterConfiguration(XML.class, "deep", deepConfig);
    }

    /**
     * How the application's {@code JsonMapper} renders domain classes: as the JSON converter renders them by default,
     * with {@code grails.converters.json.domain.include.version}, {@code grails.converters.json.domain.include.class}
     * and {@code grails.converters.json.default.deep}.
     *
     * @param grailsApplication the application
     * @param proxyHandler unwraps proxied domain class instances
     * @return the rendering
     * @since 9.0
     */
    public static DomainClassRendering jsonDomainClassRendering(GrailsApplication grailsApplication, ProxyHandler proxyHandler) {
        Config config = grailsApplication != null ? grailsApplication.getConfig() : new PropertySourcesConfig();
        return new DomainClassRendering(grailsApplication, proxyHandler, includeDomainVersionProperty(config, "json"),
                includeDomainClassProperty(config, "json"), config.getProperty(SETTING_CONVERTERS_JSON_DEFAULT_DEEP, Boolean.class, false));
    }

    private static boolean includeDomainVersionProperty(Config grailsConfig, String converterType) {
        return grailsConfig.getProperty(String.format("grails.converters.%s.domain.include.version", converterType), Boolean.class, grailsConfig.getProperty("grails.converters.domain.include.version", Boolean.class, false));
    }

    private static boolean includeDomainClassProperty(Config grailsConfig, String converterType) {
        return grailsConfig.getProperty(String.format("grails.converters.%s.domain.include.class", converterType), Boolean.class, grailsConfig.getProperty("grails.converters.domain.include.class", Boolean.class, false));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <C extends Converter> void registerObjectMarshallersFromApplicationContext(
            DefaultConverterConfiguration<C> cfg, Class<C> converterClass) {

        if (applicationContext == null) {
            return;
        }

        for (Object o : applicationContext.getBeansOfType(ObjectMarshallerRegisterer.class).values()) {
            ObjectMarshallerRegisterer omr = (ObjectMarshallerRegisterer) o;
            if (omr.getConverterClass() == converterClass) {
                cfg.registerObjectMarshaller(omr.getMarshaller(), omr.getPriority());
            }
        }
    }

    @Override
    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    private <C extends Converter> List<ObjectMarshaller<C>> getPreviouslyConfiguredMarshallers(Class<C> converterClass) {
        ConverterConfiguration<C> previousConfiguration = ConvertersConfigurationHolder.getConverterConfiguration(converterClass);
        return previousConfiguration.getOrderedObjectMarshallers();
    }
}
