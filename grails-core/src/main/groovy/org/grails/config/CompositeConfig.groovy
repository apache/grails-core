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
package org.grails.config

import groovy.transform.CompileStatic
import groovy.transform.stc.POJO

import grails.config.Config
import grails.util.GrailsStringUtils
import org.grails.core.exceptions.GrailsConfigurationException

/**
 * A {@link Config} composed of other Configs
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@POJO
class CompositeConfig implements Config {

    protected Deque<Config> configs = new ArrayDeque<>()

    /**
     * Adds a config at the highest level of precedence
     *
     * @param config
     */
    void addFirst(Config config) {
        configs.addFirst(config)
    }

    /**
     * Adds a config at the lowest level of precedence
     *
     * @param config
     */
    void addLast(Config config) {
        configs.addLast(config)
    }

    @Override
    @Deprecated
    Map<String, Object> flatten() {
        Map<String, Object> flattened = new LinkedHashMap<>()
        for (Config c in configs) {
            flattened.putAll(c.flatten())
        }
        return flattened
    }

    @Override
    Properties toProperties() {
        Properties properties = new Properties()
        for (Config c in configs) {
            properties.putAll(c.toProperties())
        }
        return properties
    }

    @Override
    Config merge(Map<String, Object> toMerge) {
        throw new UnsupportedOperationException('Config cannot be modified')
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType, T defaultValue, List<T> allowedValues) {
        T v = getProperty(key, targetType, defaultValue)
        if (!allowedValues.contains(v)) {
            throw new GrailsConfigurationException('Invalid configuration value [\$value] for key [\${key}]. Possible values \$allowedValues')
        }
        return v
    }

    @Override
    Object getAt(Object key) {
        for (Config c in configs) {
            Object v = c.getAt(key)
            if (v != null) return v
        }
        return null
    }

    @Override
    void setAt(Object key, Object value) {
        throw new UnsupportedOperationException('Config cannot be modified')
    }

    @Override
    Object navigate(String... path) {
        for (Config c in configs) {
            Object v = c.navigate(path)
            if (v != null) return v
        }
        return null
    }

    @Override
    int size() {
        int size = 0
        for (Config config in configs) {
            size += config.size()
        }
        return size
    }

    @Override
    boolean isEmpty() {
        for (Config config in configs) {
            if (!config.isEmpty()) {
                return false
            }
        }
        return true
    }

    @Override
    boolean containsKey(Object key) {
        for (Config config in configs) {
            if (config.containsKey(key)) return true
        }
        return false
    }

    @Override
    boolean containsValue(Object value) {
        for (Config config in configs) {
            if (config.containsValue(value)) return true
        }
        return false
    }

    @Override
    Object get(Object key) {
        for (Config config in configs) {
            Object v = config.get(key)
            if (v != null) return v
        }
        return null
    }

    @Override
    Object put(String key, Object value) {
        throw new UnsupportedOperationException('Config cannot be modified')
    }

    @Override
    Object remove(Object key) {
        throw new UnsupportedOperationException('Config cannot be modified')
    }

    @Override
    void putAll(Map<? extends String, ?> m) {
        throw new UnsupportedOperationException('Config cannot be modified')
    }

    @Override
    void clear() {
        throw new UnsupportedOperationException('Config cannot be modified')
    }

    @Override
    Iterator<Map.Entry<String, Object>> iterator() {
        return entrySet().iterator()
    }

    @Override
    Set<String> keySet() {
        Set<String> entries = new HashSet<>()
        for (Config config in configs) {
            entries.addAll(config.keySet())
        }
        return entries
    }

    @Override
    Collection<Object> values() {
        Collection<Object> values = new ArrayList<>()
        for (Config config in configs) {
            values.addAll(config.values())
        }
        return values
    }

    @Override
    Set<Map.Entry<String, Object>> entrySet() {
        Set<Map.Entry<String, Object>> entries = new HashSet<>()
        for (Config config in configs) {
            entries.addAll(config.entrySet())
        }
        return entries
    }

    @Override
    boolean containsProperty(String key) {
        return containsKey(key)
    }

    @Override
    String getProperty(String key, String defaultValue) {
        String v = getProperty(key, String)
        return !GrailsStringUtils.isBlank(v) ? v : defaultValue
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType) {
        for (Config config in configs) {
            T v = config.getProperty(key, targetType)
            if (v != null) return v
        }
        return null
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        T v = getProperty(key, targetType)
        return v != null ? v : defaultValue
    }

    @Override
    String getRequiredProperty(String key) throws IllegalStateException {
        String value = getProperty(key)
        if (GrailsStringUtils.isBlank(value)) {
            throw new IllegalStateException('Value for key [\$key] cannot be resolved')
        }
        return value
    }

    @Override
    def <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
        T value = getProperty(key, targetType)
        if (value == null) {
            throw new IllegalStateException('Value for key [\$key] cannot be resolved')
        }
        return value
    }

    @Override
    String resolvePlaceholders(String text) {
        throw new UnsupportedOperationException('Config cannot be used to resolve placeholders')
    }

    @Override
    String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
        throw new UnsupportedOperationException('Config cannot be used to resolve placeholders')
    }

    @Override
    String getProperty(String key) {
        return getProperty(key, String)
    }

}
