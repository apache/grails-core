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

package org.apache.grails.testing.cleanup.core

import javax.sql.DataSource

import spock.lang.Specification

import org.springframework.context.ApplicationContext

class DatabaseCleanupContextSpec extends Specification {

    def "constructor sets the cleaners list"() {
        given:
        def cleaner = createMockCleaner('h2')
        def cleaners = [cleaner]

        when:
        def context = new DatabaseCleanupContext(cleaners)

        then:
        context.cleanersByType.size() == 1
        context.cleanersByType.get('h2').is(cleaner)
    }

    def "applicationContext is null by default"() {
        given:
        def context = new DatabaseCleanupContext([createMockCleaner('h2')])

        expect:
        context.applicationContext == null
    }

    def "applicationContext can be set and retrieved"() {
        given:
        def context = new DatabaseCleanupContext([createMockCleaner('h2')])
        def appCtx = Mock(ApplicationContext)

        when:
        context.applicationContext = appCtx

        then:
        context.applicationContext.is(appCtx)
    }

    def "performCleanup throws when applicationContext is null"() {
        given:
        def context = new DatabaseCleanupContext([createMockCleaner('h2')])

        when:
        context.performCleanup(DatasourceCleanupMapping.parse(new String[0]))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('ApplicationContext is not available')
    }

    def "performCleanup throws when cleaners list is empty"() {
        given:
        def context = new DatabaseCleanupContext([])
        context.applicationContext = Mock(ApplicationContext)

        when:
        context.performCleanup(DatasourceCleanupMapping.parse(new String[0]))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('no DatabaseCleaner implementations found')
    }

