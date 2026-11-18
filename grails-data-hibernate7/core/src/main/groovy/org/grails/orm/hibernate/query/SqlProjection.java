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
     * Splits the SQL of a {@code sqlProjection} or {@code sqlGroupProjection} into one projection per column: at
     * the commas outside parentheses, string literals, quoted identifiers and comments, read in the syntax of the
     * database. A column alias that follows a column after {@code as} is removed from its SQL, which the selection
     * carries instead; it may be quoted the way the syntax quotes an identifier, and then hold any character.
     *
     * @param sql the SQL selecting the columns, separated by commas
     * @param columnAliases the alias of each column, in order
     * @param types the type of each column, in order: an {@code org.hibernate.type.StandardBasicTypes} constant, an
     *     {@code org.hibernate.type.Type} or a Java class
     * @param syntax the syntax of the database's native SQL
     * @return the projection of each column
     * @throws IllegalArgumentException if the number of columns, aliases and types differ
     */
    public static List<SqlProjection> of(String sql, List<String> columnAliases, List<?> types, SqlSyntax syntax) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("The SQL of a sqlProjection must not be empty");
        }
        if (columnAliases == null || types == null || columnAliases.size() != types.size()) {
            throw new IllegalArgumentException("A sqlProjection needs as many types as column aliases: " + sql);
        }
        List<String> columns = SqlText.of(sql, syntax).splitTopLevel(',');
        if (columns.size() != columnAliases.size()) {
            throw new IllegalArgumentException("The SQL of a sqlProjection selects " + columns.size() +
                    " columns but " + columnAliases.size() + " column aliases were given: " + sql);
        }
        List<SqlProjection> projections = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String alias = columnAliases.get(i);
            String column = alias == null ? columns.get(i) : SqlText.of(columns.get(i), syntax).withoutTrailingAlias(alias);
            projections.add(new SqlProjection(column, alias, types.get(i)));
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
}
