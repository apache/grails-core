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

import org.grails.datastore.mapping.config.UniqueNulls
import org.grails.orm.hibernate.query.SqlSyntax

class SupportedDatabaseSpec extends Specification {

    @Unroll
    void '#dialect.class.simpleName is #expected'() {
        expect:
        SupportedDatabase.forDialect(dialect) == expected

        where:
        dialect                                  || expected
        new MySQLDialect()                       || SupportedDatabase.MYSQL
        new MariaDBDialect()                     || SupportedDatabase.MYSQL
        new PostgreSQLDialect()                  || SupportedDatabase.POSTGRESQL
        new CockroachDialect()                   || SupportedDatabase.COCKROACH
        new OracleDialect()                      || SupportedDatabase.ORACLE
        new SQLServerDialect()                   || SupportedDatabase.SQL_SERVER
        new SybaseDialect()                      || SupportedDatabase.SQL_SERVER
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
        new MariaDBDialect()    || '?1 RLIKE ?2'
        new PostgreSQLDialect() || '?1 ~* ?2'
        new OracleDialect()     || 'REGEXP_LIKE(?1, ?2)'
        new H2Dialect()         || 'REGEXP_LIKE(?1, ?2)'
        new DB2Dialect()        || 'REGEXP_LIKE(?1, ?2)'
        new DB2iDialect()       || 'REGEXP_LIKE(?1, ?2)'
        // only through the Db2 Analytics Accelerator
        new DB2zDialect()       || '?1 LIKE ?2'
        new SQLServerDialect()  || '?1 LIKE ?2'
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
    void '#dialect.class.simpleName reads a null in a unique key as #expected with all-null exempt #allNullExempt'() {
        when:
        SupportedDatabase.UniqueKeyNulls nulls = SupportedDatabase.uniqueKeyNulls(dialect, null, null)

        then:
        nulls.uniqueNulls() == expected
        nulls.entirelyNullKeyExempt() == allNullExempt
        SupportedDatabase.forDialect(dialect).uniqueIndexNulls() == nulls.uniqueIndexNulls()
        SupportedDatabase.forDialect(dialect).entirelyNullKeyExempt == allNullExempt

        where:
        dialect                 || expected          | allNullExempt
        new H2Dialect()         || UniqueNulls.SKIP  | false
        new PostgreSQLDialect() || UniqueNulls.SKIP  | false
        new CockroachDialect()  || UniqueNulls.SKIP  | false
        new MySQLDialect()      || UniqueNulls.SKIP  | false
        new MariaDBDialect()    || UniqueNulls.SKIP  | false
        new SQLServerDialect()  || UniqueNulls.SKIP  | false
        new OracleDialect()     || UniqueNulls.VALUE | true
        new DB2Dialect()        || UniqueNulls.VALUE | true
        new DB2iDialect()       || UniqueNulls.SKIP  | false
        new DB2zDialect()       || UniqueNulls.SKIP  | false
    }

    @Unroll
    void 'H2 reporting mode #mode reads nulls as #expected with all-null exempt #allNullExempt'() {
        when:
        SupportedDatabase.UniqueKeyNulls nulls = SupportedDatabase.uniqueKeyNulls(new H2Dialect(), mode, null)

        then:
        nulls.uniqueNulls() == expected
        nulls.entirelyNullKeyExempt() == allNullExempt

        where:
        mode          || expected          | allNullExempt
        'REGULAR'     || UniqueNulls.SKIP  | false
        'PostgreSQL'  || UniqueNulls.SKIP  | false
        'MySQL'       || UniqueNulls.SKIP  | false
        'MariaDB'     || UniqueNulls.SKIP  | false
        'HSQLDB'      || UniqueNulls.SKIP  | false
        'STRICT'      || UniqueNulls.SKIP  | false
        'LEGACY'      || UniqueNulls.SKIP  | false
        // H2's DB2 mode keeps nulls distinct, as the regular mode does
        'DB2'         || UniqueNulls.SKIP  | false
        'Oracle'      || UniqueNulls.VALUE | true
        'ORACLE'      || UniqueNulls.VALUE | true
        'MSSQLServer' || UniqueNulls.VALUE | false
        'Derby'       || UniqueNulls.VALUE | false
    }

    @Unroll
    void 'H2 with #url reads nulls as #expected with all-null exempt #allNullExempt'() {
        when:
        SupportedDatabase.UniqueKeyNulls nulls = SupportedDatabase.uniqueKeyNulls(new H2Dialect(), null, url)

        then:
        nulls.uniqueNulls() == expected
        nulls.entirelyNullKeyExempt() == allNullExempt

        where:
        url                                              || expected          | allNullExempt
        null                                             || UniqueNulls.SKIP  | false
        'jdbc:h2:mem:db;LOCK_TIMEOUT=10000'              || UniqueNulls.SKIP  | false
        'jdbc:h2:mem:db;MODE=PostgreSQL'                 || UniqueNulls.SKIP  | false
        'jdbc:h2:mem:db;MODE=DB2'                        || UniqueNulls.SKIP  | false
        'jdbc:h2:mem:db;MODE=Sybase'                     || UniqueNulls.SKIP  | false
        'jdbc:h2:mem:db;MODE=Oracle'                     || UniqueNulls.VALUE | true
        'jdbc:h2:mem:db;mode=oracle;LOCK_TIMEOUT=10000'  || UniqueNulls.VALUE | true
        'jdbc:h2:mem:db;MODE=MSSQLServer'                || UniqueNulls.VALUE | false
        'jdbc:h2:mem:db;MODE=Derby'                      || UniqueNulls.VALUE | false
    }

    void 'the mode H2 reports wins over the one in the URL, and a blank report falls back to the URL'() {
        expect:
        SupportedDatabase.uniqueKeyNulls(new H2Dialect(), 'Oracle', 'jdbc:h2:mem:db;MODE=Derby').entirelyNullKeyExempt()
        !SupportedDatabase.uniqueKeyNulls(new H2Dialect(), '  ', 'jdbc:h2:mem:db;MODE=Derby').entirelyNullKeyExempt()
        SupportedDatabase.uniqueKeyNulls(new H2Dialect(), '', 'jdbc:h2:mem:db;MODE=Derby').uniqueNulls() == UniqueNulls.VALUE
    }

    void 'a mode is read only for H2, so another database ignores it'() {
        expect:
        SupportedDatabase.uniqueKeyNulls(new PostgreSQLDialect(), 'Oracle', 'jdbc:h2:mem:db;MODE=Oracle').uniqueNulls() == UniqueNulls.SKIP
        SupportedDatabase.uniqueKeyNulls(new OracleDialect(), 'REGULAR', null).uniqueNulls() == UniqueNulls.VALUE
    }

    void 'a dialect that is not known reads the default, and is not H2'() {
        expect:
        SupportedDatabase.uniqueKeyNulls(null, 'Oracle', null).uniqueNulls() == UniqueNulls.SKIP
        !SupportedDatabase.isH2(null)
        SupportedDatabase.isH2(new H2Dialect())
        !SupportedDatabase.isH2(new PostgreSQLDialect())
    }
}
