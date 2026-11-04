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
package org.grails.orm.hibernate.query;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.jpa.AvailableHints;

/**
 * Immutable value object holding query execution settings (such as max, offset, timeout)
 * and filtered JPA query hints.
 *
 * @since 8.0.1
 */
public record QuerySettings(
        Map<String, Object> querySettings,
        Map<String, Object> hints) {

    public QuerySettings {
        querySettings = querySettings == null ?
                Collections.emptyMap() :
                Collections.unmodifiableMap(new HashMap<>(querySettings));
        hints = filterHints(hints);
    }

    public static QuerySettings of(Map<String, Object> settings, Map<String, Object> hints) {
        return new QuerySettings(settings, hints);
    }

    public static QuerySettings empty() {
        return new QuerySettings(Collections.emptyMap(), Collections.emptyMap());
    }

    public Map<String, Object> settings() {
        return querySettings;
    }

    public Object getSetting(String key) {
        return querySettings.get(key);
    }

    public Object getSetting(HibernateQueryArgument argument) {
        return querySettings.get(argument.value());
    }

    public boolean hasSetting(String key) {
        return querySettings.containsKey(key);
    }

    public boolean hasSetting(HibernateQueryArgument argument) {
        return querySettings.containsKey(argument.value());
    }

    public boolean hasHints() {
        return !hints.isEmpty();
    }

    private static Map<String, Object> filterHints(Map<String, Object> hints) {
        if (hints == null || hints.isEmpty()) {
            return Collections.emptyMap();
        }
        return hints.entrySet().stream()
                .filter(e -> AvailableHints.getDefinedHints().contains(e.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
