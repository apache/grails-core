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
package org.grails.datastore.mapping.config.utils

import groovy.transform.CompileStatic
import org.springframework.core.env.PropertyResolver

/**
 * Adapts a property resolver to the Map<String,String> interface
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
@Deprecated
class PropertyResolverMap implements Map<String, Object>, PropertyResolver {

    @Delegate
    final PropertyResolver propertyResolver

    PropertyResolverMap(PropertyResolver propertyResolver) {
        this.propertyResolver = propertyResolver
    }

    @Override
    int size() {
        return 0
    }

    @Override
    boolean isEmpty() {
        return propertyResolver != null
    }

    @Override
    boolean containsKey(Object key) {
        return propertyResolver.containsProperty(key.toString())
    }

    @Override
    boolean containsValue(Object value) {
        throw new UnsupportedOperationException("Method containsValue not supported")
    }

    @Override
    String get(Object key) {
        return propertyResolver.getProperty(key.toString())
    }

    @Override
    Object put(String key, Object value) {
        throw new UnsupportedOperationException("Map cannot be modified")
    }

    @Override
    String remove(Object key) {
        throw new UnsupportedOperationException("Map cannot be modified")
    }

    @Override
    void putAll(Map<? extends String, ? extends Object> m) {
        throw new UnsupportedOperationException("Map cannot be modified")
    }

    @Override
    void clear() {
        throw new UnsupportedOperationException("Map cannot be modified")
    }

    @Override
    Set<String> keySet() {
        throw new UnsupportedOperationException("Method keySet() not supported")
    }

    @Override
    Collection<Object> values() {
        throw new UnsupportedOperationException("Method values() not supported")
    }

    @Override
    Set<Map.Entry<String, Object>> entrySet() {
        throw new UnsupportedOperationException("Method entrySet() not supported")
    }
}
