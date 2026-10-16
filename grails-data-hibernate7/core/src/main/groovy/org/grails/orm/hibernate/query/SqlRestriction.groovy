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

import org.grails.datastore.mapping.query.Query

/**
 * Criterion that restricts the results with a native SQL condition, created by {@code sqlRestriction} in a
 * criteria query. In the SQL, {@code {alias}} stands for the table alias of the queried entity, and each
 * {@code ?} outside a string literal, a quoted identifier and a comment for one of the values, which are bound
 * as parameters.
 *
 * @param sql the SQL condition
 * @param values the values of the {@code ?} placeholders, in order
 * @since 8.0.0
 */
@CompileStatic
record SqlRestriction(String sql, List<?> values) implements Query.Criterion {

    SqlRestriction {
        if (sql == null) {
            throw new IllegalArgumentException('The SQL of a sqlRestriction must not be null')
        }
        if (values == null) {
            values = List.of()
        }
        List<Object> copy = new ArrayList<>(values.size())
        for (Object value : values) {
            if (value == null) {
                throw new IllegalArgumentException("The values of a sqlRestriction must not be null: ${sql}".toString())
            }
            copy.add(value instanceof CharSequence ? value.toString() : value)
        }
        int placeholders = placeholderIndexes(sql).size()
        if (placeholders != copy.size()) {
            throw new IllegalArgumentException(
                    "The SQL of a sqlRestriction has ${placeholders} ? placeholders but ${copy.size()} values: ${sql}".toString())
        }
        values = Collections.unmodifiableList(copy)
    }

    /**
     * Returns the indexes of the {@code ?} placeholders in the SQL. A {@code ?} in a string literal, a quoted
     * identifier, a line comment or a block comment is not a placeholder.
     */
    @PackageScope
    static List<Integer> placeholderIndexes(String sql) {
        List<Integer> placeholders = new ArrayList<>()
        scan(sql, placeholders)
        return placeholders
    }

    /**
     * Returns whether the SQL ends in a line comment, which would comment out anything appended to it on the same
     * line.
     */
    @PackageScope
    static boolean endsInLineComment(String sql) {
        return scan(sql, new ArrayList<Integer>())
    }

    private static boolean scan(String sql, List<Integer> placeholders) {
        int length = sql.length()
        int i = 0
        while (i < length) {
            char c = sql.charAt(i)
            if (c == (char) "'" || c == (char) '"' || c == (char) '`') {
                i = skipQuoted(sql, i, c)
            } else if (c == (char) '-' && sql.startsWith('--', i)) {
                int end = endOfLine(sql, i)
                if (end == length) {
                    return true
                }
                i = end
            } else if (c == (char) '/' && sql.startsWith('/*', i)) {
                int end = sql.indexOf('*/', i + 2)
                i = end == -1 ? length : end + 2
            } else {
                if (c == (char) '?') {
                    placeholders.add(i)
                }
                i++
            }
        }
        return false
    }

    /**
     * Returns the index after the quoted text starting at {@code start}, where a doubled quote stands for the quote
     * itself.
     */
    @PackageScope
    static int skipQuoted(String sql, int start, char quote) {
        int i = start + 1
        while (i < sql.length()) {
            if (sql.charAt(i) == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2
                    continue
                }
                return i + 1
            }
            i++
        }
        return i
    }

    @PackageScope
    static int endOfLine(String sql, int start) {
        for (int i = start; i < sql.length(); i++) {
            char c = sql.charAt(i)
            if (c == (char) '\n' || c == (char) '\r') {
                return i
            }
        }
        return sql.length()
    }

}
