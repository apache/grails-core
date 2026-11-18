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

import java.util.Locale;

import jakarta.annotation.Nullable;

/**
 * One reading of one HQL string: the positions of the {@code select} and {@code from} of the query itself, its
 * select clause, how many projections it has, the entity it queries and that entity's alias. The text is lowercased
 * once and scanned once, by {@link SqlText}; everything here is derived from those positions, including the
 * normalized form that gives an unaliased query the alias {@code e} and the result type the normalized query has.
 * Nothing is read twice.
 *
 * @since 8.0.1
 */
final class HqlText {

    private static final String SELECT = HqlKeyword.SELECT.text();
    private static final String FROM = HqlKeyword.FROM.text();
    private static final String DISTINCT = HqlKeyword.DISTINCT.text();
    private static final String ALL = HqlKeyword.ALL.text();
    private static final String AS = HqlKeyword.AS.text();
    private static final String NEW = HqlKeyword.NEW.text();
    private static final String SYNTHETIC_ALIAS = "e";

    private final String text;
    private final int selectIndex;
    private final int fromIndex;
    private final int projectionCount;
    /** The select clause in lower case without a leading {@code distinct} or {@code all}, or {@code null} without a select. */
    private final @Nullable String selectClauseLower;
    private final @Nullable String rootAlias;
    private final String normalized;
    /** The select clause the normalized query has, in lower case, and the alias of its root. */
    private final @Nullable String normalizedClause;
    private final @Nullable String normalizedRootAlias;

    private HqlText(String hql) {
        text = hql.trim();
        String lower = lowerCaseKeepingLength(text);
        SqlText scan = SqlText.hql(lower);
        selectIndex = scan.indexOfTopLevel(SELECT + " ", 0);
        fromIndex = scan.indexOfTopLevel(" " + FROM + " ", Math.max(selectIndex, 0));

        // the projections: none without a select or with an empty clause, one, or two or more
        String prefix = "";
        String clauseOriginal = null;
        String clauseLower = null;
        int count = 0;
        if (selectIndex >= 0) {
            int selectStart = selectIndex + SELECT.length();
            int selectEnd = fromIndex < 0 ? text.length() : fromIndex;
            clauseOriginal = text.substring(selectStart, selectEnd).trim();
            clauseLower = lower.substring(selectStart, selectEnd).trim();
            if (!clauseOriginal.isEmpty()) {
                count = scan.countTopLevel(',', selectStart, selectEnd) == 0 ? 1 : 2;
            }
            if (clauseLower.startsWith(DISTINCT + " ")) {
                prefix = DISTINCT + " ";
            } else if (clauseLower.startsWith(ALL + " ")) {
                prefix = ALL + " ";
            }
            clauseOriginal = clauseOriginal.substring(prefix.length()).trim();
            clauseLower = clauseLower.substring(prefix.length()).trim();
        }
        projectionCount = count;
        selectClauseLower = clauseLower;
        rootAlias = readRootAlias(lower);

        // the normalized form: an unaliased root gets the alias e, and the projection is qualified with it
        String rewritten = text;
        String rewrittenClause = clauseLower;
        String rewrittenRootAlias = rootAlias;
        if (selectIndex >= 0 && fromIndex >= 0) {
            int afterFrom = fromIndex + FROM.length() + 2;
            int entityEnd = afterFrom;
            while (entityEnd < text.length() && !Character.isWhitespace(text.charAt(entityEnd))) entityEnd++;
            String entityName = text.substring(afterFrom, entityEnd);
            if (!entityName.isEmpty() && !hasUserAlias(entityEnd)) {
                String adjusted = qualify(clauseLower, entityName, clauseOriginal);
                rewritten = text.substring(0, selectIndex) + SELECT + " " + prefix + adjusted + " " +
                        FROM + " " + entityName + " " + SYNTHETIC_ALIAS + text.substring(entityEnd);
                rewrittenClause = lowerCaseKeepingLength(adjusted);
                rewrittenRootAlias = SYNTHETIC_ALIAS;
            }
        }
        normalized = rewritten;
        normalizedClause = rewrittenClause;
        normalizedRootAlias = rewrittenRootAlias;
    }

