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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Helper utility for PostgreSQL database cleanup operations. Provides PostgreSQL-specific logic such as
 * resolving the current schema from a {@link DataSource} by inspecting JDBC connection metadata
 * and parsing PostgreSQL JDBC URLs.
 */
@Slf4j
@CompileStatic
class PostgresDatabaseCleanupHelper {

    /**
     * Resolves the current schema for the given PostgreSQL datasource by inspecting the JDBC connection metadata.
     * If the JDBC URL contains a `currentSchema` parameter, returns that schema.
     * Otherwise, returns the connection's current schema.
     *
     * @param dataSource the datasource to resolve the schema for
     * @return the schema name, or {@code null} if it cannot be determined
     */
    static String resolveCurrentSchema(DataSource dataSource) {
        Connection connection = null
        try {
            connection = dataSource.getConnection()
            String schema = connection.getSchema()
            if (schema) {
                log.debug('Resolved current schema from connection: {}', schema)
                return schema
            }

            // Fallback: try to get the schema from the database metadata URL
            DatabaseMetaData metaData = connection.getMetaData()
            String url = metaData.getURL()
            if (url) {
                schema = extractCurrentSchemaFromUrl(url)
                if (schema) {
                    log.debug('Resolved current schema from URL {}: {}', url, schema)
                    return schema
                }
            }
        }
        catch (Exception e) {
            log.warn('Failed to resolve current schema from datasource', e)
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
        null
    }

    /**
     * Extracts the current schema from a PostgreSQL JDBC URL by looking for the
     * {@code currentSchema} parameter. Returns null if the parameter is not set.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code jdbc:postgresql://localhost/testdb?currentSchema=myschema} &rarr; {@code myschema}</li>
     *   <li>{@code jdbc:postgresql://localhost/testdb} &rarr; {@code null}</li>
     *   <li>{@code jdbc:postgresql://localhost/testdb?currentSchema=myschema&other=value} &rarr; {@code myschema}</li>
     * </ul>
     *
     * @param url the JDBC URL
     * @return the current schema, or {@code null} if not set or URL format is not recognized
     */
    static String extractCurrentSchemaFromUrl(String url) {
        if (!url) {
            return null
        }

        // Look for currentSchema parameter in query string
        int questionIdx = url.indexOf('?')
        if (questionIdx < 0) {
            return null
        }

        String queryString = url.substring(questionIdx + 1)
        String[] params = queryString.split('&')
        for (String param : params) {
            if (param.startsWith('currentSchema=')) {
                String schema = param.substring('currentSchema='.length())
                // URL decode if needed (handle common cases)
                if (schema) {
                    // Remove any trailing parameters if present
                    int ampIdx = schema.indexOf('&')
                    if (ampIdx >= 0) {
                        schema = schema.substring(0, ampIdx)
                    }
                    return schema ?: null
                }
            }
        }

        null
    }
}
