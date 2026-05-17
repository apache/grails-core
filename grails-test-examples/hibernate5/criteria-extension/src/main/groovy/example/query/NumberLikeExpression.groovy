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

package example.query

import groovy.transform.CompileStatic
import org.hibernate.Criteria
import org.hibernate.HibernateException
import org.hibernate.criterion.CriteriaQuery
import org.hibernate.criterion.Criterion
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.engine.spi.TypedValue
import org.hibernate.type.StandardBasicTypes

/**
 * A Hibernate {@link Criterion} that casts a numeric column to a string and performs
 * a LIKE comparison.
 *
 * Commas are stripped from the input value so formatted numbers like {@code "1,000"} work correctly.
 */
@CompileStatic
class NumberLikeExpression implements Criterion {

    private final String propertyName
    private final String value

    NumberLikeExpression(String propertyName, String value) {
        this.propertyName = propertyName
        this.value = value.replaceAll(',', '')
    }

    @Override
    String toSqlString(Criteria criteria, CriteriaQuery criteriaQuery) throws HibernateException {
        String[] columns = criteriaQuery.getColumnsUsingProjection(criteria, propertyName)
        if (columns.length != 1) {
            throw new HibernateException("numberLike may only be used with single-column properties")
        }
        String col = columns[0]
        "cast(${col} as varchar) like ?" as String
    }

    @Override
    TypedValue[] getTypedValues(Criteria criteria, CriteriaQuery criteriaQuery) throws HibernateException {
        [new TypedValue(StandardBasicTypes.STRING, value)] as TypedValue[]
    }

    @Override
    String toString() {
        "${propertyName} like ${value}"
    }
}
