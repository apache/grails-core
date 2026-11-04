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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import groovy.lang.GString;

import jakarta.annotation.Nullable;

/**
 * Immutable value object holding resolved query parameters (both named and positional)
 * and providing expansion of {@link GString} queries and value conversion.
 *
 * @since 8.0.1
 */
public record QueryParameters(
        Map<String, Object> namedParams,
        List<Object> positionalParams) {

    public record Expanded(String query, QueryParameters parameters) {}

    public QueryParameters {
        namedParams = namedParams == null ?
                Collections.emptyMap() :
                Collections.unmodifiableMap(new HashMap<>(namedParams));
        positionalParams = positionalParams == null ?
                Collections.emptyList() :
                Collections.unmodifiableList(new ArrayList<>(positionalParams));
    }

    public static QueryParameters of(Map<String, Object> named, Collection<Object> positional) {
        var namedCopy = Optional.ofNullable(named)
                .map(HashMap::new)
                .orElseGet(HashMap::new);
        var positionalCopy = Optional.ofNullable(positional)
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);

        namedCopy.replaceAll((k, v) -> convertValue(v));
        positionalCopy.replaceAll(QueryParameters::convertValue);

        return new QueryParameters(namedCopy, positionalCopy);
    }

    public static QueryParameters empty() {
        return new QueryParameters(Collections.emptyMap(), Collections.emptyList());
    }

    public Map<String, Object> named() {
        return namedParams;
    }

    public List<Object> positional() {
        return positionalParams;
    }

    public boolean hasNamed() {
        return !namedParams.isEmpty();
    }

    public boolean hasPositional() {
        return !positionalParams.isEmpty();
    }

    public boolean isEmpty() {
        return namedParams.isEmpty() && positionalParams.isEmpty();
    }

    /**
     * Expands any {@link GString} expressions in the query into bound parameters and converts all
     * parameter values.
     */
    public static Expanded expand(
            CharSequence queryCharseq,
            Map<String, Object> namedParams,
            Collection<Object> positionalParams,
            boolean isNative) {

        var namedCopy = Optional.ofNullable(namedParams)
                .map(HashMap::new)
                .orElseGet(HashMap::new);

        var positionalCopy = Optional.ofNullable(positionalParams)
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);

        String resolved = positionalCopy.isEmpty() ?
                resolveHql(queryCharseq, namedCopy) :
                resolveHql(queryCharseq, isNative, positionalCopy);

        namedCopy.replaceAll((k, v) -> convertValue(v));
        positionalCopy.replaceAll(QueryParameters::convertValue);

        return new Expanded(resolved, new QueryParameters(namedCopy, positionalCopy));
    }

    static @Nullable String resolveHql(CharSequence queryCharseq, Map<String, Object> namedParams) {
        String raw = queryCharseq instanceof GString gstr ?
                buildNamedParameterQueryFromGString(gstr, namedParams) :
                queryCharseq != null ? queryCharseq.toString() : "";
        return normalizeMultiLineQueryString(raw);
    }

    static @Nullable String resolveHql(
            CharSequence queryCharseq, boolean isNative, Collection<Object> positionalParams) {
        String raw = queryCharseq instanceof GString gstr ?
                buildPositionalParameterQueryFromGString(gstr, positionalParams, isNative) :
                queryCharseq != null ? queryCharseq.toString() : "";
        return normalizeMultiLineQueryString(raw);
    }

    static String normalizeMultiLineQueryString(String query) {
        if (query == null || query.indexOf('\n') == -1) {
            return query;
        }
        return query.trim().replace("\n", " ");
    }

    private static String buildNamedParameterQueryFromGString(GString query, Map<String, Object> params) {
        StringBuilder sql = new StringBuilder();
        Object[] values = query.getValues();
        String[] strings = query.getStrings();
        for (int i = 0; i < strings.length; i++) {
            sql.append(strings[i]);
            if (i < values.length) {
                if (!sql.isEmpty() && !Character.isWhitespace(sql.charAt(sql.length() - 1))) {
                    sql.append(' ');
                }
                String name = "p" + i;
                sql.append(':').append(name);
                params.put(name, values[i]);
            }
        }
        return sql.toString();
    }

    private static String buildPositionalParameterQueryFromGString(
            GString query, Collection<Object> positionalParams, boolean isNative) {
        StringBuilder sql = new StringBuilder();
        Object[] values = query.getValues();
        String[] strings = query.getStrings();
        for (int i = 0; i < strings.length; i++) {
            sql.append(strings[i]);
            if (i < values.length) {
                if (!sql.isEmpty() && !Character.isWhitespace(sql.charAt(sql.length() - 1))) {
                    sql.append(' ');
                }
                if (isNative) {
                    sql.append('?');
                } else {
                    sql.append('?').append(positionalParams.size() + 1);
                }
                Object value = values[i];
                positionalParams.add(value);
            }
        }
        return sql.toString();
    }

    public static Object convertValue(Object value) {
        if (value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof Collection<?> coll) {
            List<Object> newList = new ArrayList<>(coll.size());
            for (Object o : coll) {
                newList.add(convertValue(o));
            }
            return newList;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            Class<?> componentType = value.getClass().getComponentType();
            Class<?> targetComponent = (componentType == CharSequence.class || CharSequence.class.isAssignableFrom(componentType)) ?
                    String.class : componentType;
            Object newArray = Array.newInstance(targetComponent, length);
            for (int i = 0; i < length; i++) {
                Array.set(newArray, i, convertValue(Array.get(value, i)));
            }
            return newArray;
        }
        return value;
    }
}
