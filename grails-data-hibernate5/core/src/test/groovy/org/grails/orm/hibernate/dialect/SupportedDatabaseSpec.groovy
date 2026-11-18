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

import org.hibernate.dialect.CockroachDB192Dialect
import org.hibernate.dialect.DB2390Dialect
import org.hibernate.dialect.DB2390V8Dialect
import org.hibernate.dialect.DB2400Dialect
import org.hibernate.dialect.DB297Dialect
import org.hibernate.dialect.DB2Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MariaDB103Dialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQL8Dialect
import org.hibernate.dialect.Oracle12cDialect
import org.hibernate.dialect.Oracle8iDialect
import org.hibernate.dialect.Oracle9Dialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQL10Dialect
import org.hibernate.dialect.SQLServer2012Dialect
import org.hibernate.dialect.SybaseDialect
import spock.lang.Specification
import spock.lang.Unroll


class SupportedDatabaseSpec extends Specification {

    @Unroll
    void '#dialect.class.simpleName is #expected'() {
        expect:
        SupportedDatabase.forDialect(dialect) == expected

        where:
        dialect                       || expected
        new MySQL8Dialect()           || SupportedDatabase.MYSQL
        new MariaDB103Dialect()       || SupportedDatabase.MARIADB
        new MariaDBDialect()          || SupportedDatabase.MARIADB
        new PostgreSQL10Dialect()     || SupportedDatabase.POSTGRESQL
        new CockroachDB192Dialect()   || SupportedDatabase.COCKROACH
        new Oracle8iDialect()         || SupportedDatabase.ORACLE
        new Oracle12cDialect()        || SupportedDatabase.ORACLE
        new Oracle9Dialect()          || SupportedDatabase.ORACLE
        new OracleDialect()           || SupportedDatabase.ORACLE
        new SQLServer2012Dialect()    || SupportedDatabase.SQL_SERVER
        new SybaseDialect()           || SupportedDatabase.SYBASE
        new H2Dialect()               || SupportedDatabase.H2
        new DB2Dialect()              || SupportedDatabase.DB2
        new DB297Dialect()            || SupportedDatabase.DB2
        new DB2400Dialect()           || SupportedDatabase.DB2
        new DB2390Dialect()           || SupportedDatabase.DB2_ZOS
        new DB2390V8Dialect()         || SupportedDatabase.DB2_ZOS
    }

    void 'a dialect that is not known is the default'() {
        expect:
        SupportedDatabase.forDialect(null) == SupportedDatabase.DEFAULT
    }

    @Unroll
    void '#database matches a regular expression with #pattern'() {
        expect:
        SupportedDatabase.valueOf(database).regexPattern() == pattern

        where:
        database      || pattern
        'MYSQL'       || '?1 rlike ?2'
        'MARIADB'     || '?1 rlike ?2'
        'POSTGRESQL'  || '?1 ~* ?2'
        'ORACLE'      || ' REGEXP_LIKE (?1, ?2)'
        'H2'          || '?1 REGEXP ?2'
        'DB2'         || ' REGEXP_LIKE (?1, ?2)'
        'DB2_ZOS'     || '?1 LIKE ?2'
        'SQL_SERVER'  || '?1 LIKE ?2'
        'SYBASE'      || '?1 LIKE ?2'
        'COCKROACH'   || '?1 LIKE ?2'
        'DEFAULT'     || '?1 LIKE ?2'
    }
}
