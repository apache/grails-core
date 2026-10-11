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

/**
 * A config that accepts a prefix
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@POJO
class PrefixedConfig implements Config {

    protected String prefix
    protected String[] prefixTokens
    protected Config delegate

    PrefixedConfig(String prefix, Config delegate) {
        this.prefix = prefix
        this.prefixTokens = prefix.split('\\.')
        this.delegate = delegate
    }

    @Override
    boolean equals(Object o) {
        if (this.is(o)) return true
        if (o == null || getClass() != o.getClass()) return false

        PrefixedConfig entries = (PrefixedConfig) o

        if (delegate != null ? !delegate.equals(entries.@delegate) : entries.@delegate != null) return false
        if (prefix != null ? !prefix.equals(entries.@prefix) : entries.@prefix != null) return false

        return true
    }

    @Override
    int hashCode() {
        int result = prefix != null ? prefix.hashCode() : 0
        result = 31 * result + (delegate != null ? delegate.hashCode() : 0)
        return result
    }

    @Override
    @Deprecated
    Map<String, Object> flatten() {
        Map<String, Object> flattened = delegate.flatten()
        Map<String, Object> map = new LinkedHashMap<>(flattened.size())
        for (String key in flattened.keySet()) {
            map.put(formulateKey(key), flattened.get(key))
        }
        return map
    }

    @Override
    Properties toProperties() {
        Map<String, Object> flattened = flatten()
        Properties properties = new Properties()
        properties.putAll(flattened)
        return properties
    }

    @Override
    Object getAt(Object key) {
        return get(key)
    }

    @Override
    Object navigate(String... path) {
        List<String> tokens = new ArrayList<>()
        tokens.addAll(Arrays.asList(prefixTokens))
        tokens.addAll(Arrays.asList(path))
        return delegate.navigate(tokens.toArray(new String[tokens.size()]))
    }

    @Override
    Iterator<Map.Entry<String, Object>> iterator() {
        return entrySet().iterator()
    }

    @Override
    int size() {
        return delegate.size()
    }

    @Override
    boolean isEmpty() {
        return delegate.isEmpty()
    }

    @Override
    boolean containsKey(Object key) {
        return containsProperty(key.toString())
    }

    @Override
    boolean containsValue(Object value) {
        return values().contains(value)
    }

    @Override
    Object get(Object key) {
        return getProperty(key.toString(), Object)
    }

    @Override
    Set<String> keySet() {
        Set<String> keys = delegate.keySet()
        Set<String> newKeys = new HashSet<>()
        for (String key in keys) {
            newKeys.add(formulateKey(key))
        }
        return newKeys
    }

    @Override
    Collection<Object> values() {
        return delegate.values()
    }

    @Override
    Set<Map.Entry<String, Object>> entrySet() {
        final Set<Map.Entry<String, Object>> entries = delegate.entrySet()
        Set<Map.Entry<String, Object>> newEntries = new HashSet<>()
        for (final Map.Entry<String, Object> entry in entries) {
            newEntries.add(new Map.Entry<String, Object>() {
                @Override
                String getKey() {
                    return formulateKey(entry.getKey())
                }

                @Override
                Object getValue() {
                    return entry.getValue()
                }

                @Override
                Object setValue(Object value) {
                    return entry.setValue(value)
                }
            })
        }

        return newEntries
    }

    @Override
    boolean containsProperty(String key) {
        return delegate.containsProperty(formulateKey(key))
    }

    @Override
    String getProperty(String key) {
        return delegate.getProperty(formulateKey(key))
    }

    @Override
    String getProperty(String key, String defaultValue) {
        return delegate.getProperty(formulateKey(key), defaultValue)
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType) {
        return delegate.getProperty(formulateKey(key), targetType)
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        return delegate.getProperty(formulateKey(key), targetType, defaultValue)
    }

    @Override
    String getRequiredProperty(String key) throws IllegalStateException {
        return delegate.getRequiredProperty(formulateKey(key))
    }

    @Override
    def <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
        return delegate.getRequiredProperty(formulateKey(key), targetType)
    }

    protected String formulateKey(String key) {
        return prefix + '.' + key
    }

    @Override
    String resolvePlaceholders(String text) {
        throw new UnsupportedOperationException('Resolving placeholders not supported')
    }

    @Override
    String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
        throw new UnsupportedOperationException('Resolving placeholders not supported')
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
    Config merge(Map<String, Object> toMerge) {
        throw new UnsupportedOperationException('Config cannot be modified')
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType, T defaultValue, List<T> allowedValues) {
        return delegate.getProperty(key, targetType, defaultValue, allowedValues)
    }

    @Override
    void setAt(Object key, Object value) {
        throw new UnsupportedOperationException('Config cannot be modified')
    }

}
