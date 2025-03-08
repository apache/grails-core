/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.orm;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.criterion.CriteriaQuery;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.MatchMode;
import org.hibernate.dialect.*;
import org.hibernate.engine.spi.TypedValue;

/**
 * Adds support for rlike to Hibernate in supported dialects.
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

        if (dialect instanceof MySQLDialect) {
            return columns[0] + " rlike ?";
        }

        if (isOracleDialect(dialect)) {
            return " REGEXP_LIKE (" + columns[0] + ", ?)";
        }

        if (dialect instanceof PostgreSQL81Dialect) {
            return columns[0] + " ~* ?";
        }

        if (dialect instanceof H2Dialect) {
            return columns[0] + " REGEXP ?";
        }

        throw new HibernateException("rlike is not supported with the configured dialect " + dialect.getClass().getCanonicalName());
    }

    private boolean isOracleDialect(Dialect dialect) {
        return (dialect instanceof Oracle8iDialect);
    }

    public TypedValue[] getTypedValues(Criteria criteria, CriteriaQuery criteriaQuery) throws HibernateException {
        return new TypedValue[] { criteriaQuery.getTypedValue(criteria, propertyName, value.toString()) };
    }

    @Override
    public String toString() {
        return propertyName + " rlike " + value;
    }
}
