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

import groovy.sql.Sql

import org.h2.jdbcx.JdbcDataSource

import spock.lang.AutoCleanup
import spock.lang.Specification

import org.springframework.context.ApplicationContext

import org.apache.grails.testing.cleanup.core.DatabaseCleanupStats

class H2DatabaseCleanerSpec extends Specification {

    @AutoCleanup('close')
    Sql sql

    JdbcDataSource dataSource

    ApplicationContext applicationContext

    def setupSpec() {
        // Enable detailed stat collection for unit tests
        System.setProperty('grails.testing.cleanup.debug', 'true')
    }

    def cleanupSpec() {
        // Clean up system property
        System.clearProperty('grails.testing.cleanup.debug')
    }

    def setup() {
        dataSource = new JdbcDataSource()
        dataSource.URL = 'jdbc:h2:mem:testDb;DB_CLOSE_DELAY=-1'
        dataSource.user = 'sa'
        dataSource.password = ''

        sql = new Sql(dataSource)
        sql.execute('CREATE TABLE IF NOT EXISTS book (id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255))')
        sql.execute('CREATE TABLE IF NOT EXISTS author (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))')

        applicationContext = Stub(ApplicationContext)
    }

    def cleanup() {
        sql.execute('DROP TABLE IF EXISTS book')
        sql.execute('DROP TABLE IF EXISTS author')
    }

    def "databaseType returns 'h2'"() {
        given:
        def cleaner = new H2DatabaseCleaner()

        expect:
        cleaner.databaseType() == 'h2'
    }

    def "supports returns true for H2 datasource"() {
        given:
        def cleaner = new H2DatabaseCleaner()

        expect:
        cleaner.supports(dataSource)
    }

    def "supports returns false for non-H2 datasource"() {
        given:
        def cleaner = new H2DatabaseCleaner()
        def nonH2DataSource = Mock(javax.sql.DataSource) {
            getConnection() >> Mock(java.sql.Connection) {
                getMetaData() >> Mock(java.sql.DatabaseMetaData) {
                    getURL() >> 'jdbc:postgresql://localhost/testdb'
                }
            }
        }

        expect:
        !cleaner.supports(nonH2DataSource)
    }

    def "supports returns false when connection fails"() {
        given:
        def cleaner = new H2DatabaseCleaner()
        def badDataSource = Mock(javax.sql.DataSource) {
            getConnection() >> { throw new RuntimeException('Cannot connect') }
        }

        expect:
        !cleaner.supports(badDataSource)
    }

    def "cleanup truncates tables with data and returns stats"() {
        given:
        sql.execute("INSERT INTO book (title) VALUES ('The Definitive Guide to Grails')")
        sql.execute("INSERT INTO book (title) VALUES ('Grails in Action')")
        sql.execute("INSERT INTO author (name) VALUES ('Graeme Rocher')")

        def cleaner = new H2DatabaseCleaner()

        when:
        DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, dataSource)

        then: 'stats report the truncated rows'
        stats.tableRowCounts['BOOK'] == 2L
        stats.tableRowCounts['AUTHOR'] == 1L

        and: 'tables are now empty'
        sql.firstRow('SELECT COUNT(*) AS cnt FROM book').cnt == 0
        sql.firstRow('SELECT COUNT(*) AS cnt FROM author').cnt == 0
    }

    def "cleanup does not report tables with no data"() {
        given: 'tables exist but have no rows'
        def cleaner = new H2DatabaseCleaner()

        when:
        DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, dataSource)

        then:
        stats.tableRowCounts.isEmpty() || stats.tableRowCounts.values().every { it == 0L }
    }

    def "cleanup handles tables with reserved word names"() {
        given:
        sql.execute('CREATE TABLE IF NOT EXISTS "USER" (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))')
        sql.execute('INSERT INTO "USER" (name) VALUES (\'Test User\')')

        def cleaner = new H2DatabaseCleaner()

        when:
        DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, dataSource)

        then: 'table with reserved word name was truncated successfully'
        stats.tableRowCounts['USER'] == 1L

        and: 'table is now empty'
        sql.firstRow('SELECT COUNT(*) AS cnt FROM "USER"').cnt == 0

        cleanup:
        sql.execute('DROP TABLE IF EXISTS "USER"')
    }

    def "cleanup restores referential integrity after truncation"() {
        given:
        sql.execute('CREATE TABLE IF NOT EXISTS category (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))')
        sql.execute('CREATE TABLE IF NOT EXISTS item (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), category_id BIGINT, FOREIGN KEY (category_id) REFERENCES category(id))')
        sql.execute("INSERT INTO category (id, name) VALUES (1, 'Fiction')")
        sql.execute("INSERT INTO item (name, category_id) VALUES ('Book1', 1)")

        def cleaner = new H2DatabaseCleaner()

        when: 'cleanup runs (disables and re-enables referential integrity)'
        cleaner.cleanup(applicationContext, dataSource)

        then: 'referential integrity is restored - inserting without valid FK should fail'
        try {
            sql.execute("INSERT INTO item (name, category_id) VALUES ('Book2', 999)")
            assert false, 'Should have thrown exception due to referential integrity'
        }
        catch (Exception expected) {
            // Expected: referential integrity constraint violation
        }

        cleanup:
        sql.execute('DROP TABLE IF EXISTS item')
        sql.execute('DROP TABLE IF EXISTS category')
    }

    def "cleanup works with different database names"() {
        given:
        def customDataSource = new JdbcDataSource()
        customDataSource.URL = 'jdbc:h2:mem:grailsDB;DB_CLOSE_DELAY=-1'
        customDataSource.user = 'sa'
        customDataSource.password = ''

        def customSql = new Sql(customDataSource)
        customSql.execute('CREATE TABLE IF NOT EXISTS person (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))')
        customSql.execute("INSERT INTO person (name) VALUES ('Test Person')")

        def cleaner = new H2DatabaseCleaner()

        when:
        DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, customDataSource)

        then:
        stats.tableRowCounts['PERSON'] == 1L

        and: 'table is empty'
        customSql.firstRow('SELECT COUNT(*) AS cnt FROM person').cnt == 0

        cleanup:
        customSql.execute('DROP TABLE IF EXISTS person')
        customSql.close()
    }

    def "cleanup returns empty stats when schema cannot be resolved"() {
        given:
        def badDataSource = Mock(javax.sql.DataSource) {
            getConnection() >> { throw new RuntimeException('Cannot connect') }
        }
        def cleaner = new H2DatabaseCleaner()

        when:
        DatabaseCleanupStats stats = cleaner.cleanup(applicationContext, badDataSource)

        then:
        stats.tableRowCounts.isEmpty()
    }
}
