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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import groovy.lang.GString;

import jakarta.annotation.Nullable;

import org.hibernate.jpa.AvailableHints;

import org.grails.datastore.mapping.model.PersistentEntity;

import static org.grails.orm.hibernate.query.HqlQueryMethods.convertValue;

/**
 * Immutable value object that holds all resolved HQL query state which can be computed without a
 * Hibernate {@code Session}: the final HQL string, the result target class, any named parameters
 * (including those expanded from a {@link GString}), and flags for whether the query is an update
 * or native SQL.
 *
 * <p><strong>Security Note:</strong> The {@code hql} string must be trust-verified or
 * properly parameterized (e.g. via {@link GString} expansion in {@link #prepare}) before
 * being passed to execution engines to prevent injection vulnerabilities.
 *
 * <p>Use {@link #prepare} to build an instance from raw inputs.
 */
@SuppressWarnings({
    "PMD.AvoidDuplicateLiterals",
    "PMD.DataflowAnomalyAnalysis",
    "PMD.AvoidLiteralsInIfCondition",
    "PMD.UseLocaleWithCaseConversions"
})
public record HqlQueryContext(
        String hql,
        Class<?> targetClass,
        Map<String, Object> namedParams,
        List<Object> positionalParams,
        Map<String, Object> querySettings,
        Map<String, Object> hints,
        boolean isUpdate,
        boolean isNative) {

    // ─── Factory ─────────────────────────────────────────────────────────────

    /**
     * Resolves the final HQL string, the result target class, and expands any {@link GString} into
     * named parameters. No {@code Session} is required.
     */
    public static HqlQueryContext prepare(
        PersistentEntity entity,
        CharSequence queryCharseq,
        Map<String, Object> namedParams,
        Collection<Object> positionalParams,
        Map<String, Object> querySettings,
        Map<String, Object> hints,
        boolean isNative,
        boolean isUpdate) {
        return prepare(entity, queryCharseq, namedParams, positionalParams, querySettings, hints, isNative, isUpdate, null);
    }

    public static HqlQueryContext prepare(
        PersistentEntity entity,
        CharSequence queryCharseq,
        Map<String, Object> namedParams,
        Collection<Object> positionalParams,
        Map<String, Object> querySettings,
        Map<String, Object> hints,
        boolean isNative,
        boolean isUpdate,
        Class<?> targetClassOverride) {

        var namedParamsCopy = Optional.ofNullable(namedParams)
            .map(HashMap::new)
            .orElseGet(HashMap::new);

        var positionalParamsCopy = Optional.ofNullable(positionalParams)
            .map(ArrayList::new)
            .orElseGet(ArrayList::new);

        var querySettingsCopy = Optional.ofNullable(querySettings)
            .map(HashMap::new)
            .orElseGet(HashMap::new);

        var filteredHints = Optional.ofNullable(hints)
            .orElseGet(Collections::emptyMap)
            .entrySet().stream()
            .filter(e -> AvailableHints.getDefinedHints().contains(e.getKey()))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        String resolved = positionalParamsCopy.isEmpty() ?
                resolveHql(queryCharseq, namedParamsCopy) :
                resolveHql(queryCharseq, isNative, positionalParamsCopy);
        // native SQL is not HQL and is never read as such; an HQL query is read once, and its normalized form and
        // its result type come from that reading, except an update, which runs as written
        HqlText reading = isNative ? null : HqlText.of(resolved);
        var hql = Optional.ofNullable(isNative || isUpdate ? resolved : reading.normalized())
            .filter(s -> !s.trim().isEmpty())
            .orElseGet(() -> "from %s".formatted(entity.getName()));

        namedParamsCopy.replaceAll((k, v) -> convertValue(v));
        positionalParamsCopy.replaceAll(HqlQueryMethods::convertValue);

        // a native query always returns entities, as Hibernate 5 did by adding the entity to every native query
        Class<?> targetClass = targetClassOverride != null ? targetClassOverride :
                isNative ? entity.getJavaClass() : reading.target(entity.getJavaClass());

        return new HqlQueryContext(
            hql,
            targetClass,
            namedParamsCopy,
            positionalParamsCopy,
            querySettingsCopy,
            filteredHints,
            isUpdate,
            isNative);
    }

    // ─── HQL resolution ──────────────────────────────────────────────────────

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

    // ─── Projection analysis ─────────────────────────────────────────────────
    // The query is read once, by HqlText; these entry points read a text of their own for the callers that have one.

    static Class<?> getTarget(CharSequence hql, Class<?> clazz) {
        return HqlText.of(hql).target(clazz);
    }

    static int countHqlProjections(CharSequence hql) {
        return HqlText.of(hql).projectionCount();
    }

    static int getCommas(String sel) {
        return SqlText.hql(sel).countTopLevel(',');
    }

    static @Nullable String normalizeNonAliasedSelect(String hql) {
        return hql == null ? null : HqlText.of(hql).normalized();
    }

    static boolean isHasAlias(String s, int cur, int tokenEnd) {
        return HqlText.isClauseAlias(s, cur, tokenEnd);
    }

    private static String normalizeMultiLineQueryString(String query) {
        if (query == null || query.indexOf('\n') == -1) return query;
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
}
