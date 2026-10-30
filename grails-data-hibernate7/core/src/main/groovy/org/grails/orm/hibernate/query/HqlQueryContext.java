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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Immutable value object that holds all resolved HQL query state which can be computed without a
 * Hibernate {@code Session}: the final HQL string, the result target class, the {@link QueryParameters},
 * the {@link QuerySettings}, and flags for whether the query is an update or native SQL.
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
        QueryParameters parameters,
        QuerySettings settings,
        boolean isUpdate,
        boolean isNative) {

    public HqlQueryContext(
            String hql,
            Class<?> targetClass,
            Map<String, Object> namedParams,
            List<Object> positionalParams,
            Map<String, Object> querySettings,
            Map<String, Object> hints,
            boolean isUpdate,
            boolean isNative) {
        this(
                hql,
                targetClass,
                QueryParameters.of(namedParams, positionalParams),
                QuerySettings.of(querySettings, hints),
                isUpdate,
                isNative
        );
    }

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

        QueryParameters.Expanded expanded = QueryParameters.expand(
                queryCharseq, namedParams, positionalParams, isNative);
        String resolved = expanded.query();
        QueryParameters parameters = expanded.parameters();

        HqlText reading = isNative ? null : HqlText.of(resolved);
        var hql = Optional.ofNullable(isNative || isUpdate ? resolved : reading.normalized())
                .filter(s -> !s.trim().isEmpty())
                .orElseGet(() -> "from %s".formatted(entity.getName()));

        Class<?> targetClass = targetClassOverride != null ? targetClassOverride :
                isNative ? entity.getJavaClass() : reading.target(entity.getJavaClass());

        QuerySettings settings = QuerySettings.of(querySettings, hints);

        return new HqlQueryContext(
                hql,
                targetClass,
                parameters,
                settings,
                isUpdate,
                isNative);
    }

    // ─── Backwards Compatibility Accessors ────────────────────────────────────

    public Map<String, Object> namedParams() {
        return parameters != null ? parameters.named() : Collections.emptyMap();
    }

    public List<Object> positionalParams() {
        return parameters != null ? parameters.positional() : Collections.emptyList();
    }

    public Map<String, Object> querySettings() {
        return settings != null ? settings.settings() : Collections.emptyMap();
    }

    public Map<String, Object> hints() {
        return settings != null ? settings.hints() : Collections.emptyMap();
    }

    // ─── HQL resolution delegation ───────────────────────────────────────────

    static String normalizeMultiLineQueryString(String query) {
        return QueryParameters.normalizeMultiLineQueryString(query);
    }

    static @Nullable String resolveHql(CharSequence queryCharseq, Map<String, Object> namedParams) {
        return QueryParameters.resolveHql(queryCharseq, namedParams);
    }

    static @Nullable String resolveHql(
            CharSequence queryCharseq, boolean isNative, Collection<Object> positionalParams) {
        return QueryParameters.resolveHql(queryCharseq, isNative, positionalParams);
    }

    // ─── Projection analysis delegation ──────────────────────────────────────

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
}
