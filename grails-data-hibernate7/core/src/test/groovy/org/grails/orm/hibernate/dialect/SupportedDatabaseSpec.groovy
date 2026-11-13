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
package org.grails.orm.hibernate.dialect

import org.hibernate.dialect.CockroachDialect
import org.hibernate.dialect.DB2Dialect
import org.hibernate.dialect.DB2iDialect
import org.hibernate.dialect.DB2zDialect
import org.hibernate.dialect.DatabaseVersion
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.dialect.SQLServerDialect
import org.hibernate.dialect.SybaseDialect
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.query.SqlSyntax

class SupportedDatabaseSpec extends Specification {

    @Unroll
    void '#dialect.class.simpleName is #expected'() {
        expect:
        SupportedDatabase.forDialect(dialect) == expected

        where:
        dialect                                  || expected
        new MySQLDialect()                       || SupportedDatabase.MYSQL
        new MariaDBDialect()                     || SupportedDatabase.MARIADB
        new PostgreSQLDialect()                  || SupportedDatabase.POSTGRESQL
        new CockroachDialect()                   || SupportedDatabase.COCKROACH
        new OracleDialect()                      || SupportedDatabase.ORACLE
        new SQLServerDialect()                   || SupportedDatabase.SQL_SERVER
        new SybaseDialect()                      || SupportedDatabase.SYBASE
        new H2Dialect()                          || SupportedDatabase.H2
        new DB2iDialect()                        || SupportedDatabase.DB2_IBM_I
        new DB2zDialect()                        || SupportedDatabase.DB2_ZOS
        new DB2Dialect()                         || SupportedDatabase.DB2
        new Dialect(DatabaseVersion.make(1)) { } || SupportedDatabase.DEFAULT
    }

    void 'a dialect that is not known is the default'() {
        expect:
        SupportedDatabase.forDialect(null) == SupportedDatabase.DEFAULT
    }

    @Unroll
    void '#dialect.class.simpleName matches a regular expression with #pattern'() {
        expect:
        SupportedDatabase.forDialect(dialect).regexPattern() == pattern

        where:
        dialect                 || pattern
        new MySQLDialect()      || '?1 RLIKE ?2'
        new MariaDBDialect()    || '?1 REGEXP ?2'
        new PostgreSQLDialect() || '?1 ~ ?2'
        new CockroachDialect()  || '?1 ~ ?2'
        new OracleDialect()     || 'REGEXP_LIKE(?1, ?2)'
        new H2Dialect()         || 'REGEXP_LIKE(?1, ?2)'
        new DB2Dialect()        || 'REGEXP_LIKE(?1, ?2)'
        new DB2iDialect()       || 'REGEXP_LIKE(?1, ?2)'
        // only through the Db2 Analytics Accelerator
        new DB2zDialect()       || '?1 LIKE ?2'
        new SQLServerDialect()  || 'CASE WHEN ?1 LIKE ?2 THEN 1 ELSE 0 END'
    }

    @Unroll
    void '#dialect.class.simpleName reads native SQL as #syntax'() {
        expect:
        SupportedDatabase.forDialect(dialect).sqlSyntax() == syntax

        where:
        dialect                 || syntax
        new SQLServerDialect()  || SqlSyntax.TRANSACT_SQL
        new SybaseDialect()     || SqlSyntax.TRANSACT_SQL
        new PostgreSQLDialect() || SqlSyntax.POSTGRESQL
        new CockroachDialect()  || SqlSyntax.POSTGRESQL
        new MySQLDialect()      || SqlSyntax.MYSQL
        new MariaDBDialect()    || SqlSyntax.MYSQL
        new OracleDialect()     || SqlSyntax.ORACLE
        new H2Dialect()         || SqlSyntax.H2
        new DB2iDialect()       || SqlSyntax.DB2_NESTED_COMMENTS
        new DB2zDialect()       || SqlSyntax.DB2_NESTED_COMMENTS
        new DB2Dialect()        || SqlSyntax.SQL
    }

    @Unroll
    void '#dialect.class.simpleName has a unique index that treats a null as #expected'() {
        expect:
        SupportedDatabase.forDialect(dialect).uniqueIndexNulls() == expected
        UniqueIndexNulls.forDialect(dialect) == expected

        where:
        dialect                 || expected
        new H2Dialect()         || UniqueIndexNulls.DISTINCT
        new PostgreSQLDialect() || UniqueIndexNulls.DISTINCT
        new CockroachDialect()  || UniqueIndexNulls.DISTINCT
        new MySQLDialect()      || UniqueIndexNulls.DISTINCT
        new MariaDBDialect()    || UniqueIndexNulls.DISTINCT
        new SQLServerDialect()  || UniqueIndexNulls.DISTINCT
        new OracleDialect()     || UniqueIndexNulls.COMPARED
        new DB2Dialect()        || UniqueIndexNulls.COMPARED
        new DB2iDialect()       || UniqueIndexNulls.DISTINCT
        new DB2zDialect()       || UniqueIndexNulls.DISTINCT
    }
}
