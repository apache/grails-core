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

import groovy.lang.GString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import groovy.lang.GString;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Immutable value object that holds all resolved HQL query state which can be computed without a
 * Hibernate {@code Session}: the final HQL string, the result target class, any named parameters
 * (including those expanded from a {@link GString}), and flags for whether the query is an update
 * or native SQL.
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
    Collection positionalParams,
    Map<String, Object> querySettings,
    boolean isUpdate,
    boolean isNative) {

    // ─── Factory ─────────────────────────────────────────────────────────────

  /**
   * Resolves the final HQL string, the result target class, and expands any {@link GString} into
   * named parameters. No {@code Session} is required.
   */
  @SuppressWarnings("unchecked")
  public static HqlQueryContext prepare(
          PersistentEntity entity
          , CharSequence queryCharseq
          , Map<?, ?> namedParams
          , Collection positionalParams
          , Map querySettings
          , boolean isNative
          , boolean isUpdate) {
    Map<String, Object> _namedParams =
        namedParams != null ? new HashMap<>((Map<String, Object>) namedParams) : new HashMap<>();
    Collection positionalParamsCopy = positionalParams != null ? new ArrayList<>(positionalParams) : null;
    Map<String, Object> querySettingsCopy = querySettings != null ? new HashMap<>(querySettings) : null;

    String hql;
    // Prefer positional resolution only if positional parameters are explicitly provided (not null)
    // and named parameters are empty. This preserves legacy GString->named parameter behavior
    // while allowing opt-in to positional parameters via methods that pass them.
    if (positionalParamsCopy != null && _namedParams.isEmpty()) {
      hql = resolveHql(queryCharseq, isNative, positionalParamsCopy);
    } else {
      hql = resolveHql(queryCharseq, isNative, _namedParams);
    }

    Class<?> target = getTarget(hql, entity.getJavaClass());
    return new HqlQueryContext(hql, target, _namedParams, positionalParamsCopy, querySettingsCopy, isUpdate, isNative);
  }

        String hql;
        // Prefer positional resolution only if positional parameters are explicitly provided (not null)
        // and named parameters are empty. This preserves legacy GString->named parameter behavior
        // while allowing opt-in to positional parameters via methods that pass them.
        if (positionalParamsCopy != null && _namedParams.isEmpty()) {
            hql = resolveHql(queryCharseq, isNative, positionalParamsCopy);
        } else {
            hql = resolveHql(queryCharseq, isNative, _namedParams);
        }

  public static @Nullable String resolveHql(
      CharSequence queryCharseq, boolean isNative, Map<String, Object> namedParams) {
    String raw =
        queryCharseq instanceof GString gstr
            ? buildNamedParameterQueryFromGString(gstr, namedParams)
            : queryCharseq != null ? queryCharseq.toString() : "";
    String normalized = normalizeMultiLineQueryString(raw);
    return isNative ? normalized : normalizeNonAliasedSelect(normalized);
  }

  public static @Nullable String resolveHql(
      CharSequence queryCharseq, boolean isNative, Collection positionalParams) {
    String raw =
        queryCharseq instanceof GString gstr
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
      case 1 -> isPropertyProjection(normalized) ? Object.class : clazz;
      default -> Object[].class;
    };
  }

  /**
   * Returns the number of top-level projections in the SELECT clause: 0 if no explicit SELECT, 1
   * for a single projection (including DISTINCT x or NEW map(…)), 2 for two or more comma-separated
   * top-level projections.
   *
   * <p>Commas inside parentheses or string literals are ignored.
   */
  static int countHqlProjections(CharSequence hql) {
    if (hql == null || hql.length() == 0) return 0;
    String s = hql.toString().trim();
    String lower = s.toLowerCase(Locale.ROOT);
    int selectIdx = lower.indexOf("select ");
    if (selectIdx < 0) return 0;

    int fromIdx = lower.indexOf(" from ", selectIdx);
    String sel =
        s.substring(selectIdx + "select".length(), fromIdx < 0 ? s.length() : fromIdx).trim();
    if (sel.isEmpty()) return 0;

    // Strip leading DISTINCT/ALL
    String selLower = sel.toLowerCase(Locale.ROOT);
    if (selLower.startsWith("distinct ")) sel = sel.substring("distinct ".length()).trim();
    else if (selLower.startsWith("all ")) sel = sel.substring("all ".length()).trim();

    // Count top-level commas, ignoring those inside parens or string literals
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
    return commas == 0 ? 1 : 2;
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
    int selectIdx = lower.indexOf("select ");
    if (selectIdx < 0) return s; // no SELECT clause — nothing to normalize

    int fromIdx = lower.indexOf(" from ", selectIdx);
    if (fromIdx < 0) return s; // malformed — leave as-is

    int selectStart = selectIdx + "select ".length();
    String selectClauseOrig = s.substring(selectStart, fromIdx).trim();
    String selectClauseLower = lower.substring(selectStart, fromIdx).trim();

    // Parse entity name from the FROM head
    int afterFrom = fromIdx + " from ".length();
    int entityEnd = afterFrom;
    while (entityEnd < s.length() && !Character.isWhitespace(s.charAt(entityEnd))) entityEnd++;
    String entityName = s.substring(afterFrom, entityEnd);
    if (entityName.isEmpty()) return s;

    // Skip whitespace, then optional "as" keyword
    int cur = entityEnd;
    while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) cur++;
    if (cur + 2 <= s.length() && s.substring(cur, cur + 2).equalsIgnoreCase("as")) {
      cur += 2;
      while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) cur++;
    }

    // ─── HQL resolution ──────────────────────────────────────────────────────

    public static @Nullable String resolveHql(
            CharSequence queryCharseq, boolean isNative, Map<String, Object> namedParams) {
        String raw = queryCharseq instanceof GString gstr ?
                buildNamedParameterQueryFromGString(gstr, namedParams) :
                queryCharseq != null ? queryCharseq.toString() : "";
        String normalized = normalizeMultiLineQueryString(raw);
        return isNative ? normalized : normalizeNonAliasedSelect(normalized);
    }

    public static @Nullable String resolveHql(
            CharSequence queryCharseq, boolean isNative, Collection positionalParams) {
        String raw = queryCharseq instanceof GString gstr ?
                buildPositionalParameterQueryFromGString(gstr, positionalParams, isNative) :
                queryCharseq != null ? queryCharseq.toString() : "";
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
            case 1 -> isPropertyProjection(normalized) ? Object.class : clazz;
            default -> Object[].class;
        };
    }

    /**
     * Returns the number of top-level projections in the SELECT clause: 0 if no explicit SELECT, 1
     * for a single projection (including DISTINCT x or NEW map(…)), 2 for two or more comma-separated
     * top-level projections.
     *
     * <p>Commas inside parentheses or string literals are ignored.
     */
    static int countHqlProjections(CharSequence hql) {
        if (hql == null || hql.length() == 0) return 0;
        String s = hql.toString().trim();
        String lower = s.toLowerCase(Locale.ROOT);
        int selectIdx = lower.indexOf("select ");
        if (selectIdx < 0) return 0;

        int fromIdx = lower.indexOf(" from ", selectIdx);
        String sel = s.substring(selectIdx + "select".length(), fromIdx < 0 ? s.length() : fromIdx)
                .trim();
        if (sel.isEmpty()) return 0;

        // Strip leading DISTINCT/ALL
        String selLower = sel.toLowerCase(Locale.ROOT);
        if (selLower.startsWith("distinct "))
            sel = sel.substring("distinct ".length()).trim();
        else if (selLower.startsWith("all "))
            sel = sel.substring("all ".length()).trim();

        // Count top-level commas, ignoring those inside parens or string literals
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
        return commas == 0 ? 1 : 2;
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
        int selectIdx = lower.indexOf("select ");
        if (selectIdx < 0) return s; // no SELECT clause — nothing to normalize

        int fromIdx = lower.indexOf(" from ", selectIdx);
        if (fromIdx < 0) return s; // malformed — leave as-is

        int selectStart = selectIdx + "select ".length();
        String selectClauseOrig = s.substring(selectStart, fromIdx).trim();
        String selectClauseLower = lower.substring(selectStart, fromIdx).trim();

        // Parse entity name from the FROM head
        int afterFrom = fromIdx + " from ".length();
        int entityEnd = afterFrom;
        while (entityEnd < s.length() && !Character.isWhitespace(s.charAt(entityEnd))) entityEnd++;
        String entityName = s.substring(afterFrom, entityEnd);
        if (entityName.isEmpty()) return s;

        // Skip whitespace, then optional "as" keyword
        int cur = entityEnd;
        while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) cur++;
        if (cur + 2 <= s.length() && s.substring(cur, cur + 2).equalsIgnoreCase("as")) {
            cur += 2;
            while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) cur++;
        }

        // Read the next token; a clause keyword means no user-defined alias is present
        int tokenEnd = cur;
        while (tokenEnd < s.length() && !Character.isWhitespace(s.charAt(tokenEnd))) tokenEnd++;
        String token = s.substring(cur, tokenEnd).toLowerCase(Locale.ROOT);
        boolean hasAlias = !token.isEmpty() &&
                !Set.of("where", "join", "left", "right", "inner", "outer", "group", "order", "having")
                        .contains(token);
        if (hasAlias) return s;

        // Strip DISTINCT/ALL prefix before adjusting the projection
        String prefix = "", projOrig = selectClauseOrig, projLower = selectClauseLower;
        if (projLower.startsWith("distinct ")) {
            prefix = "distinct ";
            projOrig = selectClauseOrig.substring("distinct ".length()).trim();
            projLower = projLower.substring("distinct ".length()).trim();
        } else if (projLower.startsWith("all ")) {
            prefix = "all ";
            projOrig = selectClauseOrig.substring("all ".length()).trim();
            projLower = projLower.substring("all ".length()).trim();
        }

        // Qualify the projection with the synthetic alias
        String adjusted;
        if (projLower.equalsIgnoreCase(entityName)) {
            adjusted = "e"; // "select Person from Person" → "select e"
        } else if (!projLower.contains("(") && !projLower.contains(".") && !projLower.startsWith("new ")) {
            adjusted = "e." + projOrig; // "select name from Person"   → "select e.name"
        } else {
            adjusted = projOrig; // functions / constructor expr / already qualified
        }

        return "select " + prefix + adjusted + " from " + entityName + " e" + s.substring(entityEnd);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private static boolean isPropertyProjection(CharSequence hql) {
        if (hql == null) return false;
        String s = hql.toString().toLowerCase().trim();
        int selectIdx = s.indexOf("select ");
        if (selectIdx < 0) return false;
        int fromIdx = s.indexOf(" from ", selectIdx);
        String clause = s.substring(selectIdx + "select ".length(), fromIdx < 0 ? s.length() : fromIdx)
                .trim();
        if (clause.startsWith("distinct "))
            clause = clause.substring("distinct ".length()).trim();
        else if (clause.startsWith("all "))
            clause = clause.substring("all ".length()).trim();
        return clause.contains(".");
    }

    private static String normalizeMultiLineQueryString(String query) {
        if (query == null || query.indexOf('\n') == -1) return query;
        return query.trim().replace("\n", " ");
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private static String buildPositionalParameterQueryFromGString(
            GString query, Collection positionalParams, boolean isNative) {
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
                positionalParams.add(values[i]);
            }
        }
        return sql.toString();
    }
    return sql.toString();
  }

  @SuppressWarnings("unchecked")
  private static String buildPositionalParameterQueryFromGString(
      GString query, Collection positionalParams, boolean isNative) {
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
        positionalParams.add(values[i]);
      }
    }
    return sql.toString();
  }
}
