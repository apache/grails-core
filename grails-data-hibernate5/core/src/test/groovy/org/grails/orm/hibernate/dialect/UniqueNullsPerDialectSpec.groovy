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
    void '#dialect reads a null in a unique key as #expected'() {
        expect:
        UniqueNullsPerDialect.forDialect(dialect).uniqueNulls() == expected

        where:
        dialect                       || expected
        new H2Dialect()               || UniqueNulls.SKIP
        new PostgreSQL95Dialect()     || UniqueNulls.SKIP
        new MySQL57Dialect()          || UniqueNulls.SKIP
        new DB2Dialect()              || UniqueNulls.SKIP
        new Oracle10gDialect()        || UniqueNulls.VALUE
        new Oracle9Dialect()          || UniqueNulls.VALUE
        new OracleDialect()           || UniqueNulls.VALUE
        new SQLServer2012Dialect()    || UniqueNulls.VALUE
        new SybaseASE157Dialect()     || UniqueNulls.VALUE
    }
}
