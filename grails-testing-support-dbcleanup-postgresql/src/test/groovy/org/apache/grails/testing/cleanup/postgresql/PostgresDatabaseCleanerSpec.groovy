/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.grails.testing.cleanup.postgresql

import spock.util.environment.RestoreSystemProperties

import org.springframework.context.ApplicationContext

import spock.lang.Specification

@RestoreSystemProperties
class PostgresDatabaseCleanerSpec extends Specification {

    def setupSpec() {
        // Enable detailed stat collection for unit tests
        System.setProperty('grails.testing.cleanup.debug', 'true')
    }

    def "databaseType returns 'postgresql'"() {
        given:
        def cleaner = new PostgresDatabaseCleaner()

        expect:
        cleaner.databaseType() == 'postgresql'
    }

    def 'supports returns true for PostgreSQL datasource'() {
        given:
        def cleaner = new PostgresDatabaseCleaner()
        def postgresDataSource = Mock(javax.sql.DataSource) {
            getConnection() >> Mock(java.sql.Connection) {
                getMetaData() >> Mock(java.sql.DatabaseMetaData) {
                    getURL() >> 'jdbc:postgresql://localhost/testdb'
                }
            }
        }

        expect:
        cleaner.supports(postgresDataSource)
    }

    def 'supports returns true for PostgreSQL datasource with port'() {
        given:
        def cleaner = new PostgresDatabaseCleaner()
        def postgresDataSource = Mock(javax.sql.DataSource) {
            getConnection() >> Mock(java.sql.Connection) {
                getMetaData() >> Mock(java.sql.DatabaseMetaData) {
                    getURL() >> 'jdbc:postgresql://localhost:5432/mydb'
                }
            }
        }

        expect:
        cleaner.supports(postgresDataSource)
    }

    def 'supports returns false for non-PostgreSQL datasource'() {
        given:
        def cleaner = new PostgresDatabaseCleaner()
        def mysqlDataSource = Mock(javax.sql.DataSource) {
            getConnection() >> Mock(java.sql.Connection) {
                getMetaData() >> Mock(java.sql.DatabaseMetaData) {
                    getURL() >> 'jdbc:mysql://localhost/testdb'
                }
            }
        }

        expect:
        !cleaner.supports(mysqlDataSource)
    }

    def 'supports returns false when connection fails'() {
        given:
        def cleaner = new PostgresDatabaseCleaner()
        def badDataSource = Mock(javax.sql.DataSource) {
            getConnection() >> { throw new RuntimeException('Cannot connect') }
        }

        expect:
        !cleaner.supports(badDataSource)
    }

    def 'cleanup errors when schema cannot be resolved'() {
        given:
        def applicationContext = Stub(ApplicationContext)
        def cleaner = new PostgresDatabaseCleaner()
        def badDataSource = Mock(javax.sql.DataSource) {
            getConnection() >> { throw new RuntimeException('Cannot connect') }
        }

        when:
        def stats = cleaner.cleanup(applicationContext, badDataSource)

        then:
        thrown(RuntimeException)
    }
}
