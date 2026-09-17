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
package org.grails.datastore.gorm.jdbc

import spock.lang.Specification
import spock.lang.Unroll

class DatabaseDriverSpec extends Specification {

    void "test getDriverClassName, getXaDataSourceClassName and getValidationQuery for H2"() {
        expect:
        DatabaseDriver.H2.driverClassName == 'org.h2.Driver'
        DatabaseDriver.H2.xaDataSourceClassName == 'org.h2.jdbcx.JdbcDataSource'
        DatabaseDriver.H2.validationQuery == 'SELECT 1'
    }

    void "test UNKNOWN has no driver class, xa data source class or validation query"() {
        expect:
        DatabaseDriver.UNKNOWN.driverClassName == null
        DatabaseDriver.UNKNOWN.xaDataSourceClassName == null
        DatabaseDriver.UNKNOWN.validationQuery == null
    }

    @Unroll
    void "test fromJdbcUrl resolves #expected for #url"() {
        expect:
        DatabaseDriver.fromJdbcUrl(url) == expected

        where:
        url                                    | expected
        'jdbc:h2:mem:test'                     | DatabaseDriver.H2
        'jdbc:mysql://localhost/test'          | DatabaseDriver.MYSQL
        'jdbc:postgresql://localhost/test'     | DatabaseDriver.POSTGRESQL
        'jdbc:sqlite:test.db'                  | DatabaseDriver.SQLITE
        'jdbc:oracle:thin:@localhost:1521:orcl' | DatabaseDriver.ORACLE
        'jdbc:sqlserver://localhost;database=x' | DatabaseDriver.SQLSERVER
        'jdbc:somethingunrecognized:test'      | DatabaseDriver.UNKNOWN
    }

    void "test fromJdbcUrl returns UNKNOWN for a null or empty url"() {
        expect:
        DatabaseDriver.fromJdbcUrl(null) == DatabaseDriver.UNKNOWN
        DatabaseDriver.fromJdbcUrl('') == DatabaseDriver.UNKNOWN
    }

    void "test fromJdbcUrl rejects a url that does not start with jdbc"() {
        when:
        DatabaseDriver.fromJdbcUrl('not-a-jdbc-url')

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    void "test fromProductName resolves #expected for #productName"() {
        expect:
        DatabaseDriver.fromProductName(productName) == expected

        where:
        productName          | expected
        'H2'                 | DatabaseDriver.H2
        'h2'                 | DatabaseDriver.H2
        'MySQL'              | DatabaseDriver.MYSQL
        'PostgreSQL'         | DatabaseDriver.POSTGRESQL
        'Something Else'     | DatabaseDriver.UNKNOWN
    }

    void "test fromProductName returns UNKNOWN for a null or empty product name"() {
        expect:
        DatabaseDriver.fromProductName(null) == DatabaseDriver.UNKNOWN
        DatabaseDriver.fromProductName('') == DatabaseDriver.UNKNOWN
    }

    @Unroll
    void "test fromProductName custom matchProductName override resolves #expected for #productName"() {
        expect:
        DatabaseDriver.fromProductName(productName) == expected

        where:
        productName                | expected
        'Firebird 2.5.WI-V6.3.7'   | DatabaseDriver.FIREBIRD
        'FIREBIRD SOMETHING'       | DatabaseDriver.FIREBIRD
        'DB2/LINUXX8664'           | DatabaseDriver.DB2
        'DB2 UDB for AS/400'       | DatabaseDriver.DB2_AS400
        'Some AS/400 database'     | DatabaseDriver.DB2_AS400
    }
}
