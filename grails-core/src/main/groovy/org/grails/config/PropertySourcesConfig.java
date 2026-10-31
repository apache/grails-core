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
package org.grails.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import groovy.util.ConfigObject;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;

import grails.util.GrailsStringUtils;

/**
 * @author Graeme Rocher
 * @since 3.0
 */
public class PropertySourcesConfig extends NavigableMapConfig {

    protected PropertySources propertySources;
    protected PropertySourcesPropertyResolver propertySourcesPropertyResolver;

    public PropertySourcesConfig(PropertySources propertySources) {
        this.propertySources = propertySources;
        this.propertySourcesPropertyResolver = new PropertySourcesPropertyResolver(propertySources);
        initializeFromPropertySources(propertySources);
    }

    public PropertySourcesConfig() {
        this.propertySources = new MutablePropertySources();
        this.propertySourcesPropertyResolver = new PropertySourcesPropertyResolver(propertySources);
    }

    public PropertySourcesConfig(Map<String, Object> mapPropertySource) {
        MutablePropertySources mutablePropertySources = new MutablePropertySources();
        NavigableMap map = new NavigableMap();
        map.merge(mapPropertySource, true);

        mutablePropertySources.addFirst(new MapPropertySource("config", map));
        this.propertySources = mutablePropertySources;
        this.propertySourcesPropertyResolver = new PropertySourcesPropertyResolver(propertySources);
        initializeFromPropertySources(propertySources);
    }

    public PropertySourcesConfig(PropertySource propertySource) {
        MutablePropertySources mutablePropertySources = new MutablePropertySources();
        mutablePropertySources.addFirst(propertySource);
        this.propertySources = mutablePropertySources;
        this.propertySourcesPropertyResolver = new PropertySourcesPropertyResolver(propertySources);
        initializeFromPropertySources(propertySources);
    }

    public PropertySources getPropertySources() {
        return propertySources;
    }

    public void refresh() {
        initializeFromPropertySources(propertySources);
    }

    protected void initializeFromPropertySources(PropertySources propertySources) {

        EnvironmentAwarePropertySource environmentAwarePropertySource = new EnvironmentAwarePropertySource(propertySources);
        if (propertySources instanceof MutablePropertySources) {
            final String applicationConfig = "applicationConfigurationProperties";
            if (propertySources.contains(applicationConfig)) {
                ((MutablePropertySources) propertySources).addBefore(applicationConfig, environmentAwarePropertySource);
            } else {
                ((MutablePropertySources) propertySources).addLast(environmentAwarePropertySource);
            }
        }

        List<PropertySource<?>> propertySourceList = DefaultGroovyMethods.toList(propertySources);
        Collections.reverse(propertySourceList);
        for (PropertySource propertySource : propertySourceList) {
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource enumerablePropertySource = (EnumerablePropertySource) propertySource;
                mergeEnumerablePropertySource(enumerablePropertySource);
            }
        }
    }

    private void mergeEnumerablePropertySource(EnumerablePropertySource enumerablePropertySource) {
        Map<Object, Object> copies = new IdentityHashMap<>();
        if (enumerablePropertySource instanceof NavigableMapPropertySource) {
            configMap.merge((Map<?, ?>) copyOf(((NavigableMapPropertySource) enumerablePropertySource).getSource(), copies), false);
        } else {
            Map<String, Object> map = new LinkedHashMap<>();

            final String[] propertyNames = enumerablePropertySource.getPropertyNames();
            for (String propertyName : propertyNames) {
                Object value = enumerablePropertySource.getProperty(propertyName);
                if (value instanceof ConfigObject && ((ConfigObject) value).isEmpty()) {
                    continue;
                }
                map.put(propertyName, copyOf(value, copies));
            }

            configMap.merge(map, true);
        }
    }

    /**
     * A copy of a value of a property source, with a copy of each list and map in it, so the config never holds the
     * source's own list, which a later indexed write, such as {@code app.list[1]} from a property source of higher
     * precedence, would change in place. A list or map the source holds under several keys, as under a flat key and in
     * its nested map, is copied once, so the config holds one list under each of them, as it held the source's.
     */
    @SuppressWarnings("unchecked")
    private static Object copyOf(Object value, Map<Object, Object> copies) {
        if (!(value instanceof List || value instanceof Map)) {
            return value;
        }
        Object copied = copies.get(value);
        if (copied != null) {
            return copied;
        }
        if (value instanceof List) {
            List<Object> copy = shallowCopyOf((List<Object>) value);
            copies.put(value, copy);
            for (ListIterator<Object> elements = copy.listIterator(); elements.hasNext();) {
                elements.set(copyOf(elements.next(), copies));
            }
            return copy;
        }
        Map<Object, Object> copy = value instanceof NavigableMap ?
                (Map<Object, Object>) (Map<?, ?>) ((NavigableMap) value).clone() : new LinkedHashMap<>();
        copies.put(value, copy);
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            copy.put(entry.getKey(), copyOf(entry.getValue(), copies));
        }
        return copy;
    }

    /**
     * A list of the same kind as the one given, as the config held the source's own list before, or an
     * {@link ArrayList} for a list that cannot be copied as its kind, such as an unmodifiable one.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> shallowCopyOf(List<Object> list) {
        if (list instanceof ArrayList) {
            return (List<Object>) ((ArrayList<Object>) list).clone();
        }
        if (list instanceof LinkedList) {
            return (List<Object>) ((LinkedList<Object>) list).clone();
        }
        return new ArrayList<>(list);
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void setConversionService(ConfigurableConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public String resolvePlaceholders(String text) {
        if (!GrailsStringUtils.isBlank(text)) {
            return propertySourcesPropertyResolver.resolvePlaceholders(text);
        }
        return text;
    }

    @Override
    public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
        return propertySourcesPropertyResolver.resolveRequiredPlaceholders(text);
    }
}
