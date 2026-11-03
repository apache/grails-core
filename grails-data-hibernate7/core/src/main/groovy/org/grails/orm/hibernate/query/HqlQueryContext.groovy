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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.RecordOptions
import jakarta.annotation.Nullable

import org.grails.datastore.mapping.model.PersistentEntity

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
@SuppressWarnings('Indentation')
@CompileStatic
@RecordOptions(getAt = false, toList = false, toMap = false, size = false)
record HqlQueryContext(
        String hql,
        Class<?> targetClass,
        QueryParameters parameters,
        QuerySettings settings,
        boolean isUpdate,
        boolean isNative) {

    /**
     * Constructs a context from raw map/list parameters and settings.
     *
     * @deprecated since 8.0.1, use the constructor taking {@link QueryParameters} and {@link QuerySettings} instead.
     */
    @Deprecated(since = '8.0.1', forRemoval = true)
    HqlQueryContext(
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
                isNative)
    }

    boolean isUpdate() {
        this.isUpdate
    }

    boolean isNative() {
        this.isNative
    }

    // ─── Factory ─────────────────────────────────────────────────────────────

    /**
     * Resolves the final HQL string, the result target class, and expands any {@link GString} into
     * named parameters. No {@code Session} is required.
     */
    static HqlQueryContext prepare(
            PersistentEntity entity,
            CharSequence queryCharseq,
            Map<String, Object> namedParams,
            Collection<Object> positionalParams,
            Map<String, Object> querySettings,
            Map<String, Object> hints,
            boolean isNative,
            boolean isUpdate) {
        prepare(entity, queryCharseq, namedParams, positionalParams, querySettings, hints, isNative, isUpdate, null)
    }

    static HqlQueryContext prepare(
            PersistentEntity entity,
            CharSequence queryCharseq,
            Map<String, Object> namedParams,
            Collection<Object> positionalParams,
            Map<String, Object> querySettings,
            Map<String, Object> hints,
            boolean isNative,
            boolean isUpdate,
            Class<?> targetClassOverride) {

        QueryParameters.Expanded expanded = QueryParameters.expand(queryCharseq, namedParams, positionalParams, isNative)
        String resolved = expanded.query()
        QueryParameters parameters = expanded.parameters()

        // native SQL is not HQL and is never read as such; an HQL query is read once, and its normalized form and
        // its result type come from that reading, except an update, which runs as written
        HqlText reading = isNative ? null : HqlText.of(resolved)
        String hql = Optional.ofNullable(isNative || isUpdate ? resolved : reading.normalized())
                .filter({ String s -> !s.trim().isEmpty() })
                .orElseGet({ "from ${entity.name}".toString() })

        // a native query always returns entities, as Hibernate 5 did by adding the entity to every native query
        Class<?> targetClass = targetClassOverride != null ? targetClassOverride :
                isNative ? entity.javaClass : reading.target(entity.javaClass)

        new HqlQueryContext(hql, targetClass, parameters, QuerySettings.of(querySettings, hints), isUpdate, isNative)
    }

    // ─── Backwards compatibility accessors ───────────────────────────────────

    /**
     * @deprecated since 8.0.1, use {@link #parameters()} and {@link QueryParameters#named()} instead.
     */
    @Deprecated(since = '8.0.1', forRemoval = true)
    Map<String, Object> namedParams() {
        parameters != null ? parameters.named() : Collections.<String, Object> emptyMap()
    }

    /**
     * @deprecated since 8.0.1, use {@link #parameters()} and {@link QueryParameters#positional()} instead.
     */
    @Deprecated(since = '8.0.1', forRemoval = true)
    List<Object> positionalParams() {
        parameters != null ? parameters.positional() : Collections.<Object> emptyList()
    }

    /**
     * @deprecated since 8.0.1, use {@link #settings()} and {@link QuerySettings#settings()} instead.
     */
    @Deprecated(since = '8.0.1', forRemoval = true)
    Map<String, Object> querySettings() {
        settings != null ? settings.settings() : Collections.<String, Object> emptyMap()
    }

    /**
     * @deprecated since 8.0.1, use {@link #settings()} and {@link QuerySettings#hints()} instead.
     */
    @Deprecated(since = '8.0.1', forRemoval = true)
    Map<String, Object> hints() {
        settings != null ? settings.hints() : Collections.<String, Object> emptyMap()
    }

    // ─── HQL resolution delegation ───────────────────────────────────────────

    /**
     * @deprecated since 8.0.1, use {@link QueryParameters#normalizeMultiLineQueryString(String)} instead.
     */
    @Deprecated(since = '8.0.1', forRemoval = true)
    @PackageScope
    static String normalizeMultiLineQueryString(String query) {
        QueryParameters.normalizeMultiLineQueryString(query)
    }

    /**
     * @deprecated since 8.0.1, use {@link QueryParameters#resolveHql(CharSequence, Map)} instead.
     */
    @Deprecated(since = '8.0.1', forRemoval = true)
    @PackageScope
    static @Nullable String resolveHql(CharSequence queryCharseq, Map<String, Object> namedParams) {
        QueryParameters.resolveHql(queryCharseq, namedParams)
    }

    /**
     * @deprecated since 8.0.1, use {@link QueryParameters#resolveHql(CharSequence, boolean, Collection)} instead.
     */
    @Deprecated(since = '8.0.1', forRemoval = true)
    @PackageScope
    static @Nullable String resolveHql(
            CharSequence queryCharseq, boolean isNative, Collection<Object> positionalParams) {
        QueryParameters.resolveHql(queryCharseq, isNative, positionalParams)
    }

    // ─── Projection analysis ─────────────────────────────────────────────────
    // The query is read once, by HqlText; these entry points read a text of their own for the callers that have one.

    @PackageScope
    static Class<?> getTarget(CharSequence hql, Class<?> clazz) {
        HqlText.of(hql).target(clazz)
    }

    @PackageScope
    static int countHqlProjections(CharSequence hql) {
        HqlText.of(hql).projectionCount()
    }

    @PackageScope
    static int getCommas(String sel) {
        SqlText.hql(sel).countTopLevel(',' as char)
    }

    @PackageScope
    static @Nullable String normalizeNonAliasedSelect(String hql) {
        hql == null ? null : HqlText.of(hql).normalized()
    }

    @PackageScope
    static boolean isHasAlias(String s, int cur, int tokenEnd) {
        HqlText.isClauseAlias(s, cur, tokenEnd)
    }

}
