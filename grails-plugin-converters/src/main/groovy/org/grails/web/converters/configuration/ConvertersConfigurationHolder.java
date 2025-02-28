/*
 * Copyright 2004-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.converters.configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
@SuppressWarnings({"unchecked","rawtypes"})
public class ConvertersConfigurationHolder {

    public static final String CONVERTERS_DEFAULT_ENCODING = "UTF-8";

    static {
        ShutdownOperations.addOperation(new Runnable() {
            public void run() {
                clear();
            }
        }, true);
    }

    private static ConvertersConfigurationHolder INSTANCE = new ConvertersConfigurationHolder();

    private final ConcurrentMap<Class<? extends Converter>, ConverterConfiguration> defaultConfiguration =
        new ConcurrentHashMap<Class<? extends Converter>, ConverterConfiguration>();

    private final ConcurrentMap<Class<? extends Converter>, Map<String, ConverterConfiguration>> namedConfigurations =
        new ConcurrentHashMap<Class<? extends Converter>, Map<String, ConverterConfiguration>>();

    private ThreadLocal<Map<Class<? extends Converter>, ConverterConfiguration>> threadLocalConfiguration = createThreadLocalConfiguration();

    protected static ThreadLocal<Map<Class<? extends Converter>, ConverterConfiguration>> createThreadLocalConfiguration() {
        return new ThreadLocal<Map<Class<? extends Converter>, ConverterConfiguration>>() {
            protected Map<java.lang.Class<? extends Converter>, ConverterConfiguration> initialValue() {
                return new HashMap<Class<? extends Converter>, ConverterConfiguration>();
            };
        };
    }

    private ConvertersConfigurationHolder() {
        // singleton
    }

    public static void clear() {
        final ConvertersConfigurationHolder configurationHolder = getInstance();
        configurationHolder.defaultConfiguration.clear();
        configurationHolder.namedConfigurations.clear();
        configurationHolder.threadLocalConfiguration = createThreadLocalConfiguration();
    }

    public static <C extends Converter> void setDefaultConfiguration(Class<C> c, ConverterConfiguration<C> cfg) {
        getInstance().defaultConfiguration.put(c, cfg);
    }

    public static <C extends Converter> void setDefaultConfiguration(Class<C> c, List<ObjectMarshaller<C>> om) {
        getInstance().defaultConfiguration.put(c, new DefaultConverterConfiguration<C>(om));
    }

    private static ConvertersConfigurationHolder getInstance() throws ConverterException{
        return INSTANCE;
    }

    public static <C extends Converter> ConverterConfiguration<C> getConverterConfiguration(Class<C> converterClass) throws ConverterException {
        ConverterConfiguration<C> cfg = getThreadLocalConverterConfiguration(converterClass);
        if (cfg == null) {
            cfg = getInstance().defaultConfiguration.get(converterClass);
            if (cfg == null) {
                cfg = new DefaultConverterConfiguration();
                ConverterConfiguration<C> existing = getInstance().defaultConfiguration.putIfAbsent(converterClass, cfg);
                if(existing != null) {
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
        return getInstance().threadLocalConfiguration.get().get(converterClass);
    }

    public static <C extends Converter> void setThreadLocalConverterConfiguration(Class<C> converterClass, ConverterConfiguration<C> cfg) throws ConverterException {
        getInstance().threadLocalConfiguration.get().put(converterClass, cfg);
    }

    public static <C extends Converter> void setNamedConverterConfiguration(Class<C> converterClass, String name, ConverterConfiguration<C> cfg) throws ConverterException {
        getNamedConfigMapForConverter(converterClass, true).put(name, cfg);
    }

    private static <C extends Converter> Map<String, ConverterConfiguration> getNamedConfigMapForConverter(Class<C> clazz, boolean create) {
        Map<String, ConverterConfiguration> namedConfigs = getInstance().namedConfigurations.get(clazz);
        if (namedConfigs == null && create) {
            namedConfigs = new HashMap<String, ConverterConfiguration>();
            getInstance().namedConfigurations.put(clazz, namedConfigs);
        }
        return namedConfigs;
    }

    public static <C extends Converter> void setNamedConverterConfiguration(Class<C> converterClass, String name, List<ObjectMarshaller<C>> om) throws ConverterException {
        getNamedConfigMapForConverter(converterClass, true).put(name, new DefaultConverterConfiguration<C>(om));
    }
}
