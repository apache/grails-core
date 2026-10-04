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

import org.springframework.boot.origin.OriginTrackedValue
import org.springframework.core.env.MapPropertySource
import org.springframework.util.StringUtils

/**
 * A {@link org.springframework.core.env.PropertySource} that doesn't return values for navigable submaps
 *
 * <p>A list of objects, or of lists holding objects, is presented element by element, under the
 * indexed names Spring Boot binds a list from, such as {@code app.items[0].name}, rather than as
 * one value. Spring Boot binds a
 * value it finds under the name of the list itself by conversion rather than element by element,
 * so a {@code List} of beans would otherwise be bound as a list of maps. A list of plain values is
 * presented as it is.</p>
 *
 * @deprecated This class behavior is closely tied to {@link org.grails.config.NavigableMap} which will be removed in future release.
 * @author Graeme Rocher
 * @since 3.0.7
 */
@Deprecated
@CompileStatic
class NavigableMapPropertySource extends MapPropertySource {

    final String[] propertyNames
    final String[] navigablePropertyNames

    private final Set<String> objectLists
    private final Map<String, Object> elementProperties

    NavigableMapPropertySource(String name, NavigableMap source) {
        super(name, source)
        Set<String> lists = new LinkedHashSet<>()
        Map<String, Object> elements = new LinkedHashMap<>()
        for (String key : source.keySet()) {
            Object value = unwrap(source.get(key))
            if (isObjectList(value)) {
                lists << key
                flatten(elements, key, value)
            }
        }
        this.objectLists = lists
        this.elementProperties = elements

        Set<String> names = new LinkedHashSet<>()
        for (String key : source.keySet()) {
            if (!(source.get(key) instanceof NavigableMap) && !lists.contains(key)) {
                names << key
            }
        }
        names.addAll(elements.keySet())
        this.propertyNames = StringUtils.toStringArray(names)
        navigablePropertyNames =  StringUtils.toStringArray(source.keySet())
    }

    @Override
    String[] getPropertyNames() {
        return propertyNames
    }

    @Override
    boolean containsProperty(String name) {
        !objectLists.contains(name) && (super.containsProperty(name) || elementProperties.containsKey(name))
    }

    @Override
    Object getProperty(String name) {
        if (objectLists.contains(name)) {
            return null
        }
        def value = super.getProperty(name)
        if (value == null) {
            return elementProperties.get(name)
        }
        if (value instanceof OriginTrackedValue) {
            return ((OriginTrackedValue) value).value
        } else if (value instanceof NavigableMap || value instanceof NavigableMap.NullSafeNavigator) {
            return null
        }
        return value
    }

    Object getNavigableProperty(String name) {
        super.getProperty(name)
    }

    /**
     * A list holding an object anywhere in it, including within a list it holds.
     */
    private static boolean isObjectList(Object value) {
        value instanceof List && ((List) value).any { Object element ->
            Object unwrapped = unwrap(element)
            unwrapped instanceof Map || isObjectList(unwrapped)
        }
    }

    /**
     * Presents a value under the names Spring Boot's YAML loader gives it, an empty object, an
     * empty list or a {@code null} being presented as an empty string, so no element disappears.
     */
    private static void flatten(Map<String, Object> into, String name, Object value) {
        Object unwrapped = unwrap(value)
        if (unwrapped instanceof Map && !((Map) unwrapped).isEmpty()) {
            ((Map<Object, Object>) unwrapped).each { Object key, Object nested ->
                flatten(into, "${name}.${key}".toString(), nested)
            }
        }
        else if (unwrapped instanceof List && !((List) unwrapped).isEmpty()) {
            ((List) unwrapped).eachWithIndex { Object element, int index ->
                flatten(into, "${name}[${index}]".toString(), element)
            }
        }
        else if (unwrapped instanceof Map || unwrapped instanceof List || unwrapped == null) {
            into[name] = ''
        }
        else {
            into[name] = unwrapped
        }
    }

    private static Object unwrap(Object value) {
        value instanceof OriginTrackedValue ? ((OriginTrackedValue) value).value : value
    }
}
