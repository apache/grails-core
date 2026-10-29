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
import java.util.Set;

import jakarta.annotation.Nullable;

/** HQL text and its top-level projection analysis, without changing the text sent to Hibernate. */
public final class HqlText {

    private final String hql;
    private final String text;
    private final String lower;
    private final int selectIndex;
    private final int fromIndex;

    public HqlText(@Nullable CharSequence hql) {
        this(hql, true);
    }

    private HqlText(@Nullable CharSequence hql, boolean analyzeRoot) {
        this.hql = hql == null ? null : hql.toString();
        this.text = this.hql == null ? "" : analyzeRoot ? unwrapRootQuery(this.hql) : this.hql.trim();
        this.lower = text.toLowerCase(Locale.ROOT);
        this.selectIndex = indexOfTopLevel(lower, HibernateQueryArgument.HQL_SELECT.value() + " ", 0);
        this.fromIndex = indexOfTopLevel(lower, " " + HibernateQueryArgument.HQL_FROM.value() + " ",
                Math.max(selectIndex, 0));
    }

    public @Nullable String hql() {
        return hql;
    }

    public Class<?> getTarget(Class<?> entityClass) {
        return switch (countHqlProjections()) {
            case 0 -> entityClass;
            case 1 -> {
                String clause = getSingleProjectionClause();
                if (clause.startsWith("count(") || clause.startsWith("sum(") ||
                        clause.startsWith("avg(") || clause.startsWith("min(") ||
                        clause.startsWith("max(")) {
                    yield null;
                }
                String rootAlias = getRootAlias();
                boolean rootProjection = rootAlias != null ?
                        clause.equals(rootAlias.toLowerCase(Locale.ROOT)) :
                        clause.equals(getEntityName().toLowerCase(Locale.ROOT));
                yield rootProjection ? entityClass : Object.class;
            }
            default -> Object[].class;
        };
    }

    public int countHqlProjections() {
        String clause = getSingleProjectionClause();
        if (clause == null || clause.isEmpty()) return 0;
        return getCommas(clause) == 0 ? 1 : 2;
    }

    public @Nullable String getSingleProjectionClause() {
        if (selectIndex < 0) return null;
        return extractSelectClause(lower, selectIndex, fromIndex);
    }

    static String extractSelectClause(String text, int selectIndex, int fromIndex) {
        String clause = text.substring(selectIndex + HibernateQueryArgument.HQL_SELECT.value().length(),
                fromIndex < 0 ? text.length() : fromIndex).trim();
        if (clause.startsWith(HibernateQueryArgument.HQL_DISTINCT.value() + " ")) {
            clause = clause.substring(HibernateQueryArgument.HQL_DISTINCT.value().length() + 1).trim();
        } else if (clause.startsWith(HibernateQueryArgument.HQL_ALL.value() + " ")) {
            clause = clause.substring(HibernateQueryArgument.HQL_ALL.value().length() + 1).trim();
        }
        return clause;
    }

