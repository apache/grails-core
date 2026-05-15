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

package org.apache.grails.testing.cleanup.core

import spock.lang.Specification

class DatabaseCleanupStatsSpec extends Specification {

    def 'tableRowCounts defaults to empty map'() {
        when:
        def stats = new DatabaseCleanupStats()

        then:
        stats.tableRowCounts != null
        stats.tableRowCounts.isEmpty()
    }

    def 'tableRowCounts default value is 0L for unknown keys'() {
        given:
        def stats = new DatabaseCleanupStats()

        when:
        Long count = stats.tableRowCounts['NONEXISTENT_TABLE']

        then:
        count == 0L
    }

    def 'tableRowCounts accumulates row counts'() {
        given:
        def stats = new DatabaseCleanupStats()

        when:
        stats.tableRowCounts['BOOK'] += 5L
        stats.tableRowCounts['AUTHOR'] += 3L
        stats.tableRowCounts['BOOK'] += 2L

        then:
        stats.tableRowCounts['BOOK'] == 7L
        stats.tableRowCounts['AUTHOR'] == 3L
    }

    def 'datasourceName defaults to null'() {
        when:
        def stats = new DatabaseCleanupStats()

        then:
        stats.datasourceName == null
    }

    def 'datasourceName can be set'() {
        given:
        def stats = new DatabaseCleanupStats()

        when:
        stats.datasourceName = 'dataSource'

        then:
        stats.datasourceName == 'dataSource'
    }

    def 'isDebugEnabled returns false when system property is not set'() {
        expect:
        !DatabaseCleanupStats.debugEnabled
    }

    def 'isDebugEnabled returns true when system property is set'() {
        setup:
        System.setProperty(DatabaseCleanupStats.DEBUG_PROPERTY, 'true')

        expect:
        DatabaseCleanupStats.debugEnabled

        cleanup:
        System.clearProperty(DatabaseCleanupStats.DEBUG_PROPERTY)
    }

    def 'isDebugEnabled returns false for non-true values'() {
        setup:
        System.setProperty(DatabaseCleanupStats.DEBUG_PROPERTY, 'yes')

        expect:
        !DatabaseCleanupStats.debugEnabled

        cleanup:
        System.clearProperty(DatabaseCleanupStats.DEBUG_PROPERTY)
    }

    def 'toFormattedReport includes datasource name when set'() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.datasourceName = 'dataSource'
        stats.tableRowCounts['BOOK'] += 5L
        stats.tableRowCounts['AUTHOR'] += 3L

        when:
        String report = stats.toFormattedReport()

        then:
        report.contains('Database Cleanup Stats (datasource: dataSource)')
        report.contains('Table Name')
        report.contains('Row Count')
        report.contains('BOOK')
        report.contains('5')
        report.contains('AUTHOR')
        report.contains('3')
    }

    def 'toFormattedReport omits datasource name when null'() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.tableRowCounts['BOOK'] += 2L

        when:
        String report = stats.toFormattedReport()

        then:
        report.contains('Database Cleanup Stats')
        !report.contains('datasource:')
        report.contains('BOOK')
    }

    def "toFormattedReport shows 'No tables' message when empty"() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.datasourceName = 'dataSource'

        when:
        String report = stats.toFormattedReport()

        then:
        report.contains('No tables were truncated.')
        !report.contains('Table Name')
    }

    def 'toFormattedReport uses separator lines'() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.datasourceName = 'dataSource'
        stats.tableRowCounts['BOOK'] += 1L

        when:
        String report = stats.toFormattedReport()
        def lines = report.split('\n')

        then: 'starts and ends with separator'
        lines[0] == '=========================================================='
        lines.last() == '=========================================================='
    }

    def 'startTimeMillis defaults to 0L'() {
        when:
        def stats = new DatabaseCleanupStats()

        then:
        stats.startTimeMillis == 0L
    }

    def 'endTimeMillis defaults to 0L'() {
        when:
        def stats = new DatabaseCleanupStats()

        then:
        stats.endTimeMillis == 0L
    }

    def 'start() records current time'() {
        given:
        def stats = new DatabaseCleanupStats()
        def beforeStart = System.currentTimeMillis()

        when:
        stats.start()
        def afterStart = System.currentTimeMillis()

        then:
        stats.startTimeMillis >= beforeStart
        stats.startTimeMillis <= afterStart
    }

    def 'stop() records current time'() {
        given:
        def stats = new DatabaseCleanupStats()
        def beforeStop = System.currentTimeMillis()

        when:
        stats.stop()
        def afterStop = System.currentTimeMillis()

        then:
        stats.endTimeMillis >= beforeStop
        stats.endTimeMillis <= afterStop
    }

    def 'durationMillis returns 0 when times are not set'() {
        when:
        def stats = new DatabaseCleanupStats()

        then:
        stats.durationMillis == 0L
    }

    def 'durationMillis calculates difference when both times are set'() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.startTimeMillis = 1000L
        stats.endTimeMillis = 1500L

        when:
        long duration = stats.durationMillis

        then:
        duration == 500L
    }

    def 'toFormattedReport includes timing information when times are set'() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.datasourceName = 'dataSource'
        stats.tableRowCounts['BOOK'] += 1L
        stats.startTimeMillis = 1708516496789L  // Some timestamp
        stats.endTimeMillis = 1708516496999L   // 210ms later

        when:
        String report = stats.toFormattedReport()

        then:
        report.contains('Start Time:')
        report.contains('End Time:')
        report.contains('Duration:')
        report.contains('210 ms')  // Duration should be 210ms
    }

    def 'toFormattedReport omits timing when times are not set'() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.datasourceName = 'dataSource'
        stats.tableRowCounts['BOOK'] += 1L

        when:
        String report = stats.toFormattedReport()

        then:
        !report.contains('Start Time:')
        !report.contains('End Time:')
        !report.contains('Duration:')
    }

    def 'toFormattedReport includes only start time if only startTimeMillis is set'() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.datasourceName = 'dataSource'
        stats.startTimeMillis = 1708516496789L
        stats.endTimeMillis = 0L

        when:
        String report = stats.toFormattedReport()

        then:
        report.contains('Start Time:')
        !report.contains('End Time:')
        !report.contains('Duration:')
    }

    def 'toFormattedReport includes only end time if only endTimeMillis is set'() {
        given:
        def stats = new DatabaseCleanupStats()
        stats.datasourceName = 'dataSource'
        stats.startTimeMillis = 0L
        stats.endTimeMillis = 1708516496999L

        when:
        String report = stats.toFormattedReport()

        then:
        !report.contains('Start Time:')
        report.contains('End Time:')
        !report.contains('Duration:')
    }
}
