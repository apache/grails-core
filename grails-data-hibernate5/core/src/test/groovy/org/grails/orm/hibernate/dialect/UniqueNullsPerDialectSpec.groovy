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

import org.hibernate.dialect.DB2390Dialect
import org.hibernate.dialect.DB2400Dialect
import org.hibernate.dialect.DB2Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MySQL57Dialect
import org.hibernate.dialect.Oracle10gDialect
import org.hibernate.dialect.Oracle9Dialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQL95Dialect
import org.hibernate.dialect.SQLServer2012Dialect
import org.hibernate.dialect.SybaseASE157Dialect
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.config.UniqueNulls

class UniqueNullsPerDialectSpec extends Specification {

    @Unroll
    void '#dialect reads a null in a unique key as #expected with all-null exempt #allNullExempt'() {
        expect:
        UniqueNullsPerDialect.forDialect(dialect).uniqueNulls() == expected
        UniqueNullsPerDialect.forDialect(dialect).isEntirelyNullKeyExempt() == allNullExempt

        where:
        dialect                       || expected          | allNullExempt
        new H2Dialect()               || UniqueNulls.SKIP  | false
        new PostgreSQL95Dialect()     || UniqueNulls.SKIP  | false
        new MySQL57Dialect()          || UniqueNulls.SKIP  | false
        new DB2Dialect()              || UniqueNulls.VALUE | false
        new DB2400Dialect()           || UniqueNulls.VALUE | false
        new DB2390Dialect()           || UniqueNulls.VALUE | false
        new Oracle10gDialect()        || UniqueNulls.VALUE | true
        new Oracle9Dialect()          || UniqueNulls.VALUE | true
        new OracleDialect()           || UniqueNulls.VALUE | true
        new SQLServer2012Dialect()    || UniqueNulls.VALUE | false
        new SybaseASE157Dialect()     || UniqueNulls.VALUE | false
    }

    @Unroll
    void 'H2 with #url reads nulls as #expected with all-null exempt #allNullExempt'() {
        expect:
        UniqueNullsPerDialect.forDialect(new H2Dialect(), url).uniqueNulls() == expected
        UniqueNullsPerDialect.forDialect(new H2Dialect(), url).isEntirelyNullKeyExempt() == allNullExempt

        where:
        url                                 || expected          | allNullExempt
        'jdbc:h2:mem:db;LOCK_TIMEOUT=10000' || UniqueNulls.SKIP  | false
        'jdbc:h2:mem:db;MODE=PostgreSQL'    || UniqueNulls.SKIP  | false
        'jdbc:h2:mem:db;MODE=MySQL'         || UniqueNulls.SKIP  | false
        'jdbc:h2:mem:db;MODE=Oracle'        || UniqueNulls.VALUE | true
        'jdbc:h2:mem:db;MODE=MSSQLServer'   || UniqueNulls.VALUE | false
        'jdbc:h2:mem:db;MODE=Derby'         || UniqueNulls.VALUE | false
    }

    @Unroll
    void 'H2 with mode #mode reads nulls as #expected with all-null exempt #allNullExempt'() {
        expect:
        UniqueNullsPerDialect.forDialect(new H2Dialect(), null, mode).uniqueNulls() == expected
        UniqueNullsPerDialect.forDialect(new H2Dialect(), null, mode).isEntirelyNullKeyExempt() == allNullExempt

        where:
        mode          || expected          | allNullExempt
        'REGULAR'     || UniqueNulls.SKIP  | false
        'PostgreSQL'  || UniqueNulls.SKIP  | false
        'MySQL'       || UniqueNulls.SKIP  | false
        'Oracle'      || UniqueNulls.VALUE | true
        'MSSQLServer' || UniqueNulls.VALUE | false
        'Derby'       || UniqueNulls.VALUE | false
    }
}
