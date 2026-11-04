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

    @Unroll
    void "fromJdbcUrl resolves '#url' to #expected"() {
        expect:
        DatabaseDriver.fromJdbcUrl(url) == expected

        where:
        url                                             | expected
        'jdbc:h2:mem:testDb'                             | DatabaseDriver.H2
        'jdbc:mysql://localhost:3306/test'               | DatabaseDriver.MYSQL
        'jdbc:mariadb://localhost:3306/test'              | DatabaseDriver.MARIADB
        'jdbc:postgresql://localhost:5432/test'           | DatabaseDriver.POSTGRESQL
        'jdbc:oracle:thin:@localhost:1521:orcl'           | DatabaseDriver.ORACLE
        'jdbc:sqlserver://localhost:1433;databaseName=x'  | DatabaseDriver.SQLSERVER
        'jdbc:hsqldb:mem:testDb'                          | DatabaseDriver.HSQLDB
        'jdbc:sqlite:test.db'                             | DatabaseDriver.SQLITE
        'jdbc:derby:memory:testDb'                        | DatabaseDriver.DERBY
        'jdbc:unknowndb:foo'                              | DatabaseDriver.UNKNOWN
        null                                              | DatabaseDriver.UNKNOWN
        ''                                                | DatabaseDriver.UNKNOWN
    }

    void "fromJdbcUrl throws IllegalArgumentException for a URL not starting with jdbc"() {
        when:
        DatabaseDriver.fromJdbcUrl('mysql://localhost:3306/test')

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    void "fromProductName resolves '#productName' to #expected"() {
        expect:
        DatabaseDriver.fromProductName(productName) == expected

        where:
        productName                    | expected
        'H2'                           | DatabaseDriver.H2
        'h2'                           | DatabaseDriver.H2
        'MySQL'                        | DatabaseDriver.MYSQL
        'PostgreSQL'                   | DatabaseDriver.POSTGRESQL
        'Oracle'                       | DatabaseDriver.ORACLE
        'Firebird 3.0'                 | DatabaseDriver.FIREBIRD
        'DB2/LINUXX8664'               | DatabaseDriver.DB2
        'DB2 UDB for AS/400'           | DatabaseDriver.DB2_AS400
        'Something running on AS/400'  | DatabaseDriver.DB2_AS400
        'Completely unknown product'   | DatabaseDriver.UNKNOWN
        null                           | DatabaseDriver.UNKNOWN
        ''                             | DatabaseDriver.UNKNOWN
    }

    void "getDriverClassName and getValidationQuery and getXaDataSourceClassName return the expected values for H2"() {
        expect:
        DatabaseDriver.H2.driverClassName == 'org.h2.Driver'
        DatabaseDriver.H2.xaDataSourceClassName == 'org.h2.jdbcx.JdbcDataSource'
        DatabaseDriver.H2.validationQuery == 'SELECT 1'
    }

    void "UNKNOWN driver has no driver class name, xa data source or validation query"() {
        expect:
        DatabaseDriver.UNKNOWN.driverClassName == null
        DatabaseDriver.UNKNOWN.xaDataSourceClassName == null
        DatabaseDriver.UNKNOWN.validationQuery == null
    }
}
