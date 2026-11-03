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

import java.lang.reflect.Array

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.RecordOptions
import jakarta.annotation.Nullable

/**
 * Immutable value object holding resolved query parameters (both named and positional)
 * and providing expansion of {@link GString} queries and value conversion.
 *
 * @since 8.0.1
 */
@SuppressWarnings('Indentation')
@CompileStatic
@RecordOptions(getAt = false, toList = false, toMap = false, size = false)
record QueryParameters(Map<String, Object> namedParams, List<Object> positionalParams) {

    /** The query text after expansion together with the parameters it was expanded into. */
    @SuppressWarnings('Indentation')
    @CompileStatic
    @RecordOptions(getAt = false, toList = false, toMap = false, size = false)
    record Expanded(String query, QueryParameters parameters) {
    }

    QueryParameters(Map<String, Object> namedParams, List<Object> positionalParams) {
        this.namedParams = namedParams == null ?
                Collections.<String, Object> emptyMap() :
                Collections.unmodifiableMap(new HashMap<String, Object>(namedParams))
        this.positionalParams = positionalParams == null ?
                Collections.<Object> emptyList() :
                Collections.unmodifiableList(new ArrayList<Object>(positionalParams))
    }

    static QueryParameters of(Map<String, Object> named, Collection<Object> positional) {
        Map<String, Object> namedCopy = named == null ? new HashMap<String, Object>() : new HashMap<String, Object>(named)
        List<Object> positionalCopy = positional == null ? new ArrayList<Object>() : new ArrayList<Object>(positional)

        namedCopy.replaceAll({ String k, Object v -> convertValue(v) })
        positionalCopy.replaceAll(QueryParameters::convertValue)

        new QueryParameters(namedCopy, positionalCopy)
    }

    static QueryParameters empty() {
        new QueryParameters(Collections.<String, Object> emptyMap(), Collections.<Object> emptyList())
    }

    Map<String, Object> named() {
        namedParams
    }

    List<Object> positional() {
        positionalParams
    }

    boolean hasNamed() {
        !namedParams.isEmpty()
    }

    boolean hasPositional() {
        !positionalParams.isEmpty()
    }

    boolean isEmpty() {
        namedParams.isEmpty() && positionalParams.isEmpty()
    }

    /**
     * Expands any {@link GString} expressions in the query into bound parameters and converts all
     * parameter values.
     */
    static Expanded expand(
            CharSequence queryCharseq,
            Map<String, Object> namedParams,
            Collection<Object> positionalParams,
            boolean isNative) {

        Map<String, Object> namedCopy = namedParams == null ? new HashMap<String, Object>() : new HashMap<String, Object>(namedParams)
        List<Object> positionalCopy = positionalParams == null ? new ArrayList<Object>() : new ArrayList<Object>(positionalParams)

        String resolved = positionalCopy.isEmpty() ?
                resolveHql(queryCharseq, namedCopy) :
                resolveHql(queryCharseq, isNative, positionalCopy)

        namedCopy.replaceAll({ String k, Object v -> convertValue(v) })
        positionalCopy.replaceAll(QueryParameters::convertValue)

        new Expanded(resolved, new QueryParameters(namedCopy, positionalCopy))
    }

    @PackageScope
    static @Nullable String resolveHql(CharSequence queryCharseq, Map<String, Object> namedParams) {
        String raw = queryCharseq instanceof GString ?
                buildNamedParameterQueryFromGString((GString) queryCharseq, namedParams) :
                queryCharseq != null ? queryCharseq.toString() : ''
        normalizeMultiLineQueryString(raw)
    }

    @PackageScope
    static @Nullable String resolveHql(
            CharSequence queryCharseq, boolean isNative, Collection<Object> positionalParams) {
        String raw = queryCharseq instanceof GString ?
                buildPositionalParameterQueryFromGString((GString) queryCharseq, positionalParams, isNative) :
                queryCharseq != null ? queryCharseq.toString() : ''
        normalizeMultiLineQueryString(raw)
    }

    @PackageScope
    static String normalizeMultiLineQueryString(String query) {
        if (query == null || query.indexOf('\n') == -1) {
            return query
        }
        query.trim().replace('\n', ' ')
    }

    private static String buildNamedParameterQueryFromGString(GString query, Map<String, Object> params) {
        StringBuilder sql = new StringBuilder()
        Object[] values = query.values
        String[] strings = query.strings
        for (int i = 0; i < strings.length; i++) {
            sql.append(strings[i])
            if (i < values.length) {
                if (sql.length() > 0 && !Character.isWhitespace(sql.charAt(sql.length() - 1))) {
                    sql.append(' ')
                }
                String name = "p${i}".toString()
                sql.append(':').append(name)
                params.put(name, values[i])
            }
        }
        sql.toString()
    }

    private static String buildPositionalParameterQueryFromGString(
            GString query, Collection<Object> positionalParams, boolean isNative) {
        StringBuilder sql = new StringBuilder()
        Object[] values = query.values
        String[] strings = query.strings
        for (int i = 0; i < strings.length; i++) {
            sql.append(strings[i])
            if (i < values.length) {
                if (sql.length() > 0 && !Character.isWhitespace(sql.charAt(sql.length() - 1))) {
                    sql.append(' ')
                }
                if (isNative) {
                    sql.append('?')
                } else {
                    sql.append('?').append(positionalParams.size() + 1)
                }
                positionalParams.add(values[i])
            }
        }
        sql.toString()
    }

    static Object convertValue(Object value) {
        if (value instanceof CharSequence) {
            return value.toString()
        }
        if (value instanceof Collection<?>) {
            Collection<?> coll = (Collection<?>) value
            List<Object> newList = new ArrayList<>(coll.size())
            for (Object o : coll) {
                newList.add(convertValue(o))
            }
            return newList
        }
        if (value != null && value.class.isArray()) {
            int length = Array.getLength(value)
            Class<?> componentType = value.class.componentType
            Class<?> targetComponent = (componentType == CharSequence || CharSequence.isAssignableFrom(componentType)) ? String : componentType
            Object newArray = Array.newInstance(targetComponent, length)
            for (int i = 0; i < length; i++) {
                Array.set(newArray, i, convertValue(Array.get(value, i)))
            }
            return newArray
        }
        value
    }

}
