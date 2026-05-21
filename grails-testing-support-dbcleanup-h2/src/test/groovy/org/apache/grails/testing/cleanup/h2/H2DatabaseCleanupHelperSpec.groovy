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

package org.apache.grails.testing.cleanup.h2

import java.sql.Connection
import java.sql.DatabaseMetaData

import javax.sql.DataSource

import spock.lang.Specification
import spock.lang.Unroll

class H2DatabaseCleanupHelperSpec extends Specification {

    def "resolveSchemaName returns schema from Connection.getSchema()"() {
        given:
        def connection = Mock(Connection) {
            getSchema() >> 'TESTDB'
        }
        def dataSource = Mock(DataSource) {
            getConnection() >> connection
        }

        when:
        String schema = H2DatabaseCleanupHelper.resolveSchemaName(dataSource)

        then:
        schema == 'TESTDB'

        and: 'connection is closed'
        1 * connection.close()
    }

    def "resolveSchemaName falls back to URL parsing when Connection.getSchema() returns null"() {
        given:
        def metaData = Mock(DatabaseMetaData) {
            getURL() >> 'jdbc:h2:mem:grailsDB'
        }
        def connection = Mock(Connection) {
            getSchema() >> null
            getMetaData() >> metaData
        }
        def dataSource = Mock(DataSource) {
            getConnection() >> connection
        }

        when:
        String schema = H2DatabaseCleanupHelper.resolveSchemaName(dataSource)

        then:
        schema == 'GRAILSDB'

        and: 'connection is closed'
        1 * connection.close()
    }

    def "resolveSchemaName returns null when connection fails"() {
        given:
        def dataSource = Mock(DataSource) {
            getConnection() >> { throw new RuntimeException('Connection failed') }
        }

        when:
        String schema = H2DatabaseCleanupHelper.resolveSchemaName(dataSource)

        then:
        schema == null
    }

    def "resolveSchemaName returns null when both schema and URL are null"() {
        given:
        def metaData = Mock(DatabaseMetaData) {
            getURL() >> null
        }
        def connection = Mock(Connection) {
            getSchema() >> null
            getMetaData() >> metaData
        }
        def dataSource = Mock(DataSource) {
            getConnection() >> connection
        }

        when:
        String schema = H2DatabaseCleanupHelper.resolveSchemaName(dataSource)

        then:
        schema == null
    }

    @Unroll
    def "extractSchemaFromUrl('#url') returns '#expected'"() {
        expect:
        H2DatabaseCleanupHelper.extractSchemaFromUrl(url) == expected

        where:
        url                                              || expected
        'jdbc:h2:mem:testDb'                             || 'TESTDB'
        'jdbc:h2:mem:grailsDB'                           || 'GRAILSDB'
        'jdbc:h2:mem:devDb'                              || 'DEVDB'
        'jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000'          || 'TESTDB'
        'jdbc:h2:mem:myDb;DB_CLOSE_DELAY=-1;MODE=MySQL'  || 'MYDB'
        'jdbc:h2:./data/mydb'                            || 'MYDB'
        'jdbc:h2:file:./data/mydb'                       || 'MYDB'
        'jdbc:h2:file:./data/mydb;AUTO_SERVER=TRUE'      || 'MYDB'
        'jdbc:h2:tcp://localhost/~/testdb'               || 'TESTDB'
        'jdbc:h2:ssl://localhost/~/testdb'               || 'TESTDB'
        null                                             || null
    }

    def "extractSchemaFromUrl returns null for non-H2 JDBC URLs"() {
        expect:
        H2DatabaseCleanupHelper.extractSchemaFromUrl('jdbc:mysql://localhost:3306/mydb') == null
        H2DatabaseCleanupHelper.extractSchemaFromUrl('jdbc:postgresql://localhost/mydb') == null
    }

    def "extractSchemaFromUrl returns null for empty H2 in-memory database name"() {
        expect:
        H2DatabaseCleanupHelper.extractSchemaFromUrl('jdbc:h2:mem:') == null
    }
}
