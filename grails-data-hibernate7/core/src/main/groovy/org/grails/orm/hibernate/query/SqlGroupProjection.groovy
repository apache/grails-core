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

import org.grails.datastore.mapping.query.Query

/**
 * Native SQL expression the results are grouped by, created by the group by clause of a {@code sqlGroupProjection}
 * in a criteria query. It is not selected: the columns of the {@code sqlGroupProjection} are {@link SqlProjection}s.
 * In the SQL, {@code {alias}} stands for the table alias of the queried entity.
 *
 * @since 8.0.1
 */
@CompileStatic
class SqlGroupProjection extends Query.Projection {

    private final String sql

    SqlGroupProjection(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException('The group by clause of a sqlGroupProjection must not be empty')
        }
        this.sql = sql
    }

    /**
     * Returns the SQL expression the results are grouped by.
     */
    String getSql() {
        return sql
    }

    /**
     * Splits the group by clause of a {@code sqlGroupProjection}, a single expression or a comma separated list of
     * them, into one projection per expression.
     */
    static List<SqlGroupProjection> of(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            throw new IllegalArgumentException('The group by clause of a sqlGroupProjection must not be empty')
        }
        List<SqlGroupProjection> projections = []
        for (String expression : SqlProjection.splitColumns(groupBy)) {
            projections.add(new SqlGroupProjection(expression))
        }
        return projections
    }

}
