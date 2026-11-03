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
package org.grails.orm.hibernate.query

import java.util.stream.Collectors

import groovy.transform.CompileStatic
import groovy.transform.RecordOptions
import org.hibernate.jpa.AvailableHints

/**
 * Immutable value object holding query execution settings (such as max, offset, timeout)
 * and filtered JPA query hints.
 *
 * @since 8.0.1
 */
@SuppressWarnings('Indentation')
@CompileStatic
@RecordOptions(getAt = false, toList = false, toMap = false, size = false)
record QuerySettings(Map<String, Object> querySettings, Map<String, Object> hints) {

    QuerySettings(Map<String, Object> querySettings, Map<String, Object> hints) {
        this.querySettings = querySettings == null ?
                Collections.<String, Object> emptyMap() :
                Collections.unmodifiableMap(new HashMap<String, Object>(querySettings))
        this.hints = filterHints(hints)
    }

    static QuerySettings of(Map<String, Object> settings, Map<String, Object> hints) {
        new QuerySettings(settings, hints)
    }

    static QuerySettings empty() {
        new QuerySettings(Collections.<String, Object> emptyMap(), Collections.<String, Object> emptyMap())
    }

    Map<String, Object> settings() {
        querySettings
    }

    Object getSetting(String key) {
        querySettings.get(key)
    }

    Object getSetting(HibernateQueryArgument argument) {
        querySettings.get(argument.value())
    }

    boolean hasSetting(String key) {
        querySettings.containsKey(key)
    }

    boolean hasSetting(HibernateQueryArgument argument) {
        querySettings.containsKey(argument.value())
    }

    boolean hasHints() {
        !hints.isEmpty()
    }

    private static Map<String, Object> filterHints(Map<String, Object> hints) {
        if (hints == null || hints.isEmpty()) {
            return Collections.<String, Object> emptyMap()
        }
        hints.entrySet().stream()
                .filter({ Map.Entry<String, Object> e -> AvailableHints.definedHints.contains(e.key) })
                .collect(Collectors.toUnmodifiableMap({ Map.Entry<String, Object> e -> e.key }, { Map.Entry<String, Object> e -> e.value }))
    }

}
