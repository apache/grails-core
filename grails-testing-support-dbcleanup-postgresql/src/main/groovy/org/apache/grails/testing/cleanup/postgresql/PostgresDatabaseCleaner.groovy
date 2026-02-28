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
package org.apache.grails.testing.cleanup.postgresql

import javax.sql.DataSource

import groovy.sql.Sql
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.context.ApplicationContext

import org.apache.grails.testing.cleanup.core.DatabaseCleaner
import org.apache.grails.testing.cleanup.core.DatabaseCleanupStats

/**
 * {@link DatabaseCleaner} implementation for PostgreSQL databases. Truncates all tables in the
 * current schema (or all non-system schemas if no currentSchema is set) and optionally evicts the
 * Hibernate second-level cache.
 *
 * <p>This cleaner identifies PostgreSQL databases by inspecting the JDBC URL of the datasource's
 * connection metadata. It supports both local and remote PostgreSQL instances.</p>
 *
 * <p>Schema cleanup behavior:
 * <ul>
 *   <li>If the JDBC URL contains a {@code currentSchema} parameter, only that schema is cleaned</li>
 *   <li>If no {@code currentSchema} is set, all non-system schemas are cleaned (excluding
 *       {@code pg_catalog}, {@code information_schema}, and {@code pg_toast})</li>
 * </ul>
 * </p>
 */
@Slf4j
@CompileStatic
class PostgresDatabaseCleaner implements DatabaseCleaner {

    private static final String DATABASE_TYPE = 'postgresql'
    private static final String QUERY = '''
            SELECT n.nspname AS schemaname, 
                c.relname AS table_name,
                c.reltuples::bigint AS row_count_estimate
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind = 'r'
            AND n.nspname = ?
            ORDER BY row_count_estimate DESC;
        '''

    @Override
    String databaseType() {
        DATABASE_TYPE
    }

    @Override
    boolean supports(DataSource dataSource) {
        try (def con = dataSource.connection) {
            return con.metaData?.URL?.startsWith('jdbc:postgresql:')
        } catch (Exception e) {
            log.debug('Could not determine if datasource is PostgreSQL', e)
            return false
        }
    }

    @Override
    @SuppressWarnings('SqlNoDataSourceInspection')
    DatabaseCleanupStats cleanup(ApplicationContext applicationContext, DataSource dataSource) {

        def stats = new DatabaseCleanupStats().tap { start() }
        def sql = new Sql(dataSource)
        try {
            // Disable all triggers and referential integrity checks for this session
            // This is more efficient than using CASCADE on each truncate
            sql.execute('SET session_replication_role = replica')
            def currentSchema = PostgresDatabaseCleanupHelper.resolveCurrentSchema(dataSource)
            log.debug('Cleaning schema: {}', currentSchema)
            cleanupSchema(sql, currentSchema, stats)
            cleanupCacheLayer(applicationContext)
        }
        finally {
            try {
                // Re-enable triggers and referential integrity checks
                sql.execute('SET session_replication_role = DEFAULT')
                sql.close()
            }
            catch (e) {
                log.error('Error closing SQL connection after cleanup', e)
            }
            stats.stop()
        }
        stats
    }

    @SuppressWarnings('SqlNoDataSourceInspection')
    private static void cleanupSchema(Sql sql, String schemaName, DatabaseCleanupStats stats) {
        def tablesInSchema = [] as List<String>
        sql.eachRow(QUERY, [schemaName] as List<Object>) { row ->
            def tableName = row.getString('table_name')
            tablesInSchema << tableName
            def rowCount = row.getLong('row_count_estimate')
            if (stats.detailedStatCollection) {
                if (rowCount == -1) {
                    def countQuery = "SELECT COUNT(*) AS cnt FROM $tableName" as String
                    rowCount = sql.firstRow(countQuery)?.cnt as Long ?: 0L
                }
            }
            stats.addTableRowCount(tableName, rowCount)
        }

        if (tablesInSchema) {
            log.debug('Truncating tables: {}', tablesInSchema.join(', '))
            def tables = tablesInSchema.collect { "\"$it\"" }.join(',')
            sql.executeUpdate(
                    "TRUNCATE TABLE $tables RESTART IDENTITY CASCADE" as String
            )
        }
    }
}
