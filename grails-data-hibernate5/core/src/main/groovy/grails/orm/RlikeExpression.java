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
package grails.orm;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.criterion.CriteriaQuery;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.MatchMode;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.TypedValue;

import org.grails.orm.hibernate.dialect.SupportedDatabase;

/**
 * Adds support for rlike to Hibernate. On a database with no regular expression operator that Grails uses, rlike
 * falls back to like, as it does with Hibernate 7.
 *
 * @author Graeme Rocher
 * @since 1.1.1
 */
public class RlikeExpression implements Criterion {

    private static final long serialVersionUID = -214329918050957956L;

    private final String propertyName;
    private final Object value;

    public RlikeExpression(String propertyName, Object value) {
        this.propertyName = propertyName;
        this.value = value;
    }

    public RlikeExpression(String propertyName, String value, MatchMode matchMode) {
        this(propertyName, matchMode.toMatchString(value));
    }

    public String toSqlString(Criteria criteria, CriteriaQuery criteriaQuery) throws HibernateException {
        Dialect dialect = criteriaQuery.getFactory().getDialect();
        String[] columns = criteriaQuery.getColumnsUsingProjection(criteria, propertyName);
        if (columns.length != 1) {
            throw new HibernateException("rlike may only be used with single-column properties");
        }

        // the value placeholder goes first: the column is inserted last, so a formula or a quoted name that
        // contains "?2" reaches the SQL as it is
        return SupportedDatabase.forDialect(dialect).regexPattern().replace("?2", "?").replace("?1", columns[0]);
    }

    public TypedValue[] getTypedValues(Criteria criteria, CriteriaQuery criteriaQuery) throws HibernateException {
        return new TypedValue[] { criteriaQuery.getTypedValue(criteria, propertyName, value.toString()) };
    }

    @Override
    public String toString() {
        return propertyName + " rlike " + value;
    }
}
