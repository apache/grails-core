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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.grails.core.lifecycle.ShutdownOperations;
import org.grails.web.converters.Converter;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * Singleton which holds all default and named configurations for the Converter classes.
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ConvertersConfigurationHolder {

    public static final String CONVERTERS_DEFAULT_ENCODING = "UTF-8";

    private static volatile ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

    @FunctionalInterface
    public interface ConverterAction {
        void run() throws ConverterException;
    }

    static {
        ShutdownOperations.addOperation(new Runnable() {
            public void run() {
                clear();
            }
        }, true);
    }

    private static ConvertersConfigurationHolder INSTANCE = new ConvertersConfigurationHolder();

    private final ConcurrentMap<Class<? extends Converter>, ConverterConfiguration> defaultConfiguration =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<Class<? extends Converter>, Map<String, ConverterConfiguration>> namedConfigurations =
            new ConcurrentHashMap<>();

    private ThreadLocal<Map<Class<? extends Converter>, ConverterConfiguration>> threadLocalConfiguration = createThreadLocalConfiguration();

    protected static ThreadLocal<Map<Class<? extends Converter>, ConverterConfiguration>> createThreadLocalConfiguration() {
        return new ThreadLocal<>();
    }

    private ConvertersConfigurationHolder() {
        // singleton
    }

    public static void clear() {
        final ConvertersConfigurationHolder configurationHolder = getInstance();
        configurationHolder.defaultConfiguration.clear();
        configurationHolder.namedConfigurations.clear();
        configurationHolder.threadLocalConfiguration = createThreadLocalConfiguration();
        observationRegistry = ObservationRegistry.NOOP;
    }

    /**
     * Runs converter rendering with observability instrumentation when available.
     *
     * @param format the converter format, for example {@code json} or {@code xml}
     * @param action the rendering action to invoke
     * @throws ConverterException when the rendering action fails with a converter exception
     */
    public static void withConverterObservation(String format, ConverterAction action) throws ConverterException {
        Objects.requireNonNull(format, "format cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        var registry = observationRegistry;
        if (registry == null || registry.isNoop()) {
            action.run();
            return;
        }

        var observation = Observation.createNotStarted("grails.convert", registry)
                .contextualName("grails.convert " + format)
                .lowCardinalityKeyValue("grails.convert.format", format)
                .start();

        var scope = observation.openScope();
        try {
            action.run();
        }
        catch (Throwable t) {
            observation.error(t);
            throw t;
        }
        finally {
            scope.close();
            observation.stop();
        }
    }

    static void setObservationRegistry(ObservationRegistry registry) {
        observationRegistry = (registry != null) ? registry : ObservationRegistry.NOOP;
    }

    public static <C extends Converter> void setDefaultConfiguration(Class<C> c, ConverterConfiguration<C> cfg) {
        getInstance().defaultConfiguration.put(c, cfg);
    }

    public static <C extends Converter> void setDefaultConfiguration(Class<C> c, List<ObjectMarshaller<C>> om) {
        getInstance().defaultConfiguration.put(c, new DefaultConverterConfiguration<>(om));
    }

    private static ConvertersConfigurationHolder getInstance() throws ConverterException {
        return INSTANCE;
    }

    public static <C extends Converter> ConverterConfiguration<C> getConverterConfiguration(Class<C> converterClass) throws ConverterException {
        ConverterConfiguration<C> cfg = getThreadLocalConverterConfiguration(converterClass);
        if (cfg == null) {
            cfg = getInstance().defaultConfiguration.get(converterClass);
            if (cfg == null) {
                cfg = new DefaultConverterConfiguration();
                ConverterConfiguration<C> existing = getInstance().defaultConfiguration.putIfAbsent(converterClass, cfg);
                if (existing != null) {
                    cfg = existing;
                }
            }
        }
        return cfg;
    }

    public static <C extends Converter> ConverterConfiguration<C> getNamedConverterConfiguration(String name, Class<C> converterClass) throws ConverterException {
        Map<String, ConverterConfiguration> map = getNamedConfigMapForConverter(converterClass, false);
        return map != null ? map.get(name) : null;
    }

    public static <C extends Converter> ConverterConfiguration<C> getThreadLocalConverterConfiguration(Class<C> converterClass) throws ConverterException {
        Map<Class<? extends Converter>, ConverterConfiguration> configurations = getThreadLocalConverterConfigurations(false);
        return configurations != null ? configurations.get(converterClass) : null;
    }

    public static <C extends Converter> void setThreadLocalConverterConfiguration(Class<C> converterClass, ConverterConfiguration<C> cfg) throws ConverterException {
        Map<Class<? extends Converter>, ConverterConfiguration> configurations = getThreadLocalConverterConfigurations(cfg != null);
        if (cfg == null) {
            if (configurations != null) {
                configurations.remove(converterClass);
                if (configurations.isEmpty()) {
                    getInstance().threadLocalConfiguration.remove();
                }
            }
        }
        else {
            configurations.put(converterClass, cfg);
        }
    }

    private static Map<Class<? extends Converter>, ConverterConfiguration> getThreadLocalConverterConfigurations(boolean create) {
        ThreadLocal<Map<Class<? extends Converter>, ConverterConfiguration>> threadLocalConfiguration = getInstance().threadLocalConfiguration;
        Map<Class<? extends Converter>, ConverterConfiguration> configurations = threadLocalConfiguration.get();
        if (configurations == null && create) {
            configurations = new HashMap<>();
            threadLocalConfiguration.set(configurations);
        }
        return configurations;
    }

    public static <C extends Converter> void setNamedConverterConfiguration(Class<C> converterClass, String name, ConverterConfiguration<C> cfg) throws ConverterException {
        getNamedConfigMapForConverter(converterClass, true).put(name, cfg);
    }

    private static <C extends Converter> Map<String, ConverterConfiguration> getNamedConfigMapForConverter(Class<C> clazz, boolean create) {
        Map<String, ConverterConfiguration> namedConfigs = getInstance().namedConfigurations.get(clazz);
        if (namedConfigs == null && create) {
            namedConfigs = new HashMap<>();
            getInstance().namedConfigurations.put(clazz, namedConfigs);
        }
        return namedConfigs;
    }

    public static <C extends Converter> void setNamedConverterConfiguration(Class<C> converterClass, String name, List<ObjectMarshaller<C>> om) throws ConverterException {
        getNamedConfigMapForConverter(converterClass, true).put(name, new DefaultConverterConfiguration<>(om));
    }
}
