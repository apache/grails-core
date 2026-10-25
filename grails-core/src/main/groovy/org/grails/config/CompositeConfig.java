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

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import groovy.transform.CompileStatic;

import grails.config.Config;
import grails.util.GrailsStringUtils;
import org.grails.core.exceptions.GrailsConfigurationException;

/**
 * A {@link Config} composed of other Configs. A lookup answers from the first config, in order of precedence,
 * that has the key, and the {@link Map} views, {@link #flatten()} and {@link #toProperties()} resolve a key
 * shared by several configs the same way, so each key appears once with its highest-precedence value.
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
public class CompositeConfig implements Config {

    protected Deque<Config> configs = new ArrayDeque<>();

    /**
     * Adds a config at the highest level of precedence
     *
     * @param config
     */
    public void addFirst(Config config) {
        configs.addFirst(config);
    }

    /**
     * Adds a config at the lowest level of precedence
     *
     * @param config
     */
    public void addLast(Config config) {
        configs.addLast(config);
    }

    @Override
    @Deprecated
    public Map<String, Object> flatten() {
        Map<String, Object> flattened = new LinkedHashMap<>();
        Iterator<Config> lowestPrecedenceFirst = configs.descendingIterator();
        while (lowestPrecedenceFirst.hasNext()) {
            flattened.putAll(lowestPrecedenceFirst.next().flatten());
        }
        return flattened;
    }

    @Override
    public Properties toProperties() {
        Properties properties = new Properties();
        Iterator<Config> lowestPrecedenceFirst = configs.descendingIterator();
        while (lowestPrecedenceFirst.hasNext()) {
            properties.putAll(lowestPrecedenceFirst.next().toProperties());
        }
        return properties;
    }

    @Override
    public Config merge(Map<String, Object> toMerge) {
        throw new UnsupportedOperationException("Config cannot be modified");
    }

    @Override
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue, List<T> allowedValues) {
        T v = getProperty(key, targetType, defaultValue);
        if (!allowedValues.contains(v)) {
            throw new GrailsConfigurationException("Invalid configuration value [$value] for key [${key}]. Possible values $allowedValues");
        }
        return v;
    }

    @Override
    public Object getAt(Object key) {
        for (Config c : configs) {
            if (c.containsKey(key)) {
                return c.getAt(key);
            }
        }
        for (Config c : configs) {
            Object v = c.getAt(key);
            if (v != null) return v;
        }
        return null;
    }

    @Override
    public void setAt(Object key, Object value) {
        throw new UnsupportedOperationException("Config cannot be modified");
    }

    @Override
    public Object navigate(String... path) {
        for (Config c : configs) {
            Object v = c.navigate(path);
            if (v != null) return v;
        }
        return null;
    }

    @Override
    public int size() {
        return keySet().size();
    }

    @Override
    public boolean isEmpty() {
        for (Config config : configs) {
            if (!config.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean containsKey(Object key) {
        for (Config config : configs) {
            if (config.containsKey(key)) return true;
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    /**
     * The value of the highest precedence config that has the key. A config answers a key it does not have with a
     * navigator for the keys below it, so a config is only asked when it has the key, and the first answer of any
     * config is returned only when none has it.
     */
    @Override
    public Object get(Object key) {
        for (Config config : configs) {
            if (config.containsKey(key)) {
                return config.get(key);
            }
        }
        for (Config config : configs) {
            Object v = config.get(key);
            if (v != null) return v;
        }
        return null;
    }

    @Override
    public Object put(String key, Object value) {
        throw new UnsupportedOperationException("Config cannot be modified");
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException("Config cannot be modified");
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        throw new UnsupportedOperationException("Config cannot be modified");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Config cannot be modified");
    }

    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        return entrySet().iterator();
    }

    @Override
    public Set<String> keySet() {
        Set<String> keys = new LinkedHashSet<>();
        for (Config config : configs) {
            keys.addAll(config.keySet());
        }
        return keys;
    }

    @Override
    public Collection<Object> values() {
        Collection<Object> values = new ArrayList<>();
        for (String key : keySet()) {
            values.add(get(key));
        }
        return values;
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        Set<Map.Entry<String, Object>> entries = new LinkedHashSet<>();
        for (String key : keySet()) {
            entries.add(new AbstractMap.SimpleImmutableEntry<>(key, get(key)));
        }
        return entries;
    }

    @Override
    public boolean containsProperty(String key) {
        return containsKey(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        String v = getProperty(key, String.class);
        return !GrailsStringUtils.isBlank(v) ? v : defaultValue;
    }

    @Override
    public <T> T getProperty(String key, Class<T> targetType) {
        for (Config config : configs) {
            T v = config.getProperty(key, targetType);
            if (v != null) return v;
        }
        return null;
    }

    @Override
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        T v = getProperty(key, targetType);
        return v != null ? v : defaultValue;
    }

    @Override
    public String getRequiredProperty(String key) throws IllegalStateException {
        String value = getProperty(key);
        if (GrailsStringUtils.isBlank(value)) {
            throw new IllegalStateException("Value for key [$key] cannot be resolved");
        }
        return value;
    }

    @Override
    public <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
        T value = getProperty(key, targetType);
        if (value == null) {
            throw new IllegalStateException("Value for key [$key] cannot be resolved");
        }
        return value;
    }

    @Override
    public String resolvePlaceholders(String text) {
        throw new UnsupportedOperationException("Config cannot be used to resolve placeholders");
    }

    @Override
    public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Config cannot be used to resolve placeholders");
    }

    @Override
    public String getProperty(String key) {
        return getProperty(key, String.class);
    }
}
