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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.datatype.XMLGregorianCalendar;

import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.PropertyResolver;
import org.springframework.util.ClassUtils;

import grails.config.Config;
import grails.converters.JSON;
import grails.core.GrailsApplication;
import grails.core.support.proxy.ProxyHandler;
import org.apache.grails.converters.internal.json.SimpleTypeMarshaller;
import org.grails.config.PropertySourcesConfig;
import org.grails.core.lifecycle.ShutdownOperations;
import org.grails.web.converters.Converter.CircularReferenceBehaviour;
import org.grails.web.converters.jackson.ConverterJsonModule;
import org.grails.web.converters.jackson.DomainClassJacksonModule;
import org.grails.web.converters.jackson.DomainClassRendering;
import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.json.JsonDateFormat;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class ConvertersConfigurationInitializer extends AbstractConvertersConfigurationInitializer {

    public static final String SETTING_CONVERTERS_JSON_DATE = "grails.converters.json.date";
    public static final String SETTING_CONVERTERS_JSON_DEFAULT_DEEP = "grails.converters.json.default.deep";
    /**
     * Whether domain classes are registered with the application's {@code JsonMapper}, so that it renders them with the
     * serializer the JSON converter renders them with when {@link #SETTING_CONVERTERS_JSON_LEGACY} is {@code false},
     * including when a Spring MVC controller returns one; the marshallers and the rule for enums the converter applies on
     * top do not apply there. Defaults to {@code false}, so that the application's {@code JsonMapper} renders domain
     * classes as Jackson beans, as it did in Grails 8. The JSON converter's rendering does not depend on it.
     *
     * @since 9.0
     */
    public static final String SETTING_CONVERTERS_JSON_DOMAIN_JACKSON_ENABLED = "grails.converters.json.domain.jackson.enabled";
    /**
     * Whether the JSON converter renders values as Grails 8 did, with the marshallers of Grails 8 rather than the
     * application's {@code JsonMapper}. Defaults to {@code true} in Grails 9: set it to {@code false} to render JSON
     * through the application's {@code JsonMapper}, which Grails 10 makes the default, together with
     * {@code grails.web.rendering.json.spring} and {@code grails.databinding.json.jackson}.
     *
     * @since 9.0
     */
    public static final String SETTING_CONVERTERS_JSON_LEGACY = "grails.converters.json.legacy";

    /**
     * The default of {@link #SETTING_CONVERTERS_JSON_LEGACY} in Grails 9, which Grails 10 changes; read through
     * {@link #isLegacyJson(PropertyResolver)}, so that code compiled against this release does not keep it.
     */
    private static final boolean LEGACY_JSON_BY_DEFAULT = true;

    /**
     * Whether the notice that {@link #SETTING_CONVERTERS_JSON_LEGACY} is not set has been logged, so that it is logged
     * once for each application, however many times converters are initialized; it is logged again after the application
     * shuts down, as for a restart, as the other notices of the converters are.
     */
    private static final AtomicBoolean LEGACY_JSON_DEFAULT_LOGGED = new AtomicBoolean();

    /**
     * @param properties the application's configuration, or its environment
     * @return whether the JSON converter renders values as Grails 8 did: what {@link #SETTING_CONVERTERS_JSON_LEGACY} is
     *         set to, or the default of this release
     * @since 9.0
     */
    public static boolean isLegacyJson(PropertyResolver properties) {
        return properties.getProperty(SETTING_CONVERTERS_JSON_LEGACY, Boolean.class, LEGACY_JSON_BY_DEFAULT);
    }

    static {
        ShutdownOperations.addOperation(() -> LEGACY_JSON_DEFAULT_LOGGED.set(false), true);
    }

    public static final String SETTING_CONVERTERS_ENCODING = "grails.converters.encoding";
    public static final String SETTING_CONVERTERS_CIRCULAR_REFERENCE_BEHAVIOUR = "grails.converters.default.circular.reference.behaviour";
    public static final String SETTING_CONVERTERS_JSON_CIRCULAR_REFERENCE_BEHAVIOUR = "grails.converters.json.circular.reference.behaviour";
    public static final String SETTING_CONVERTERS_PRETTY_PRINT = "grails.converters.default.pretty.print";
    public static final String SETTING_CONVERTERS_JSON_PRETTY_PRINT = "grails.converters.json.pretty.print";
    public static final String SETTING_CONVERTERS_JSON_CACHE_OBJECTS = "grails.converters.json.cacheObjectMarshallerSelectionByClass";

    /**
     * @deprecated Scheduled for removal in Grails 11. The XML converter is configured by the {@code grails-xml} module,
     * as {@code XmlConvertersConfigurationInitializer.SETTING_CONVERTERS_XML_DEEP}
     */
    @Deprecated(since = "9.0", forRemoval = true)
    public static final String SETTING_CONVERTERS_XML_DEEP = "grails.converters.xml.default.deep";

    public final Log LOG = LogFactory.getLog(ConvertersConfigurationInitializer.class);

    /** The name of Spring Boot's JsonMapper bean. */
    private static final String JACKSON_JSON_MAPPER = "jacksonJsonMapper";

    /**
     * The initializer of the XML converter, found by name since {@code grails-xml} depends on this module, or
     * {@code null} without it; {@code grails-xml} registers it for an ahead-of-time image.
     */
    private static final Class<? extends AbstractConvertersConfigurationInitializer> XML_INITIALIZER = xmlInitializer();

    /** Whether this initializer is starting as a bean, when the XML plugin's own initializer configures the XML converter. */
    private boolean starting;

    @Override
    public void afterPropertiesSet() {
        starting = true;
        try {
            super.afterPropertiesSet();
        }
        finally {
            starting = false;
        }
    }

    /**
     * Initializes the default and named configurations of the JSON converter and, when the application includes the
     * {@code grails-xml} module, of its XML converter, as before that converter moved there. Starting as a bean beside the
     * XML plugin's initializer, it leaves the XML converter to that bean.
     */
    @Override
    public void initialize() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initializing Converters Default Configurations...");
        }
        boolean legacy = isLegacyJson(getGrailsConfig());
        if (!legacy && "javascript".equals(jsonDateSetting(getGrailsConfig()))) {
            throw new IllegalStateException(SETTING_CONVERTERS_JSON_DATE + ": javascript writes dates as JavaScript new Date(...) " +
                    "expressions, which are not JSON: with " + SETTING_CONVERTERS_JSON_LEGACY + ": false, JSON.parse and " +
                    "request.JSON parse with the application's JsonMapper, which cannot read them back. Set " +
                    SETTING_CONVERTERS_JSON_LEGACY + " to true to keep them, or remove " + SETTING_CONVERTERS_JSON_DATE +
                    " to write dates as the JsonMapper writes them.");
        }
        ApplicationContext applicationContext = getApplicationContext();
        if (applicationContext != null) {
            ConvertersConfigurationHolder.setObservationRegistry(
                    applicationContext.getBeanProvider(ObservationRegistry.class).getIfAvailable(() -> ObservationRegistry.NOOP));
            // the application's JsonMapper: its only or primary one, or else Spring Boot's, as respond and data binding
            // find it, or a default one where it has none of those. Rendering as Grails 8 did, a default one, so that the
            // application's Jackson settings do not change the text
            ConvertersConfigurationHolder.setJsonMapper(legacy ? null : withDomainClasses(applicationJsonMapper(applicationContext)));
        }
        else {
            // a default one, also where the converters were initialized before without being cleared
            ConvertersConfigurationHolder.setJsonMapper(legacy ? null : withDomainClasses(null));
        }
        initJSONConfiguration(legacy);
        initDeepJSONConfiguration();
        initXmlConfiguration();
    }

    private void initXmlConfiguration() {
        if (XML_INITIALIZER == null) {
            return;
        }
        ApplicationContext applicationContext = getApplicationContext();
        if (starting && applicationContext != null && BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                applicationContext, XML_INITIALIZER, true, false).length > 0) {
            // the XML plugin's initializer bean configures it as it starts
            return;
        }
        AbstractConvertersConfigurationInitializer xml = BeanUtils.instantiateClass(XML_INITIALIZER);
        xml.setApplicationContext(applicationContext);
        xml.setGrailsApplication(getGrailsApplication());
        xml.initialize();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AbstractConvertersConfigurationInitializer> xmlInitializer() {
        String name = "org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer";
        ClassLoader classLoader = ConvertersConfigurationInitializer.class.getClassLoader();
        return ClassUtils.isPresent(name, classLoader) ?
                (Class<? extends AbstractConvertersConfigurationInitializer>) ClassUtils.resolveClassName(name, classLoader) :
                null;
    }

    private static JsonMapper applicationJsonMapper(ApplicationContext applicationContext) {
        JsonMapper mapper = applicationContext.getBeanProvider(JsonMapper.class).getIfUnique();
        if (mapper == null && applicationContext.containsBean(JACKSON_JSON_MAPPER)) {
            mapper = applicationContext.getBean(JACKSON_JSON_MAPPER, JsonMapper.class);
        }
        return mapper;
    }

    /**
     * The mapper the JSON converter renders with: the application's own, or a default one with Jackson's limits, which
     * the legacy writer's has not, with the modules of the converter, as Grails responses derive theirs.
     */
    private JsonMapper withDomainClasses(JsonMapper mapper) {
        JsonMapper source = mapper != null ? mapper : JsonMapper.builder().build();
        return GrailsJsonMapperCustomizer.withGrailsModules(source,
                // the converter writes each instance with its own rendering
                new DomainClassJacksonModule(() -> jsonDomainClassRendering(getGrailsApplication(), getProxyHandler()), true),
                new ConverterJsonModule(this::getApplicationContext))
                // a bean with no properties is written as an empty object, as the converter always has
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
    }

    private void initJSONConfiguration(boolean legacy) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initializing default JSON Converters Configuration...");
        }

        List<ObjectMarshaller<JSON>> marshallers = new ArrayList<>();
        marshallers.addAll(getPreviouslyConfiguredMarshallers(JSON.class));

        Config grailsConfig = getGrailsConfig();
        ProxyHandler proxyHandler = getProxyHandler();
        ConvertersConfigurationHolder.setLegacyJson(legacy);
        if (!grailsConfig.containsProperty(SETTING_CONVERTERS_JSON_LEGACY) && LEGACY_JSON_DEFAULT_LOGGED.compareAndSet(false, true)) {
            LOG.warn(SETTING_CONVERTERS_JSON_LEGACY + " is not set, so the JSON converter renders JSON as Grails 8 did. " +
                    "Grails 10 renders JSON through the application's JsonMapper by default. Set it to true to keep " +
                    "the Grails 8 rendering, or to false to render through the JsonMapper now; see \"Rendering JSON As " +
                    "Grails 8 Did\" in the upgrade guide.");
        }
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
        cfg.setCircularReferenceBehaviour(jsonCircularReferenceBehaviour(grailsConfig));

        Boolean defaultPrettyPrint = grailsConfig.getProperty(SETTING_CONVERTERS_PRETTY_PRINT, Boolean.class, false);
        Boolean prettyPrint = grailsConfig.getProperty(SETTING_CONVERTERS_JSON_PRETTY_PRINT, Boolean.class, defaultPrettyPrint);
        cfg.setPrettyPrint(prettyPrint);
        cfg.setCacheObjectMarshallerByClass(grailsConfig.getProperty(SETTING_CONVERTERS_JSON_CACHE_OBJECTS, Boolean.class, true));

        registerObjectMarshallersFromApplicationContext(cfg, JSON.class);

        ConvertersConfigurationHolder.setDefaultConfiguration(JSON.class, new ChainedConverterConfiguration<>(cfg, proxyHandler));
    }

    private void addJsonMarshallers(List<ObjectMarshaller<JSON>> marshallers, Config grailsConfig, ProxyHandler proxyHandler) {
        // domain classes, and proxies of them, are claimed first: the application's JsonMapper may have a serializer for
        // them, and they are rendered with the converter's includes, excludes and deep setting
        marshallers.add(new org.grails.web.converters.marshaller.ProxyUnwrappingMarshaller<>());
        marshallers.add(domainClassMarshaller(grailsConfig, proxyHandler));
        // everything else is written by the application's JsonMapper, as Spring Boot writes it: dates and times, the other
        // single values it has a serializer for, collections, maps, arrays, records, enums and beans
        marshallers.add(new org.grails.web.converters.marshaller.json.JsonMapperValueMarshaller());
    }

    /**
     * @return how the JSON converter writes dates: {@code javascript} or {@code default}
     */
    private static String jsonDateSetting(Config grailsConfig) {
        return grailsConfig.getProperty(SETTING_CONVERTERS_JSON_DATE, String.class, "default", Arrays.asList("javascript", "default"));
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
        if ("javascript".equals(jsonDateSetting(grailsConfig))) {
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
            return new org.grails.web.converters.marshaller.json.DeepDomainClassMarshaller(includeDomainVersion, includeDomainClassName, proxyHandler, getGrailsApplication());
        }
        return new org.grails.web.converters.marshaller.json.DomainClassMarshaller(includeDomainVersion, includeDomainClassName, proxyHandler, getGrailsApplication());
    }

    private void initDeepJSONConfiguration() {
        DefaultConverterConfiguration<JSON> deepConfig = new DefaultConverterConfiguration<>(ConvertersConfigurationHolder.getConverterConfiguration(JSON.class), getProxyHandler());
        deepConfig.registerObjectMarshaller(new org.grails.web.converters.marshaller.json.DeepDomainClassMarshaller(includeDomainVersionProperty(getGrailsConfig(), "json"), includeDomainClassProperty(getGrailsConfig(), "json"), getProxyHandler(), getGrailsApplication()));
        ConvertersConfigurationHolder.setNamedConverterConfiguration(JSON.class, "deep", deepConfig);
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
                includeDomainClassProperty(config, "json"), config.getProperty(SETTING_CONVERTERS_JSON_DEFAULT_DEEP, Boolean.class, false),
                jsonCircularReferenceBehaviour(config));
    }

    /**
     * What the JSON converter writes for an object that is reached again while it is being written:
     * {@code grails.converters.json.circular.reference.behaviour}, else {@code grails.converters.default.circular.reference.behaviour},
     * else {@code DEFAULT}.
     *
     * @param config the application's configuration
     * @return the behaviour
     * @since 9.0
     */
    public static CircularReferenceBehaviour jsonCircularReferenceBehaviour(Config config) {
        String fallback = config.getProperty(SETTING_CONVERTERS_CIRCULAR_REFERENCE_BEHAVIOUR, "DEFAULT");
        return CircularReferenceBehaviour.valueOf(config.getProperty(SETTING_CONVERTERS_JSON_CIRCULAR_REFERENCE_BEHAVIOUR,
                String.class, fallback, CircularReferenceBehaviour.allowedValues()));
    }

}
