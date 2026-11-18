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
import java.util.Collections;
import java.util.List;

import org.grails.datastore.mapping.query.Query;

/**
 * Criterion that restricts the results with a native SQL condition, created by {@code sqlRestriction} in a
 * criteria query. In the SQL, {@code {alias}} stands for the table alias of the queried entity, and each
 * {@code ?} outside a string literal, a quoted identifier and a comment for one of the values, which are bound
 * as parameters. The SQL is read in the {@link SqlSyntax} of the database: {@link #of} checks the placeholders
 * against the values when the restriction is created, and the query that renders it checks them again.
 *
 * <p>The canonical constructor checks that the SQL and the values are not {@code null} and copies the values, but does
 * not count the placeholders, because the count depends on the syntax of the database, which a restriction built
 * without a connection does not know. The count is checked where the SQL is rendered, on the connection the query
 * runs on, and earlier by {@link #of(String, List, SqlSyntax)} where the connection is known.</p>
 *
 * @param sql the SQL condition
 * @param values the values of the {@code ?} placeholders, in order
 * @since 8.0.0
 */
public record SqlRestriction(String sql, List<?> values) implements Query.Criterion {

    public SqlRestriction {
        if (sql == null) {
            throw new IllegalArgumentException("The SQL of a sqlRestriction must not be null");
        }
        if (values == null) {
            values = List.of();
        }
        List<Object> copy = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value == null) {
                throw new IllegalArgumentException("The values of a sqlRestriction must not be null: " + sql);
            }
            copy.add(value instanceof CharSequence ? value.toString() : value);
        }
        values = Collections.unmodifiableList(copy);
    }

    /**
     * Creates the restriction, checking that the SQL, read in the syntax of the database, has one {@code ?}
     * placeholder per value. A {@code ?} in a string literal, a quoted identifier, a line comment or a block comment
     * is not a placeholder; nor is one in a bracket identifier where the syntax has them.
     *
     * @param sql the SQL condition
     * @param values the values of the {@code ?} placeholders, in order, none of them {@code null}
     * @param syntax the syntax of the database's native SQL
     * @throws IllegalArgumentException if the SQL is {@code null}, a value is {@code null}, or the number of
     *     placeholders differs from the number of values
     */
    public static SqlRestriction of(String sql, List<?> values, SqlSyntax syntax) {
        SqlRestriction restriction = new SqlRestriction(sql, values);
        int placeholders = SqlText.of(sql, syntax).plainIndexes('?').size();
        if (placeholders != restriction.values().size()) {
            throw new IllegalArgumentException("The SQL of a sqlRestriction has " + placeholders +
                    " ? placeholders but " + restriction.values().size() + " values: " + sql);
        }
        return restriction;
    }
}
