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
package grails.orm

import org.hibernate.Criteria
import org.hibernate.HibernateException
import org.hibernate.criterion.CriteriaQuery
import org.hibernate.dialect.DB2390Dialect
import org.hibernate.dialect.DB2390V8Dialect
import org.hibernate.dialect.DB2400Dialect
import org.hibernate.dialect.DB297Dialect
import org.hibernate.dialect.DB2Dialect
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MySQL8Dialect
import org.hibernate.dialect.Oracle12cDialect
import org.hibernate.dialect.PostgreSQL10Dialect
import org.hibernate.dialect.SQLServer2012Dialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The SQL that {@link RlikeExpression} renders for each database, and the databases it refuses.
 */
class RlikeExpressionSpec extends Specification {

    @Unroll
    void 'on #dialect.class.simpleName rlike renders #sql'() {
        expect:
        new RlikeExpression('name', 'a.*').toSqlString(Stub(Criteria), criteriaQuery(dialect)) == sql

        where:
        dialect                    | sql
        new MySQL8Dialect()        | 'name_col rlike ?'
        new Oracle12cDialect()     | ' REGEXP_LIKE (name_col, ?)'
        new PostgreSQL10Dialect()  | 'name_col ~* ?'
        new H2Dialect()            | 'name_col REGEXP ?'
        new DB2Dialect()           | ' REGEXP_LIKE (name_col, ?)'
        new DB297Dialect()         | ' REGEXP_LIKE (name_col, ?)'
        new DB2400Dialect()        | ' REGEXP_LIKE (name_col, ?)'
    }

    @Unroll
    void 'on #dialect.class.simpleName rlike is refused'() {
        when:
        new RlikeExpression('name', 'a.*').toSqlString(Stub(Criteria), criteriaQuery(dialect))

        then:
        HibernateException e = thrown()
        e.message == "rlike is not supported with the configured dialect ${dialect.class.canonicalName}"

        where:
        dialect << [new DB2390Dialect(), new DB2390V8Dialect(), new SQLServer2012Dialect()]
    }

    private CriteriaQuery criteriaQuery(Dialect dialect) {
        SessionFactoryImplementor factory = Stub {
            getDialect() >> dialect
        }
        Stub(CriteriaQuery) {
            getFactory() >> factory
            getColumnsUsingProjection(_ as Criteria, 'name') >> (['name_col'] as String[])
        }
    }
}
