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
import groovy.transform.TupleConstructor
import groovy.transform.RecordOptions
import groovy.transform.PackageScope
import jakarta.annotation.Nonnull
import jakarta.annotation.Nullable
import org.hibernate.jpa.AvailableHints

import org.grails.datastore.mapping.model.PersistentEntity

import static org.grails.orm.hibernate.query.HqlQueryMethods.convertValue

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
@CompileStatic
@SuppressWarnings([
    'PMD.AvoidDuplicateLiterals',
    'PMD.DataflowAnomalyAnalysis',
    'PMD.AvoidLiteralsInIfCondition',
    'PMD.UseLocaleWithCaseConversions',
    'ClassStartsWithBlankLine',
    'Indentation'
])
@RecordOptions(getAt = false, toList = false, toMap = false, size = false)
@TupleConstructor(defaults = false)
record HqlQueryContext(
        String hql,
        Class<?> targetClass,
        Map<String, Object> namedParams,
        List<Object> positionalParams,
        Map<String, Object> querySettings,
        Map<String, Object> hints,
        boolean mutating,
        boolean nativeQuery) {

    // The components are `mutating` and `nativeQuery`, not `isUpdate` and `isNative`: under @CompileStatic a record
    // component named `isX` cannot be called as `isX()` from another class (Groovy 6.0.0), so the Java accessors
    // are kept as explicit methods instead.
    boolean isUpdate() {
        return mutating()
    }

    boolean isNative() {
        return nativeQuery()
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
        return prepare(entity, queryCharseq, namedParams, positionalParams, querySettings, hints, isNative, isUpdate, null)
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

        Map<String, Object> namedParamsCopy = namedParams != null ? new HashMap<>(namedParams) : new HashMap<>()

        List<Object> positionalParamsCopy = positionalParams != null ? new ArrayList<>(positionalParams) : new ArrayList<>()

        Map<String, Object> querySettingsCopy = querySettings != null ? new HashMap<>(querySettings) : new HashMap<>()

        Map<String, Object> hintsSource = hints != null ? hints : Collections.emptyMap()
        Map<String, Object> filteredHints = [:]
        for (Map.Entry<String, Object> e : hintsSource.entrySet()) {
            if (AvailableHints.definedHints.contains(e.key)) {
                filteredHints.put(e.key, e.value)
            }
        }
        filteredHints = Collections.unmodifiableMap(filteredHints)

        String resolved = positionalParamsCopy.isEmpty() ?
                resolveHql(queryCharseq, namedParamsCopy) :
                resolveHql(queryCharseq, isNative, positionalParamsCopy)
        // the result type of a native query or an update is not read from its select, so it is run as written
        String resolvedHql = isNative || isUpdate ? resolved : normalizeNonAliasedSelect(resolved)
        String hql = (resolvedHql != null && !resolvedHql.trim().isEmpty()) ? resolvedHql : "from ${entity.name}".toString()

        for (Map.Entry<String, Object> entry : namedParamsCopy.entrySet()) {
            entry.value = convertValue(entry.value)
        }
        for (int i = 0; i < positionalParamsCopy.size(); i++) {
            positionalParamsCopy.set(i, HqlQueryMethods.convertValue(positionalParamsCopy.get(i)))
        }

        // a native query always returns entities, as Hibernate 5 did by adding the entity to every native query
        Class<?> targetClass = targetClassOverride != null ? targetClassOverride :
                isNative ? entity.javaClass : getTarget(hql, entity.javaClass)

        return new HqlQueryContext(
            hql,
            targetClass,
            namedParamsCopy,
            positionalParamsCopy,
            querySettingsCopy,
            filteredHints,
            isUpdate,
            isNative)
    }

    // ─── HQL resolution ──────────────────────────────────────────────────────

    @Nullable
    @PackageScope static String resolveHql(CharSequence queryCharseq, Map<String, Object> namedParams) {
        String raw
        if (queryCharseq instanceof GString) {
            raw = buildNamedParameterQueryFromGString((GString) queryCharseq, namedParams)
        } else {
            raw = queryCharseq != null ? queryCharseq.toString() : ''
        }
        return normalizeMultiLineQueryString(raw)
    }

    @Nullable
    @PackageScope static String resolveHql(
            CharSequence queryCharseq, boolean isNative, Collection<Object> positionalParams) {
        String raw
        if (queryCharseq instanceof GString) {
            raw = buildPositionalParameterQueryFromGString((GString) queryCharseq, positionalParams, isNative)
        } else {
            raw = queryCharseq != null ? queryCharseq.toString() : ''
        }
        return normalizeMultiLineQueryString(raw)
    }

    // ─── Projection analysis ─────────────────────────────────────────────────

    /**
     * Returns the result target class for a query: the entity class when there is no explicit SELECT
     * or the SELECT is the root alias, {@code Object.class} for any other single projection, such as a
     * property, a joined alias or a function, or {@code Object[].class} for multiple projections.
     */
    @PackageScope static Class<?> getTarget(CharSequence hql, Class<?> clazz) {
        String normalized = normalizeNonAliasedSelect(hql == null ? null : hql.toString())
        int count = countHqlProjections(normalized)
        if (count == 0) {
            return clazz
        } else if (count == 1) {
            String clause = getSingleProjectionClause(normalized)
            if (clause != null) {
                if (clause.startsWith('count(') || clause.startsWith('sum(') ||
                        clause.startsWith('avg(') || clause.startsWith('min(') ||
                        clause.startsWith('max(')) {
                    return null // Let Hibernate determine the result type for aggregates
                }
            }
            return isRootAliasProjection(normalized, clause) ? clazz : Object
        }
        return Object[].class
    }

    @Nullable
    @PackageScope static String getSingleProjectionClause(CharSequence hql) {
        if (hql == null) {
            return null
        }
        String s = hql.toString().toLowerCase(Locale.ROOT).trim()
        int selectIdx = indexOfSelect(s)
        if (selectIdx < 0) {
            return null
        }
        int fromIdx = indexOfFrom(s, selectIdx)
        return extractSelectClause(s, selectIdx, fromIdx)
    }

    @Nonnull
    @PackageScope static String extractSelectClause(String s, int selectIdx, int fromIdx) {
        String clause = s.substring(
                        selectIdx + HibernateQueryArgument.HQL_SELECT.value().length(),
                        fromIdx < 0 ? s.length() : fromIdx)
                .trim()
        if (clause.startsWith(HibernateQueryArgument.HQL_DISTINCT.value() + ' ')) {
            clause = clause.substring(
                            HibernateQueryArgument.HQL_DISTINCT.value().length() + 1)
                    .trim()
        } else if (clause.startsWith(HibernateQueryArgument.HQL_ALL.value() + ' ')) {
            clause = clause.substring(HibernateQueryArgument.HQL_ALL.value().length() + 1)
                    .trim()
        }
        return clause
    }

    /**
     * Returns the number of top-level projections in the SELECT clause: 0 if no explicit SELECT, 1
     * for a single projection (including DISTINCT x or NEW map(…)), 2 for two or more comma-separated
     * top-level projections.
     *
     * <p>Commas inside parentheses or string literals are ignored.
     */
    @PackageScope static int countHqlProjections(CharSequence hql) {
        if (hql == null || hql.length() == 0) {
            return 0
        }
        String s = hql.toString().trim()
        String lower = s.toLowerCase(Locale.ROOT)
        int selectIdx = indexOfSelect(lower)
        if (selectIdx < 0) {
            return 0
        }

        int fromIdx = indexOfFrom(lower, selectIdx)
        String sel = s.substring(
                        selectIdx + HibernateQueryArgument.HQL_SELECT.value().length(),
                        fromIdx < 0 ? s.length() : fromIdx)
                .trim()
        if (sel.isEmpty()) {
            return 0
        }

        // Strip leading DISTINCT/ALL
        String selLower = sel.toLowerCase(Locale.ROOT)
        if (selLower.startsWith(HibernateQueryArgument.HQL_DISTINCT.value() + ' ')) {
            sel = sel.substring(HibernateQueryArgument.HQL_DISTINCT.value().length() + 1)
                    .trim()
        } else if (selLower.startsWith(HibernateQueryArgument.HQL_ALL.value() + ' ')) {
            sel = sel.substring(HibernateQueryArgument.HQL_ALL.value().length() + 1)
                    .trim()
        }

        // Count top-level commas, ignoring those inside parens or string literals
        int commas = getCommas(sel)
        return commas == 0 ? 1 : 2
    }

    @PackageScope static int getCommas(String sel) {
        int depth = 0
        int commas = 0
        boolean inSingle = false
        boolean inDouble = false
        int i = 0
        while (i < sel.length()) {
            char c = sel.charAt(i)
            if (!inDouble && c == ('\'' as char)) {
                if (inSingle && i + 1 < sel.length() && sel.charAt(i + 1) == ('\'' as char)) {
                    // escaped '' — skip next
                    i++
                } else {
                    inSingle = !inSingle
                }
            } else if (!inSingle && c == ('"' as char)) {
                inDouble = !inDouble
            } else if (!inSingle && !inDouble) {
                if (c == ('(' as char)) {
                    depth++
                } else if (c == (')' as char) && depth > 0) {
                    depth--
                } else if (c == (',' as char) && depth == 0) {
                    commas++
                }
            }
            i++
        }
        return commas
    }

    /** Returns the index of the {@code select} of the query itself, not of a subquery, or -1 if it has none. */
    private static int indexOfSelect(String lowerHql) {
        return indexOfTopLevel(lowerHql, HibernateQueryArgument.HQL_SELECT.value() + ' ', 0)
    }

    /**
     * Returns the index of the space before the {@code from} of the query itself, not of a subquery, at or after
     * {@code fromIndex}, or -1 if it has none.
     */
    private static int indexOfFrom(String lowerHql, int fromIndex) {
        return indexOfTopLevel(lowerHql, ' ' + HibernateQueryArgument.HQL_FROM.value() + ' ', fromIndex)
    }

    /**
     * Returns the index of {@code keyword} in the HQL at or after {@code fromIndex}, outside string literals, quoted
     * identifiers, comments and parentheses. Inside parentheses is a subquery or the arguments of a function, and the
     * {@code select} and {@code from} of a subquery are not those of the query. The keyword is matched as written, so
     * the HQL is passed in lower case.
     *
     * @return the index, or -1 if the keyword is not at the top level of the HQL
     */
    private static int indexOfTopLevel(String hql, String keyword, int fromIndex) {
        int depth = 0
        int i = 0
        while (i < hql.length()) {
            char c = hql.charAt(i)
            if (c == ('\'' as char) || c == ('"' as char) || c == ('`' as char)) {
                i = SqlRestriction.skipQuoted(hql, i, c)
                continue
            }
            if (hql.startsWith('/*', i)) {
                int commentEnd = hql.indexOf('*/', i + 2)
                i = commentEnd < 0 ? hql.length() : commentEnd + 2
                continue
            }
            if (c == ('(' as char)) {
                depth++
            } else if (c == (')' as char) && depth > 0) {
                depth--
            } else if (depth == 0 && i >= fromIndex && hql.startsWith(keyword, i) && startsWord(hql, i)) {
                return i
            }
            i++
        }
        return -1
    }

    /**
     * Returns whether a keyword at {@code index} starts a word, so that {@code select} in {@code preselect = 1} or in
     * {@code b.select} is a part of a name and not the keyword. A keyword that starts with a space starts a word.
     */
    private static boolean startsWord(String hql, int index) {
        if (index == 0 || hql.charAt(index) == (' ' as char)) {
            return true
        }
        char previous = hql.charAt(index - 1)
        return !Character.isJavaIdentifierPart(previous) && previous != ('.' as char)
    }

    // ─── HQL normalization ────────────────────────────────────────────────────

    /**
     * Injects a synthetic alias {@code "e"} into unaliased SELECT queries so that projection
     * detection works uniformly. The FROM remainder is left intact. Only the SELECT and FROM of the
     * query itself are read, not those of a subquery, and what precedes the SELECT, such as a comment
     * or the {@code with} clause of a common table expression, is kept.
     *
     * <p>Examples: {@code "select name from Person"} → {@code "select e.name from Person e"}<br>
     * {@code "select Person from Person"} → {@code "select e from Person e"}
     */
    @Nullable
    @PackageScope static String normalizeNonAliasedSelect(String hql) {
        if (hql == null) {
            return null
        }
        String s = hql.trim()
        if (s.isEmpty()) {
            return s
        }

        String lower = s.toLowerCase()
        int selectIdx = indexOfSelect(lower)
        if (selectIdx < 0) {
            return s // no SELECT clause — nothing to normalize
        }

        int fromIdx = indexOfFrom(lower, selectIdx)
        if (fromIdx < 0) {
            return s // malformed — leave as-is
        }

        int selectStart = selectIdx + HibernateQueryArgument.HQL_SELECT.value().length() + 1
        String selectClauseOrig = s.substring(selectStart, fromIdx).trim()
        String selectClauseLower = lower.substring(selectStart, fromIdx).trim()

        // Parse entity name from the FROM head
        int afterFrom = fromIdx + HibernateQueryArgument.HQL_FROM.value().length() + 2
        int entityEnd = afterFrom
        while (entityEnd < s.length() && !Character.isWhitespace(s.charAt(entityEnd))) {
            entityEnd++
        }
        String entityName = s.substring(afterFrom, entityEnd)
        if (entityName.isEmpty()) {
            return s
        }

        // Skip whitespace, then optional "as" keyword
        int cur = entityEnd
        while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) {
            cur++
        }
        if (cur + 2 <= s.length() &&
                s.substring(cur, cur + 2).equalsIgnoreCase(HibernateQueryArgument.HQL_AS.value())) {
            cur += HibernateQueryArgument.HQL_AS.value().length()
            while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) {
                cur++
            }
        }

        // Read the next token; a clause keyword means no user-defined alias is present
        int tokenEnd = cur
        while (tokenEnd < s.length() && !Character.isWhitespace(s.charAt(tokenEnd))) {
            tokenEnd++
        }
        boolean hasAlias = isHasAlias(s, cur, tokenEnd)
        if (hasAlias) {
            return s
        }

        // Strip DISTINCT/ALL prefix before adjusting the projection
        String prefix = ''
        String projOrig = selectClauseOrig
        String projLower = selectClauseLower
        if (projLower.startsWith(HibernateQueryArgument.HQL_DISTINCT.value() + ' ')) {
            prefix = HibernateQueryArgument.HQL_DISTINCT.value() + ' '
            projOrig = selectClauseOrig.substring(prefix.length()).trim()
            projLower = projLower.substring(prefix.length()).trim()
        } else if (projLower.startsWith(HibernateQueryArgument.HQL_ALL.value() + ' ')) {
            prefix = HibernateQueryArgument.HQL_ALL.value() + ' '
            projOrig = selectClauseOrig.substring(prefix.length()).trim()
            projLower = projLower.substring(prefix.length()).trim()
        }

        // Qualify the projection with the synthetic alias
        String adjusted = getAdjusted(projLower, entityName, projOrig)

        return "${s.substring(0, selectIdx)}${HibernateQueryArgument.HQL_SELECT.value()} ${prefix}${adjusted} ${HibernateQueryArgument.HQL_FROM.value()} ${entityName} e${s.substring(entityEnd)}".toString()
    }

    private static String getAdjusted(String projLower, String entityName, String projOrig) {
        String adjusted
        if (projLower.equalsIgnoreCase(entityName)) {
            adjusted = 'e' // "select Person from Person" → "select e"
        } else if (!projLower.contains('(') &&
                !projLower.contains('.') &&
                !projLower.startsWith(HibernateQueryArgument.HQL_NEW.value() + ' ')) {
            adjusted = "e.${projOrig}".toString() // "select name from Person"   → "select e.name"
        } else {
            adjusted = projOrig // functions / constructor expr / already qualified
        }
        return adjusted
    }

    private static boolean isHasAlias(String s, int cur, int tokenEnd) {
        String token = s.substring(cur, tokenEnd).toLowerCase(Locale.ROOT)
        return !token.isEmpty() &&
                !Set.of(
                                HibernateQueryArgument.HQL_WHERE.value(),
                                HibernateQueryArgument.HQL_JOIN.value(),
                                HibernateQueryArgument.HQL_LEFT.value(),
                                HibernateQueryArgument.HQL_RIGHT.value(),
                                HibernateQueryArgument.HQL_INNER.value(),
                                HibernateQueryArgument.HQL_OUTER.value(),
                                HibernateQueryArgument.HQL_GROUP.value(),
                                HibernateQueryArgument.HQL_ORDER.value(),
                                HibernateQueryArgument.HQL_HAVING.value())
                        .contains(token)
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Whether the single projection is the alias of the queried entity. An unaliased query has been given the
     * alias {@code e} by {@link #normalizeNonAliasedSelect(String)}, which also turns a projection of the entity
     * name into {@code e}.
     */
    private static boolean isRootAliasProjection(String normalizedHql, @Nullable String clause) {
        if (clause == null) {
            return true
        }
        String rootAlias = getRootAlias(normalizedHql)
        return rootAlias != null && clause == rootAlias.toLowerCase(Locale.ROOT)
    }

    /** The alias of the entity named first after FROM, or {@code null} if it has none. */
    @Nullable
    @PackageScope static String getRootAlias(@Nullable String hql) {
        if (hql == null) {
            return null
        }
        String s = hql.trim()
        String lower = s.toLowerCase(Locale.ROOT)
        int fromIdx = indexOfFrom(lower, Math.max(indexOfSelect(lower), 0))
        if (fromIdx < 0) {
            return null
        }
        int cur = fromIdx + HibernateQueryArgument.HQL_FROM.value().length() + 2
        while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) {
            cur++
        }
        while (cur < s.length() && !Character.isWhitespace(s.charAt(cur))) {
            cur++
        }
        while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) {
            cur++
        }
        if (cur + 2 < s.length() &&
                s.substring(cur, cur + 2).equalsIgnoreCase(HibernateQueryArgument.HQL_AS.value()) &&
                Character.isWhitespace(s.charAt(cur + 2))) {
            cur += HibernateQueryArgument.HQL_AS.value().length()
            while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) {
                cur++
            }
        }
        int tokenEnd = cur
        while (tokenEnd < s.length() && !Character.isWhitespace(s.charAt(tokenEnd)) && s.charAt(tokenEnd) != (',' as char)) {
            tokenEnd++
        }
        return isHasAlias(s, cur, tokenEnd) ? s.substring(cur, tokenEnd) : null
    }

    private static String normalizeMultiLineQueryString(String query) {
        if (query == null || query.indexOf('\n') == -1) {
            return query
        }
        return query.trim().replace('\n', ' ')
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
        return sql.toString()
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
                Object value = values[i]
                positionalParams.add(value)
            }
        }
        return sql.toString()
    }

}
