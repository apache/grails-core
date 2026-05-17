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

package example

import example.query.NumberLikeExpression
import org.hibernate.Criteria
import org.hibernate.HibernateException
import org.hibernate.criterion.CriteriaQuery
import org.hibernate.dialect.Dialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.type.StandardBasicTypes
import spock.lang.Specification
import spock.lang.Unroll

class NumberLikeExpressionSpec extends Specification {

    Criteria criteria = Mock(Criteria)
    CriteriaQuery criteriaQuery = Mock(CriteriaQuery)
    SessionFactoryImplementor sessionFactory = Mock(SessionFactoryImplementor)

    def setup() {
        criteriaQuery.getFactory() >> sessionFactory
        sessionFactory.getDialect() >> Mock(Dialect)
    }

    @Unroll
    void "toSqlString generates correct SQL for value '#value'"() {
        given:
        criteriaQuery.getColumnsUsingProjection(criteria, 'price') >> ['price_col']
        def expr = new NumberLikeExpression('price', value)

        when:
        String sql = expr.toSqlString(criteria, criteriaQuery)

        then:
        sql == expectedSql

        where:
        value       || expectedSql
        '100'       || "trim(to_char(trunc(price_col,0), '999999999999999999999999990')) like ?"
        '99.9'      || "trim(to_char(trunc(price_col,1), '999999999999999999999999990.9')) like ?"
        '99.99'     || "trim(to_char(trunc(price_col,2), '999999999999999999999999990.99')) like ?"
        '1%'        || "trim(to_char(trunc(price_col,0), '999999999999999999999999990')) like ?"
        '9.9%'      || "trim(to_char(trunc(price_col,1), '999999999999999999999999990.9')) like ?"
    }

    void "toSqlString strips commas from value in constructor"() {
        given:
        criteriaQuery.getColumnsUsingProjection(criteria, 'price') >> ['price_col']
        def expr = new NumberLikeExpression('price', '1,000.50')

        when:
        String sql = expr.toSqlString(criteria, criteriaQuery)

        then:
        sql == "trim(to_char(trunc(price_col,2), '999999999999999999999999990.99')) like ?"
    }

    void "toSqlString throws HibernateException for multi-column properties"() {
        given:
        criteriaQuery.getColumnsUsingProjection(criteria, 'price') >> ['col1', 'col2']
        def expr = new NumberLikeExpression('price', '100')

        when:
        expr.toSqlString(criteria, criteriaQuery)

        then:
        thrown(HibernateException)
    }

    void "getTypedValues returns a single STRING-typed value"() {
        given:
        def expr = new NumberLikeExpression('price', '99.99')

        when:
        def typedValues = expr.getTypedValues(criteria, criteriaQuery)

        then:
        typedValues.length == 1
        typedValues[0].value == '99.99'
        typedValues[0].type == StandardBasicTypes.STRING
    }

    void "toString returns a readable representation"() {
        expect:
        new NumberLikeExpression('price', '100').toString() == 'price like 100'
    }

    void "commas are stripped from value before storage"() {
        expect:
        new NumberLikeExpression('price', '1,000').toString() == 'price like 1000'
    }
}