    /**
     * Lowercases the text character by character, so that every index of the lowercase text is the same index of
     * the original: {@code String.toLowerCase} can lengthen a text, as it does for the dotted capital I of a
     * literal, and the reader indexes the original with positions found in the lowercase text.
     */
    private static String lowerCaseKeepingLength(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Character.toLowerCase(chars[i]);
        }
        return new String(chars);
    }

    /** Reads the HQL once; {@code null} reads as an empty query. */
    static HqlText of(@Nullable CharSequence hql) {
        return new HqlText(hql == null ? "" : hql.toString());
    }

    /** The HQL as given, trimmed. */
    String text() {
        return text;
    }

    /**
     * The number of top-level projections of the select clause: 0 without an explicit select or with an empty one,
     * 1 for a single projection (a {@code distinct x} or a {@code new map(...)} included), 2 for two or more.
     * Commas inside parentheses, string literals, quoted identifiers and comments do not separate projections.
     */
    int projectionCount() {
        return projectionCount;
    }

    /** The select clause in lower case, without a leading {@code distinct} or {@code all}; {@code null} without a select. */
    @Nullable String singleProjectionClause() {
        return selectClauseLower;
    }

    /** The alias of the entity named first after {@code from}, or {@code null} when it has none. */
    @Nullable String rootAlias() {
        return rootAlias;
    }

    /**
     * The query with the synthetic alias {@code e} given to an unaliased root, so that projection detection works
     * uniformly: {@code select name from Person} becomes {@code select e.name from Person e}, and
     * {@code select Person from Person} becomes {@code select e from Person e}. Only the select and from of the
     * query itself are read, not those of a subquery, and what precedes the select, such as a comment or the
     * {@code with} clause of a common table expression, is kept. A query that already aliases its root, or has no
     * select or from, is returned as it is.
     */
    String normalized() {
        return normalized;
    }

    /**
     * The result type of the normalized query: the entity for a query without a select or one that selects the
     * root alias, {@code null} for a single aggregate so that Hibernate decides, {@code Object} for one other
     * projection and {@code Object[]} for several.
     */
    @Nullable Class<?> target(Class<?> entityClass) {
        return switch (projectionCount) {
            case 0 -> entityClass;
            case 1 -> {
                String clause = normalizedClause;
                if (clause != null && (clause.startsWith("count(") || clause.startsWith("sum(") ||
                        clause.startsWith("avg(") || clause.startsWith("min(") || clause.startsWith("max("))) {
                    yield null;
                }
                boolean root = clause == null ||
                        (normalizedRootAlias != null && clause.equals(normalizedRootAlias.toLowerCase(Locale.ROOT)));
                yield root ? entityClass : Object.class;
            }
            default -> Object[].class;
        };
    }

    /**
     * Whether the token between {@code from} and {@code to} is an alias and not the keyword of the clause that
     * follows the entity.
     */
    static boolean isClauseAlias(String hql, int from, int to) {
        String token = hql.substring(from, to);
        return !token.isEmpty() && !HqlKeyword.startsClause(token);
    }

    /** Whether the root entity ending at {@code entityEnd} is followed by an alias of the user's, with or without {@code as}. */
    private boolean hasUserAlias(int entityEnd) {
        int cur = entityEnd;
        while (cur < text.length() && Character.isWhitespace(text.charAt(cur))) cur++;
        if (cur + AS.length() <= text.length() && text.substring(cur, cur + AS.length()).equalsIgnoreCase(AS)) {
            cur += AS.length();
            while (cur < text.length() && Character.isWhitespace(text.charAt(cur))) cur++;
        }
        int tokenEnd = cur;
        while (tokenEnd < text.length() && !Character.isWhitespace(text.charAt(tokenEnd))) tokenEnd++;
        return isClauseAlias(text, cur, tokenEnd);
    }

    /** The alias of the first entity after {@code from}, read the way GORM reads it for a root alias. */
    private @Nullable String readRootAlias(String lower) {
        int cur;
        if (fromIndex >= 0) {
            cur = fromIndex + FROM.length() + 2;
        } else if (lower.startsWith(FROM + " ")) {
            // a query that starts with from has no space before it for the top-level search to find
            cur = FROM.length() + 1;
        } else {
            return null;
        }
        while (cur < text.length() && Character.isWhitespace(text.charAt(cur))) cur++;
        while (cur < text.length() && !Character.isWhitespace(text.charAt(cur))) cur++;
        while (cur < text.length() && Character.isWhitespace(text.charAt(cur))) cur++;
        if (cur + AS.length() < text.length() &&
                text.substring(cur, cur + AS.length()).equalsIgnoreCase(AS) &&
                Character.isWhitespace(text.charAt(cur + AS.length()))) {
            cur += AS.length();
            while (cur < text.length() && Character.isWhitespace(text.charAt(cur))) cur++;
        }
        int tokenEnd = cur;
        while (tokenEnd < text.length() && !Character.isWhitespace(text.charAt(tokenEnd)) && text.charAt(tokenEnd) != ',') tokenEnd++;
        return isClauseAlias(text, cur, tokenEnd) ? text.substring(cur, tokenEnd) : null;
    }

    /** Qualifies a projection with the synthetic alias: the entity itself becomes {@code e}, a bare property {@code e.property}. */
    private static String qualify(String clauseLower, String entityName, String clauseOriginal) {
        if (clauseLower.equalsIgnoreCase(entityName)) {
            return SYNTHETIC_ALIAS;
        }
        if (!clauseLower.contains("(") && !clauseLower.contains(".") && !clauseLower.startsWith(NEW + " ")) {
            return SYNTHETIC_ALIAS + "." + clauseOriginal;
        }
        return clauseOriginal;
    }
}