    def "performCleanup with cleanAll calls matching cleaner for each DataSource bean"() {
        given:
        def stats1 = new DatabaseCleanupStats()
        stats1.tableRowCounts['BOOK'] = 5L

        def stats2 = new DatabaseCleanupStats()
        stats2.tableRowCounts['AUTHOR'] = 3L

        def dataSource1 = Mock(DataSource)
        def dataSource2 = Mock(DataSource)

        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource1) >> true
            supports(dataSource2) >> true
        }
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource1, 'dataSource_secondary': dataSource2]
        }

        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        when:
        List<DatabaseCleanupStats> result = context.performCleanup(DatasourceCleanupMapping.parse(new String[0]))

        then:
        1 * cleaner.cleanup(appCtx, dataSource1) >> stats1
        1 * cleaner.cleanup(appCtx, dataSource2) >> stats2

        and:
        result.size() == 2
        result.any { it.tableRowCounts['BOOK'] == 5L }
        result.any { it.tableRowCounts['AUTHOR'] == 3L }
    }

    def "performCleanup filters by specified datasource names (auto-discover)"() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.tableRowCounts['BOOK'] = 5L

        def dataSource1 = Mock(DataSource)
        def dataSource2 = Mock(DataSource)

        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource1) >> true
        }
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource1, 'dataSource_secondary': dataSource2]
        }

        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        when:
        List<DatabaseCleanupStats> result = context.performCleanup(DatasourceCleanupMapping.parse(['dataSource'] as String[]))

        then:
        1 * cleaner.cleanup(appCtx, dataSource1) >> stats
        0 * cleaner.cleanup(appCtx, dataSource2)

        and:
        result.size() == 1
    }

    def "performCleanup with explicit type uses type-based lookup"() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.tableRowCounts['BOOK'] = 5L

        def dataSource = Mock(DataSource)

        def h2Cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }
        def pgCleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'postgresql'
        }
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }

        def context = new DatabaseCleanupContext([h2Cleaner, pgCleaner])
        context.applicationContext = appCtx

        when:
        List<DatabaseCleanupStats> result = context.performCleanup(DatasourceCleanupMapping.parse(['dataSource:h2'] as String[]))

        then:
        1 * h2Cleaner.cleanup(appCtx, dataSource) >> stats
        0 * pgCleaner.cleanup(_, _)

        and:
        result.size() == 1
    }

    def "performCleanup throws when specified datasource name is not found"() {
        given:
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': Mock(DataSource)]
        }

        def context = new DatabaseCleanupContext([createMockCleaner('h2')])
        context.applicationContext = appCtx

        when:
        context.performCleanup(DatasourceCleanupMapping.parse(['nonExistentDataSource'] as String[]))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('nonExistentDataSource')
        ex.message.contains('was not found')
    }

    def "performCleanup throws when no cleaner supports a datasource (cleanAll)"() {
        given:
        def dataSource = Mock(DataSource)
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> false
        }
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }

        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        when:
        context.performCleanup(DatasourceCleanupMapping.parse(new String[0]))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('No DatabaseCleaner implementation found')
        ex.message.contains('dataSource')
    }

    def "performCleanup throws when no cleaner supports a datasource (auto-discover entry)"() {
        given:
        def dataSource = Mock(DataSource)
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> false
        }
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }

        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        when:
        context.performCleanup(DatasourceCleanupMapping.parse(['dataSource'] as String[]))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('No DatabaseCleaner implementation found')
        ex.message.contains('dataSource')
    }

    def "performCleanup throws when explicit type has no matching cleaner"() {
        given:
        def dataSource = Mock(DataSource)
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }

        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        when:
        context.performCleanup(DatasourceCleanupMapping.parse(['dataSource:postgresql'] as String[]))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("No DatabaseCleaner found for database type 'postgresql'")
        ex.message.contains('dataSource')
    }

    def "performCleanup handles empty DataSource map"() {
        given:
        def cleaner = createMockCleaner('h2')
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> [:]
        }

        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        when:
        List<DatabaseCleanupStats> result = context.performCleanup(DatasourceCleanupMapping.parse(new String[0]))

        then:
        result.isEmpty()
    }

    def "performCleanup with mixed entries uses correct cleaner for each"() {
        given:
        def h2Stats = new DatabaseCleanupStats()
        h2Stats.tableRowCounts['BOOK'] = 5L

        def pgStats = new DatabaseCleanupStats()
        pgStats.tableRowCounts['AUTHOR'] = 3L

        def h2DataSource = Mock(DataSource)
        def pgDataSource = Mock(DataSource)

        def h2Cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }
        def pgCleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'postgresql'
            supports(pgDataSource) >> true
        }

        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': h2DataSource, 'dataSource_pg': pgDataSource]
        }

        def context = new DatabaseCleanupContext([h2Cleaner, pgCleaner])
        context.applicationContext = appCtx

        when: 'explicit type for h2, auto-discover for pg'
        List<DatabaseCleanupStats> result = context.performCleanup(
            DatasourceCleanupMapping.parse(['dataSource:h2', 'dataSource_pg'] as String[]))

        then:
        1 * h2Cleaner.cleanup(appCtx, h2DataSource) >> h2Stats
        1 * pgCleaner.cleanup(appCtx, pgDataSource) >> pgStats

        and:
        result.size() == 2
    }

    def "performCleanup sets datasourceName on stats for cleanAll"() {
        given:
        def dataSource = Mock(DataSource)
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> true
        }
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['myDataSource': dataSource]
        }

        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        when:
        List<DatabaseCleanupStats> result = context.performCleanup(DatasourceCleanupMapping.parse(new String[0]))

        then:
        1 * cleaner.cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()

        and:
        result.size() == 1
        result[0].datasourceName == 'myDataSource'
    }

    def "performCleanup sets datasourceName on stats for specific entries"() {
        given:
        def dataSource = Mock(DataSource)
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }

        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        when:
        List<DatabaseCleanupStats> result = context.performCleanup(
            DatasourceCleanupMapping.parse(['dataSource:h2'] as String[]))

        then:
        1 * cleaner.cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()

        and:
        result.size() == 1
        result[0].datasourceName == 'dataSource'
    }

    def "constructor throws IllegalStateException when cleaner returns null databaseType"() {
        given:
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> null
        }

        when:
        new DatabaseCleanupContext([cleaner])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('returned a null or empty databaseType()')
        ex.message.contains(cleaner.class.name)
    }

    def "constructor throws IllegalStateException when cleaner returns empty databaseType"() {
        given:
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> ''
        }

        when:
        new DatabaseCleanupContext([cleaner])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('returned a null or empty databaseType()')
    }

    def "constructor throws IllegalStateException when cleaner returns whitespace-only databaseType"() {
        given:
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> '   '
        }

        when:
        new DatabaseCleanupContext([cleaner])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('returned a null or empty databaseType()')
    }

    def "constructor throws IllegalStateException when multiple cleaners declare same databaseType"() {
        given:
        def cleaner1 = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }
        def cleaner2 = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }

        when:
        new DatabaseCleanupContext([cleaner1, cleaner2])

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Duplicate databaseType 'h2'")
        ex.message.contains('Each DatabaseCleaner must declare a unique databaseType')
        ex.message.contains(cleaner1.class.name)
        ex.message.contains(cleaner2.class.name)
    }

    def "constructor successfully creates map with valid unique cleaners"() {
        given:
        def h2Cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }
        def pgCleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'postgresql'
        }
        def mySqlCleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'mysql'
        }

        when:
        def context = new DatabaseCleanupContext([h2Cleaner, pgCleaner, mySqlCleaner])

        then:
        context.cleanersByType.size() == 3
        context.cleanersByType.get('h2').is(h2Cleaner)
        context.cleanersByType.get('postgresql').is(pgCleaner)
        context.cleanersByType.get('mysql').is(mySqlCleaner)
    }

    def "constructor returns unmodifiable map"() {
        given:
        def cleaner = createMockCleaner('h2')
        def context = new DatabaseCleanupContext([cleaner])

        when:
        context.cleanersByType.put('postgresql', createMockCleaner('postgresql'))

        then:
        thrown(UnsupportedOperationException)
    }

    private DatabaseCleaner createMockCleaner(String type) {
        Mock(DatabaseCleaner) {
            databaseType() >> type
        }
    }
}