    static int getCommas(String clause) {
        int depth = 0;
        int commas = 0;
        int index = 0;
        while (index < clause.length()) {
            int skipped = skipIgnored(clause, index);
            if (skipped != index) {
                index = skipped;
                continue;
            }
            char current = clause.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')' && depth > 0) {
                depth--;
            } else if (current == ',' && depth == 0) {
                commas++;
            }
            index++;
        }
        return commas;
    }

    private static String unwrapRootQuery(String hql) {
        String text = hql.trim();
        while (text.startsWith("(")) {
            int depth = 0;
            int closing = -1;
            int index = 0;
            while (index < text.length()) {
                int skipped = skipIgnored(text, index);
                if (skipped != index) {
                    index = skipped;
                    continue;
                }
                char current = text.charAt(index);
                if (current == '(') {
                    depth++;
                } else if (current == ')') {
                    depth--;
                    if (depth == 0) {
                        closing = index;
                        break;
                    }
                }
                index++;
            }
            if (closing < 0) return text;
            int next = skipWhitespaceAndComments(text, closing + 1);
            if (next < text.length() && !isSetOperation(text, next)) return text;
            text = text.substring(1, closing).trim();
        }
        return text;
    }

    private static boolean isSetOperation(String text, int index) {
        for (String keyword : Set.of("union", "intersect", "except")) {
            int end = index + keyword.length();
            if (text.regionMatches(true, index, keyword, 0, keyword.length()) &&
                    (end == text.length() || !Character.isJavaIdentifierPart(text.charAt(end)))) {
                return true;
            }
        }
        return false;
    }

    private static int skipIgnored(String text, int index) {
        char current = text.charAt(index);
        if (current == '\'' || current == '"' || current == '`') {
            return SqlRestriction.skipQuoted(text, index, current);
        }
        return skipComment(text, index);
    }

    private static int skipComment(String text, int index) {
        if (text.startsWith("/*", index)) {
            int end = text.indexOf("*/", index + 2);
            return end < 0 ? text.length() : end + 2;
        }
        return index;
    }

    private static int skipWhitespaceAndComments(String text, int index) {
        while (index < text.length()) {
            if (Character.isWhitespace(text.charAt(index))) {
                index++;
            } else {
                int skipped = skipComment(text, index);
                if (skipped == index) return index;
                index = skipped;
            }
        }
        return index;
    }

    private static int indexOfTopLevel(String text, String keyword, int fromIndex) {
        int depth = 0;
        int index = 0;
        while (index < text.length()) {
            int skipped = skipIgnored(text, index);
            if (skipped != index) {
                index = skipped;
                continue;
            }
            char current = text.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')' && depth > 0) {
                depth--;
            } else if (depth == 0 && index >= fromIndex && text.startsWith(keyword, index) && startsWord(text, index)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static boolean startsWord(String text, int index) {
        if (index == 0 || text.charAt(index) == ' ') return true;
        char previous = text.charAt(index - 1);
        return !Character.isJavaIdentifierPart(previous) && previous != '.';
    }

    public @Nullable String getRootAlias() {
        if (fromIndex < 0) return null;
        int current = entityEnd();
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) current++;
        if (current + 2 < text.length() &&
                text.regionMatches(true, current, HibernateQueryArgument.HQL_AS.value(), 0, 2) &&
                Character.isWhitespace(text.charAt(current + 2))) {
            current += HibernateQueryArgument.HQL_AS.value().length();
            while (current < text.length() && Character.isWhitespace(text.charAt(current))) current++;
        }
        int end = current;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end)) && text.charAt(end) != ',') end++;
        return isHasAlias(text, current, end) ? text.substring(current, end) : null;
    }

    private int entityStart() {
        int start = fromIndex + HibernateQueryArgument.HQL_FROM.value().length() + 2;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        return start;
    }

    private int entityEnd() {
        int end = entityStart();
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        return end;
    }

    private String getEntityName() {
        return fromIndex < 0 ? "" : text.substring(entityStart(), entityEnd());
    }

    static boolean isHasAlias(String text, int current, int end) {
        String token = text.substring(current, end).toLowerCase(Locale.ROOT);
        return !token.isEmpty() && !Set.of(
                HibernateQueryArgument.HQL_WHERE.value(), HibernateQueryArgument.HQL_JOIN.value(),
                HibernateQueryArgument.HQL_LEFT.value(), HibernateQueryArgument.HQL_RIGHT.value(),
                HibernateQueryArgument.HQL_INNER.value(), HibernateQueryArgument.HQL_OUTER.value(),
                HibernateQueryArgument.HQL_GROUP.value(), HibernateQueryArgument.HQL_ORDER.value(),
                HibernateQueryArgument.HQL_HAVING.value()).contains(token);
    }

    static @Nullable String normalizeNonAliasedSelect(@Nullable String hql) {
        if (hql == null) return null;
        return new HqlText(hql, false).normalizeNonAliasedSelect();
    }

    private String normalizeNonAliasedSelect() {
        if (selectIndex < 0 || fromIndex < 0) return text;
        int selectStart = selectIndex + HibernateQueryArgument.HQL_SELECT.value().length() + 1;
        String selectClause = text.substring(selectStart, fromIndex).trim();
        String projection = lower.substring(selectStart, fromIndex).trim();
        String entityName = getEntityName();
        if (entityName.isEmpty() || getRootAlias() != null) return text;

        String prefix = "";
        if (projection.startsWith(HibernateQueryArgument.HQL_DISTINCT.value() + " ")) {
            prefix = HibernateQueryArgument.HQL_DISTINCT.value() + " ";
        } else if (projection.startsWith(HibernateQueryArgument.HQL_ALL.value() + " ")) {
            prefix = HibernateQueryArgument.HQL_ALL.value() + " ";
        }
        selectClause = selectClause.substring(prefix.length()).trim();
        projection = projection.substring(prefix.length()).trim();
        String adjusted = selectClause;
        if (projection.equalsIgnoreCase(entityName)) {
            adjusted = "e";
        } else if (!projection.contains("(") && !projection.contains(".") &&
                !projection.startsWith(HibernateQueryArgument.HQL_NEW.value() + " ")) {
            adjusted = "e." + selectClause;
        }
        return text.substring(0, selectIndex) + HibernateQueryArgument.HQL_SELECT.value() + " " + prefix + adjusted + " " +
                HibernateQueryArgument.HQL_FROM.value() + " " + entityName + " e" + text.substring(entityEnd());
    }
}
