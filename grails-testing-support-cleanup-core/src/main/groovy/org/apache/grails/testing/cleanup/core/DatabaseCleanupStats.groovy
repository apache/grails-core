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

import groovy.transform.CompileStatic
import java.text.SimpleDateFormat

/**
 * Captures statistics from a single datasource cleanup operation, including which
 * tables were truncated and how many rows were in each table before truncation.
 *
 * <p>The {@link #datasourceName} field identifies which datasource these stats belong to.
 * The {@link #tableRowCounts} map tracks per-table row counts using a default of {@code 0L}
 * for unknown keys, enabling convenient {@code +=} accumulation.</p>
 *
 * <p>Individual cleanup timing can be tracked using the {@link #start()} and {@link #stop()}
 * methods. These capture the start and end times for schema/datasource purging.</p>
 *
 * <p>When debug output is enabled via the {@code grails.testing.cleanup.debug} system property,
 * the {@link #toFormattedReport()} method produces a human-readable summary suitable for
 * printing to the console.</p>
 */
@CompileStatic
class DatabaseCleanupStats {

    /**
     * System property that enables debug output of cleanup statistics to stdout.
     * Set to {@code "true"} to enable: {@code -Dgrails.testing.cleanup.debug=true}
     */
    static final String DEBUG_PROPERTY = 'grails.testing.cleanup.debug'

    /**
     * The name of the datasource bean that was cleaned up (e.g., "dataSource").
     * May be {@code null} if not set by the caller.
     */
    String datasourceName

    /**
     * Map of table names to the number of rows that existed before truncation.
     * Uses a default value of {@code 0L} for unknown keys, enabling {@code +=} accumulation.
     */
    Map<String, Long> tableRowCounts = [:].withDefault { 0L }

    /**
     * The start time of the cleanup operation in milliseconds since epoch.
     * Set by calling {@link #start()} before cleaning begins.
     */
    long startTimeMillis = 0L

    /**
     * The end time of the cleanup operation in milliseconds since epoch.
     * Set by calling {@link #stop()} after cleaning completes.
     */
    long endTimeMillis = 0L

    /**
     * Starts the timer for individual schema/datasource cleanup.
     * Records the current time as the start time.
     */
    void start() {
        this.startTimeMillis = System.currentTimeMillis()
    }

    /**
     * Stops the timer for individual schema/datasource cleanup.
     * Records the current time as the end time.
     */
    void stop() {
        this.endTimeMillis = System.currentTimeMillis()
    }

    /**
     * Returns {@code true} if the {@code grails.testing.cleanup.debug} system property
     * is set to {@code "true"}.
     */
    static boolean isDebugEnabled() {
        Boolean.getBoolean(DEBUG_PROPERTY)
    }

    /**
     * Calculates the duration of the cleanup operation in milliseconds.
     *
     * @return the duration in milliseconds, or 0 if times were not set
     */
    long getDurationMillis() {
        if (startTimeMillis > 0L && endTimeMillis > 0L) {
            endTimeMillis - startTimeMillis
        }
        else {
            0L
        }
    }

    /**
     * Returns a human-readable formatted report of the cleanup statistics.
     *
     * <p>Example output:</p>
     * <pre>
     * ==========================================================
     * Database Cleanup Stats (datasource: dataSource)
     * Start Time: 2024-02-21T12:34:56.789Z
     * End Time:   2024-02-21T12:34:57.123Z
     * Duration:   334 ms
     * ==========================================================
     * Table Name                          | Row Count
     * ----------------------------------------------------------
     * BOOK                                | 5
     * AUTHOR                              | 3
     * ==========================================================
     * </pre>
     *
     * @return the formatted report string
     */
    String toFormattedReport() {
        String separator = '=========================================================='
        String divider = '----------------------------------------------------------'

        StringBuilder sb = new StringBuilder()
        sb.append(separator).append('\n')
        if (datasourceName) {
            sb.append("Database Cleanup Stats (datasource: ${datasourceName})").append('\n')
        }
        else {
            sb.append('Database Cleanup Stats').append('\n')
        }

        // Add timing information if available
        if (startTimeMillis > 0L || endTimeMillis > 0L) {
            if (startTimeMillis > 0L) {
                sb.append("Start Time: ${formatTime(startTimeMillis)}").append('\n')
            }
            if (endTimeMillis > 0L) {
                sb.append("End Time:   ${formatTime(endTimeMillis)}").append('\n')
            }
            if (startTimeMillis > 0L && endTimeMillis > 0L) {
                sb.append("Duration:   ${durationMillis} ms").append('\n')
            }
        }

        sb.append(separator).append('\n')

        if (tableRowCounts.isEmpty()) {
            sb.append('No tables were truncated.').append('\n')
        }
        else {
            sb.append(String.format('%-36s| %s', 'Table Name', 'Row Count')).append('\n')
            sb.append(divider).append('\n')
            tableRowCounts.each { String name, Long count ->
                sb.append(String.format('%-36s| %d', name, count)).append('\n')
            }
        }

        sb.append(separator).append('\n')
        sb.toString()
    }

    /**
     * Formats a timestamp in milliseconds as an ISO 8601 string.
     *
     * @param timeMillis the timestamp in milliseconds since epoch
     * @return the formatted time string
     */
    private static String formatTime(long timeMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat('yyyy-MM-dd\'T\'HH:mm:ss.SSS\'Z\'')
        sdf.setTimeZone(TimeZone.getTimeZone('UTC'))
        sdf.format(new Date(timeMillis))
    }
}
