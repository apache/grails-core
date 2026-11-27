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

import jakarta.persistence.criteria.CriteriaBuilder
import org.hibernate.dialect.CockroachDialect
import org.hibernate.dialect.DB2Dialect
import org.hibernate.dialect.DB2zDialect
import org.hibernate.dialect.DB2iDialect
import org.hibernate.dialect.DatabaseVersion
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.HANADialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.dialect.SQLServerDialect
import org.hibernate.dialect.SybaseASEDialect
import spock.lang.Specification
import spock.lang.Unroll

class SqlSyntaxSpec extends Specification {

    @Unroll
    void 'the native SQL of #dialectName is read as #syntax'() {
        expect:
        SqlSyntax.forDialect(dialect) == syntax

        where:
        dialect                                              | syntax
        new SQLServerDialect()                               | SqlSyntax.TRANSACT_SQL
        new SybaseASEDialect()                               | SqlSyntax.TRANSACT_SQL
        new H2Dialect()                                      | SqlSyntax.H2
        new PostgreSQLDialect()                              | SqlSyntax.POSTGRESQL
        new CockroachDialect()                               | SqlSyntax.POSTGRESQL
        new MySQLDialect()                                   | SqlSyntax.MYSQL
        new MariaDBDialect()                                 | SqlSyntax.MYSQL
        new OracleDialect()                                  | SqlSyntax.ORACLE
        new DB2Dialect()                                     | SqlSyntax.SQL
        new DB2iDialect()                                    | SqlSyntax.DB2_NESTED_COMMENTS
        new DB2zDialect()                                    | SqlSyntax.DB2_NESTED_COMMENTS
        new HANADialect()                                    | SqlSyntax.SQL
        new Dialect(DatabaseVersion.make(1)) { }             | SqlSyntax.SQL
        dialectName = dialect.class.simpleName
    }

    void "a criteria builder that is not Hibernate's reads SQL"() {
        expect:
        SqlSyntax.forCriteriaBuilder(Stub(CriteriaBuilder)) == SqlSyntax.SQL
    }
}
