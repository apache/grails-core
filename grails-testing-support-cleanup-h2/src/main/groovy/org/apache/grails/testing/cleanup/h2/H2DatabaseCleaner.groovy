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
 * {@link DatabaseCleaner} implementation for H2 databases. Truncates all tables in the
 * resolved schema and optionally evicts the Hibernate second-level cache.
 *
 * <p>This cleaner identifies H2 databases by inspecting the JDBC URL of the datasource's
 * connection metadata. It supports both in-memory ({@code jdbc:h2:mem:}) and file-based
 * ({@code jdbc:h2:}) H2 databases.</p>
 */
@Slf4j
@CompileStatic
class H2DatabaseCleaner implements DatabaseCleaner {

    private static final String DATABASE_TYPE = 'h2'

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
            return url && url.startsWith('jdbc:h2:')
        }
        catch (Exception e) {
            log.debug('Could not determine if datasource is H2', e)
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

        String schemaName = H2DatabaseCleanupHelper.resolveSchemaName(dataSource)
        if (!schemaName) {
            log.warn('Could not resolve schema name for datasource, skipping cleanup')
            stats.stop()
            return stats
        }

        Sql sql = new Sql(dataSource)
        try {
            sql.execute('SET REFERENTIAL_INTEGRITY FALSE')
            String query = "SELECT table_name, row_count_estimate FROM information_schema.tables WHERE table_schema = '${schemaName}' AND row_count_estimate > 0" as String
            sql.eachRow(query) { GroovyResultSet row ->
                String tableName = row['table_name'] as String
                log.debug('Truncating table: {}', tableName)
                stats.tableRowCounts[tableName] += row['row_count_estimate'] as Long
                sql.executeUpdate("TRUNCATE TABLE \"${tableName}\"" as String)
            }

            cleanupCacheLayer(applicationContext)
        }
        finally {
            try {
                sql.execute('SET REFERENTIAL_INTEGRITY TRUE')
                sql.close()
            }
            catch (e) {
                log.error('Error cleaning up after cleaning up database', e)
            }
            stats.stop()
        }

        stats
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
