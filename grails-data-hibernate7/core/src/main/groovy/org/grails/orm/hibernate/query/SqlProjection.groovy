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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.Type;

import org.grails.datastore.mapping.query.Query;

/**
 * Projection that selects the value of a native SQL expression, created by {@code sqlProjection} or
 * {@code sqlGroupProjection} in a criteria query. In the SQL, {@code {alias}} stands for the table alias of the
 * queried entity.
 *
 * <p>The SQL of a {@code sqlProjection} may select several columns, separated by commas, each one optionally
 * followed by {@code as} and its column alias, as on Hibernate 5. It is split into one projection per column, so
 * every column is a selection of its own.</p>
 *
 * @since 8.0.1
 */
public class SqlProjection extends Query.Projection {

    private static final String QUOTED_IDENTIFIER = "\"(?:[^\"]|\"\")+\"|`(?:[^`]|``)+`|\\[(?:[^\\]]|]])+]";
    private static final Pattern QUOTED = Pattern.compile("(?s)" + QUOTED_IDENTIFIER);
    private static final Pattern TRAILING_ALIAS = Pattern.compile(
            "(?is)^(.*?)\\s+as\\s+(" + QUOTED_IDENTIFIER + "|[\\w$]+)\\s*$");
    private static final Pattern DOLLAR_QUOTE_TAG = Pattern.compile("\\$(?:[A-Za-z_][A-Za-z0-9_]*)?\\$");

    private final String sql;
    private final String columnAlias;
    private final Object declaredType;
    private final Class<?> type;

