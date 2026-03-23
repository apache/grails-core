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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import groovy.lang.GString;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.grails.datastore.mapping.model.PersistentEntity;

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
            boolean isNative,
            boolean isUpdate) {
        Map<String, Object> _namedParams = namedParams != null ?
                new HashMap<>(namedParams) :
                new HashMap<>();
        List<Object> positionalParamsCopy = positionalParams != null ?
                new ArrayList<>(positionalParams) :
                new ArrayList<>();
        Map<String, Object> querySettingsCopy = querySettings != null ?
                new HashMap<>(querySettings) :
                new HashMap<>();

        boolean _isNative = toBool(isNative);
        boolean _isUpdate = toBool(isUpdate);

        String hql;
        // Prefer positional resolution only if positional parameters are explicitly provided (not null)
        // and named parameters are empty. This preserves legacy GString->named parameter behavior
        // while allowing opt-in to positional parameters via methods that pass them.
        if (_namedParams.isEmpty()) {
            hql = resolveHql(queryCharseq, _isNative, positionalParamsCopy);
        } else {
            hql = resolveHql(queryCharseq, _isNative, _namedParams);
        }

        Class<?> target = getTarget(hql, entity.getJavaClass());
        return new HqlQueryContext(
                hql, target, _namedParams, positionalParamsCopy, querySettingsCopy, _isUpdate, _isNative);
    }

    // ─── HQL resolution ──────────────────────────────────────────────────────

    public static @Nullable String resolveHql(
            CharSequence queryCharseq, boolean isNative, Map<String, Object> namedParams) {
        String raw = queryCharseq instanceof GString gstr
                ? buildNamedParameterQueryFromGString(gstr, namedParams)
                : queryCharseq != null ? queryCharseq.toString() : "";
        String normalized = normalizeMultiLineQueryString(raw);
        return isNative ? normalized : normalizeNonAliasedSelect(normalized);
    }

    public static @Nullable String resolveHql(
            CharSequence queryCharseq, boolean isNative, Collection<Object> positionalParams) {
        String raw = queryCharseq instanceof GString gstr
                ? buildPositionalParameterQueryFromGString(gstr, positionalParams, isNative)
                : queryCharseq != null ? queryCharseq.toString() : "";
        String normalized = normalizeMultiLineQueryString(raw);
        return isNative ? normalized : normalizeNonAliasedSelect(normalized);
    }

    // ─── Projection analysis ─────────────────────────────────────────────────

    /**
     * Returns the result target class for a query: the entity class when there is no explicit SELECT
     * or a single entity projection, {@code Object.class} for a single scalar projection, or {@code
     * Object[].class} for multiple projections.
     */
    public static Class<?> getTarget(CharSequence hql, Class<?> clazz) {
        String normalized = normalizeNonAliasedSelect(hql == null ? null : hql.toString());
        return switch (countHqlProjections(normalized)) {
            case 0 -> clazz;
            case 1 ->
                isAggregateProjection(normalized)
                        ? Long.class
                        : (isPropertyProjection(normalized) ? Object.class : clazz);
            default -> Object[].class;
        };
    }

    private static boolean isAggregateProjection(CharSequence hql) {
        String clause = getSingleProjectionClause(hql);
        if (clause == null) return false;

        return clause.startsWith("count(")
                || clause.startsWith("sum(")
                || clause.startsWith("avg(")
                || clause.startsWith("min(")
                || clause.startsWith("max(");
    }

    private static @Nullable String getSingleProjectionClause(CharSequence hql) {
        if (hql == null) return null;
        String s = hql.toString().toLowerCase(Locale.ROOT).trim();
        int selectIdx = s.indexOf(HibernateQueryArgument.HQL_SELECT.value() + " ");
        if (selectIdx < 0) return null;
        int fromIdx = s.indexOf(" " + HibernateQueryArgument.HQL_FROM.value() + " ", selectIdx);
        return extractSelectClause(s, selectIdx, fromIdx);
    }

    private static @NonNull String extractSelectClause(String s, int selectIdx, int fromIdx) {
        String clause = s.substring(
                        selectIdx + HibernateQueryArgument.HQL_SELECT.value().length(),
                        fromIdx < 0 ? s.length() : fromIdx)
                .trim();
        if (clause.startsWith(HibernateQueryArgument.HQL_DISTINCT.value() + " ")) {
            clause = clause.substring(
                            HibernateQueryArgument.HQL_DISTINCT.value().length() + 1)
                    .trim();
        } else if (clause.startsWith(HibernateQueryArgument.HQL_ALL.value() + " ")) {
            clause = clause.substring(HibernateQueryArgument.HQL_ALL.value().length() + 1)
                    .trim();
        }
        return clause;
    }

    /**
     * Returns the number of top-level projections in the SELECT clause: 0 if no explicit SELECT, 1
     * for a single projection (including DISTINCT x or NEW map(…)), 2 for two or more comma-separated
     * top-level projections.
     *
     * <p>Commas inside parentheses or string literals are ignored.
     */
    static int countHqlProjections(CharSequence hql) {
        if (hql == null || hql.isEmpty()) return 0;
        String s = hql.toString().trim();
        String lower = s.toLowerCase(Locale.ROOT);
        int selectIdx = lower.indexOf(HibernateQueryArgument.HQL_SELECT.value() + " ");
        if (selectIdx < 0) return 0;

        int fromIdx = lower.indexOf(" " + HibernateQueryArgument.HQL_FROM.value() + " ", selectIdx);
        String sel = s.substring(
                        selectIdx + HibernateQueryArgument.HQL_SELECT.value().length(),
                        fromIdx < 0 ? s.length() : fromIdx)
                .trim();
        if (sel.isEmpty()) return 0;

        // Strip leading DISTINCT/ALL
        String selLower = sel.toLowerCase(Locale.ROOT);
        if (selLower.startsWith(HibernateQueryArgument.HQL_DISTINCT.value() + " "))
            sel = sel.substring(HibernateQueryArgument.HQL_DISTINCT.value().length() + 1)
                    .trim();
        else if (selLower.startsWith(HibernateQueryArgument.HQL_ALL.value() + " "))
            sel = sel.substring(HibernateQueryArgument.HQL_ALL.value().length() + 1)
                    .trim();

        // Count top-level commas, ignoring those inside parens or string literals
        int commas = getCommas(sel);
        return commas == 0 ? 1 : 2;
    }

    private static int getCommas(String sel) {
        int depth = 0, commas = 0;
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < sel.length(); i++) {
            char c = sel.charAt(i);
            if (!inDouble && c == '\'') {
                if (inSingle && i + 1 < sel.length() && sel.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                } // escaped ''
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') depth++;
                else if (c == ')' && depth > 0) depth--;
                else if (c == ',' && depth == 0) commas++;
            }
        }
        return commas;
    }

    // ─── HQL normalization ────────────────────────────────────────────────────

    /**
     * Injects a synthetic alias {@code "e"} into unaliased SELECT queries so that projection
     * detection works uniformly. The FROM remainder is left intact.
     *
     * <p>Examples: {@code "select name from Person"} → {@code "select e.name from Person e"}<br>
     * {@code "select Person from Person"} → {@code "select e from Person e"}
     */
    static @Nullable String normalizeNonAliasedSelect(String hql) {
        if (hql == null) return null;
        String s = hql.trim();
        if (s.isEmpty()) return s;

        String lower = s.toLowerCase();
        int selectIdx = lower.indexOf(HibernateQueryArgument.HQL_SELECT.value() + " ");
        if (selectIdx < 0) return s; // no SELECT clause — nothing to normalize

        int fromIdx = lower.indexOf(" " + HibernateQueryArgument.HQL_FROM.value() + " ", selectIdx);
        if (fromIdx < 0) return s; // malformed — leave as-is

        int selectStart = selectIdx + HibernateQueryArgument.HQL_SELECT.value().length() + 1;
        String selectClauseOrig = s.substring(selectStart, fromIdx).trim();
        String selectClauseLower = lower.substring(selectStart, fromIdx).trim();

        // Parse entity name from the FROM head
        int afterFrom = fromIdx + HibernateQueryArgument.HQL_FROM.value().length() + 2;
        int entityEnd = afterFrom;
        while (entityEnd < s.length() && !Character.isWhitespace(s.charAt(entityEnd))) entityEnd++;
        String entityName = s.substring(afterFrom, entityEnd);
        if (entityName.isEmpty()) return s;

        // Skip whitespace, then optional "as" keyword
        int cur = entityEnd;
        while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) cur++;
        if (cur + 2 <= s.length()
                && s.substring(cur, cur + 2).equalsIgnoreCase(HibernateQueryArgument.HQL_AS.value())) {
            cur += HibernateQueryArgument.HQL_AS.value().length();
            while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) cur++;
        }

        // Read the next token; a clause keyword means no user-defined alias is present
        int tokenEnd = cur;
        while (tokenEnd < s.length() && !Character.isWhitespace(s.charAt(tokenEnd))) tokenEnd++;
        String token = s.substring(cur, tokenEnd).toLowerCase(Locale.ROOT);
        boolean hasAlias = !token.isEmpty()
                && !Set.of(
                                HibernateQueryArgument.HQL_WHERE.value(),
                                HibernateQueryArgument.HQL_JOIN.value(),
                                HibernateQueryArgument.HQL_LEFT.value(),
                                HibernateQueryArgument.HQL_RIGHT.value(),
                                HibernateQueryArgument.HQL_INNER.value(),
                                HibernateQueryArgument.HQL_OUTER.value(),
                                HibernateQueryArgument.HQL_GROUP.value(),
                                HibernateQueryArgument.HQL_ORDER.value(),
                                HibernateQueryArgument.HQL_HAVING.value())
                        .contains(token);
        if (hasAlias) return s;

        // Strip DISTINCT/ALL prefix before adjusting the projection
        String prefix = "", projOrig = selectClauseOrig, projLower = selectClauseLower;
        if (projLower.startsWith(HibernateQueryArgument.HQL_DISTINCT.value() + " ")) {
            prefix = HibernateQueryArgument.HQL_DISTINCT.value() + " ";
            projOrig = selectClauseOrig
                    .substring(HibernateQueryArgument.HQL_DISTINCT.value().length() + 1)
                    .trim();
            projLower = projLower
                    .substring(HibernateQueryArgument.HQL_DISTINCT.value().length() + 1)
                    .trim();
        } else if (projLower.startsWith(HibernateQueryArgument.HQL_ALL.value() + " ")) {
            prefix = HibernateQueryArgument.HQL_ALL.value() + " ";
            projOrig = selectClauseOrig
                    .substring(HibernateQueryArgument.HQL_ALL.value().length() + 1)
                    .trim();
            projLower = projLower
                    .substring(HibernateQueryArgument.HQL_ALL.value().length() + 1)
                    .trim();
        }

        // Qualify the projection with the synthetic alias
        String adjusted;
        if (projLower.equalsIgnoreCase(entityName)) {
            adjusted = "e"; // "select Person from Person" → "select e"
        } else if (!projLower.contains("(")
                && !projLower.contains(".")
                && !projLower.startsWith(HibernateQueryArgument.HQL_NEW.value() + " ")) {
            adjusted = "e." + projOrig; // "select name from Person"   → "select e.name"
        } else {
            adjusted = projOrig; // functions / constructor expr / already qualified
        }

        return HibernateQueryArgument.HQL_SELECT.value() + " " + prefix + adjusted + " "
                + HibernateQueryArgument.HQL_FROM.value() + " " + entityName + " e" + s.substring(entityEnd);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private static boolean isPropertyProjection(CharSequence hql) {
        String clause = getSingleProjectionClause(hql);
        return clause != null && clause.contains(".");
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

    private static boolean toBool(Object v) {
        return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(v.toString());
    }
}
