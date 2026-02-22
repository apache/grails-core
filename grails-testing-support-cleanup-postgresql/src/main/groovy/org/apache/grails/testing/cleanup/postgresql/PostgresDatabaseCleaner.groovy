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

import java.sql.Connection
import java.sql.DatabaseMetaData

import javax.sql.DataSource

import groovy.sql.GroovyResultSet
import groovy.sql.Sql
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

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
    private static final List<String> SYSTEM_SCHEMAS = ['pg_catalog', 'information_schema', 'pg_toast', 'pg_temp_1']

    @Override
    String databaseType() {
        DATABASE_TYPE
    }

    @Override
    boolean supports(DataSource dataSource) {
        Connection connection = null
        try {
            connection = dataSource.getConnection()
            DatabaseMetaData metaData = connection.getMetaData()
            String url = metaData.getURL()
            return url && url.startsWith('jdbc:postgresql:')
        }
        catch (Exception e) {
            log.debug('Could not determine if datasource is PostgreSQL', e)
            return false
        }
        finally {
            if (connection) {
                try {
                    connection.close()
                }
                catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    @SuppressWarnings('SqlNoDataSourceInspection')
    @Override
    DatabaseCleanupStats cleanup(ApplicationContext applicationContext, DataSource dataSource) {
        DatabaseCleanupStats stats = new DatabaseCleanupStats()
        stats.start()

        Sql sql = null
        try {
            sql = new Sql(dataSource)

            // Disable all triggers and referential integrity checks for this session
            // This is more efficient than using CASCADE on each truncate
            sql.execute('SET session_replication_role = replica')

            String currentSchema = PostgresDatabaseCleanupHelper.resolveCurrentSchema(dataSource)

            if (currentSchema) {
                log.debug('Cleaning schema: {}', currentSchema)
                cleanupSchema(sql, currentSchema, stats)
            }
            else {
                log.debug('No currentSchema parameter found, cleaning all non-system schemas')
                cleanupNonSystemSchemas(sql, stats)
            }

            cleanupCacheLayer(applicationContext)
        }
        catch (Exception e) {
            log.warn('Error during database cleanup', e)
        }
        finally {
            if (sql) {
                try {
                    // Re-enable triggers and referential integrity checks
                    sql.execute('SET session_replication_role = DEFAULT')
                    sql.close()
                }
                catch (e) {
                    log.error('Error closing SQL connection after cleanup', e)
                }
            }
            stats.stop()
        }

        stats
    }

    @SuppressWarnings('SqlNoDataSourceInspection')
    private void cleanupSchema(Sql sql, String schemaName, DatabaseCleanupStats stats) {
        String query = """
            SELECT tablename FROM pg_tables
            WHERE schemaname = '${schemaName}'
            AND tablename NOT LIKE 'pg_%'
        """ as String

        sql.eachRow(query) { GroovyResultSet row ->
            String tableName = row['tablename'] as String
            truncateTable(sql, schemaName, tableName, stats)
        }
    }

    @SuppressWarnings('SqlNoDataSourceInspection')
    private void cleanupNonSystemSchemas(Sql sql, DatabaseCleanupStats stats) {
        String query = """
            SELECT schemaname FROM pg_namespace
            WHERE schemaname NOT IN (${SYSTEM_SCHEMAS.collect { "'$it'" }.join(',')})
        """ as String

        sql.eachRow(query) { GroovyResultSet row ->
            String schemaName = row['schemaname'] as String
            cleanupSchema(sql, schemaName, stats)
        }
    }

    @SuppressWarnings('SqlNoDataSourceInspection')
    private void truncateTable(Sql sql, String schemaName, String tableName, DatabaseCleanupStats stats) {
        String qualifiedTableName = "\"${schemaName}\".\"${tableName}\""
        try {
            // Get row count before truncate
            String countQuery = "SELECT COUNT(*) AS cnt FROM ${qualifiedTableName}" as String
            Long rowCount = sql.firstRow(countQuery)?.cnt as Long ?: 0L

            if (rowCount > 0) {
                log.debug('Truncating table: {}', qualifiedTableName)
                // Since session_replication_role is set to 'replica', foreign keys are effectively disabled
                String truncateQuery = "TRUNCATE TABLE ${qualifiedTableName}" as String
                sql.execute(truncateQuery)
                stats.tableRowCounts[tableName] += rowCount
            }
        }
        catch (Exception e) {
            log.warn('Failed to truncate table {}', qualifiedTableName, e)
        }
    }

    @CompileDynamic
    private void cleanupCacheLayer(ApplicationContext applicationContext) {
        // Clear the 2nd layer cache if it exists
        if (ClassUtils.isPresent('org.hibernate.SessionFactory', this.class.classLoader)) {
            def sessionFactory = applicationContext.getBean('sessionFactory', Class.forName('org.hibernate.SessionFactory'))
            sessionFactory?.cache?.evictAllRegions()
        }
    }
}