    /**
     * @param sql the SQL expression of the projected column, without its column alias
     * @param columnAlias the alias of the projected column
     * @param type the type of the projected value: an {@code org.hibernate.type.StandardBasicTypes} constant, an
     *     {@code org.hibernate.type.Type}, a Java class or {@code null} for a value of any type
     * @throws IllegalArgumentException if the SQL is empty or the type is none of these
     */
    public SqlProjection(String sql, String columnAlias, Object type) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("The SQL of a sqlProjection must not be empty");
        }
        this.sql = sql;
        this.columnAlias = columnAlias;
        this.declaredType = type;
        this.type = javaType(type);
    }

    /**
     * Returns the SQL expression of the projected column, without its column alias.
     */
    public String getSql() {
        return sql;
    }

    /**
     * Returns the alias of the projected column, which names it in an {@code order} of the criteria query.
     */
    public String getColumnAlias() {
        return columnAlias;
    }

    /**
     * Returns the Java type of the projected value.
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Returns the type of the projected value as it was given: an {@code org.hibernate.type.StandardBasicTypes}
     * constant or an {@code org.hibernate.type.Type}, which also tell how the value is read, a Java class or
     * {@code null}.
     */
    public Object getDeclaredType() {
        return declaredType;
    }

    /**
     * Splits the SQL of a {@code sqlProjection} or {@code sqlGroupProjection} into one projection per column.
     *
     * @param sql the SQL selecting the columns, separated by commas
     * @param columnAliases the alias of each column, in order
     * @param types the type of each column, in order: an {@code org.hibernate.type.StandardBasicTypes} constant, an
     *     {@code org.hibernate.type.Type} or a Java class
     * @return the projection of each column
     * @throws IllegalArgumentException if the number of columns, aliases and types differ
     */
    public static List<SqlProjection> of(String sql, List<String> columnAliases, List<?> types) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("The SQL of a sqlProjection must not be empty");
        }
        if (columnAliases == null || types == null || columnAliases.size() != types.size()) {
            throw new IllegalArgumentException("A sqlProjection needs as many types as column aliases: " + sql);
        }
        List<String> columns = splitColumns(sql);
        if (columns.size() != columnAliases.size()) {
            throw new IllegalArgumentException("The SQL of a sqlProjection selects " + columns.size() +
                    " columns but " + columnAliases.size() + " column aliases were given: " + sql);
        }
        List<SqlProjection> projections = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String alias = columnAliases.get(i);
            projections.add(new SqlProjection(withoutAlias(columns.get(i), alias), alias, types.get(i)));
        }
        return projections;
    }

    /**
     * Returns the Java type of a projected value given as an {@code org.hibernate.type.StandardBasicTypes} constant,
     * an {@code org.hibernate.type.Type} or a Java class.
     */
    static Class<?> javaType(Object type) {
        if (type == null) {
            return Object.class;
        }
        if (type instanceof Class<?> javaClass) {
            return javaClass;
        }
        if (type instanceof BasicTypeReference<?> reference) {
            return reference.getJavaType();
        }
        if (type instanceof Type hibernateType) {
            return hibernateType.getReturnedClass();
        }
        throw new IllegalArgumentException("The type of a sqlProjection must be a StandardBasicTypes constant, " +
                "an org.hibernate.type.Type or a class, not " + type.getClass().getName());
    }

    /**
     * Splits SQL at the commas that separate its columns: those outside parentheses, square brackets, string
     * literals, dollar-quoted strings, quoted identifiers and comments. Inside square brackets, which quote an
     * identifier or hold an array subscript, parentheses are not counted, and a doubled closing bracket of the
     * outermost ones stands for the bracket itself, so {@code [total (cm)]} and {@code [a]]b]} are identifiers.
     */
    static List<String> splitColumns(String sql) {
        List<String> columns = new ArrayList<>();
        int length = sql.length();
        int depth = 0;
        int brackets = 0;
        int start = 0;
        int i = 0;
        while (i < length) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                i = SqlRestriction.skipQuoted(sql, i, c);
            } else if (c == '$') {
                i = skipDollarQuoted(sql, i);
            } else if (c == '-' && sql.startsWith("--", i)) {
                i = SqlRestriction.endOfLine(sql, i);
            } else if (c == '/' && sql.startsWith("/*", i)) {
                int end = sql.indexOf("*/", i + 2);
                i = end == -1 ? length : end + 2;
            } else {
                if (brackets > 0) {
                    if (c == '[') {
                        brackets++;
                    } else if (c == ']' && brackets == 1 && sql.startsWith("]]", i)) {
                        i++;
                    } else if (c == ']') {
                        brackets--;
                    }
                } else if (c == '[') {
                    brackets++;
                } else if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    columns.add(sql.substring(start, i).trim());
                    start = i + 1;
                }
                i++;
            }
        }
        columns.add(sql.substring(start).trim());
        return columns;
    }

    /**
     * Returns the index after the dollar-quoted string starting at {@code start}, such as {@code $$a,b$$} or
     * {@code $tag$a,b$tag$}, or the index after the {@code $} if none starts there, as in the identifier
     * {@code a$b} or the parameter {@code $1}.
     */
    private static int skipDollarQuoted(String sql, int start) {
        char previous = start > 0 ? sql.charAt(start - 1) : ' ';
        Matcher tag = DOLLAR_QUOTE_TAG.matcher(sql).region(start, sql.length());
        if (Character.isLetterOrDigit(previous) || previous == '_' || previous == '$' || !tag.lookingAt()) {
            return start + 1;
        }
        int end = sql.indexOf(tag.group(), tag.end());
        return end == -1 ? sql.length() : end + tag.group().length();
    }

    /**
     * Returns the SQL without the comments and whitespace it ends with.
     */
    private static String withoutTrailingComments(String sql) {
        int length = sql.length();
        int end = 0;
        int i = 0;
        while (i < length) {
            char c = sql.charAt(i);
            if (c == '-' && sql.startsWith("--", i)) {
                i = SqlRestriction.endOfLine(sql, i);
            } else if (c == '/' && sql.startsWith("/*", i)) {
                int close = sql.indexOf("*/", i + 2);
                i = close == -1 ? length : close + 2;
            } else {
                if (c == '\'' || c == '"' || c == '`') {
                    i = SqlRestriction.skipQuoted(sql, i, c);
                } else if (c == '$') {
                    i = skipDollarQuoted(sql, i);
                } else {
                    i++;
                }
                if (!Character.isWhitespace(c)) {
                    end = i;
                }
            }
        }
        return sql.substring(0, end);
    }

    /**
     * Removes a trailing {@code as alias} naming the given column alias, which the selection carries instead, so the
     * expression can also be grouped and ordered by. The alias may be quoted with double quotes, backquotes or
     * square brackets, and then hold any character. Comments after the alias are removed with it.
     */
    static String withoutAlias(String column, String alias) {
        if (alias == null) {
            return column;
        }
        Matcher matcher = TRAILING_ALIAS.matcher(withoutTrailingComments(column));
        if (matcher.matches() && alias.equalsIgnoreCase(unquote(matcher.group(2)))) {
            return matcher.group(1).trim();
        }
        return column;
    }

    /**
     * Returns an identifier quoted with double quotes, backquotes or square brackets without its quotes, reading a
     * doubled closing quote inside it as a single one, or any other SQL as it is.
     */
    static String unquote(String identifier) {
        if (!QUOTED.matcher(identifier).matches()) {
            return identifier;
        }
        String closingQuote = identifier.substring(identifier.length() - 1);
        return identifier.substring(1, identifier.length() - 1).replace(closingQuote + closingQuote, closingQuote);
    }
}
