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

import javax.sql.DataSource

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Helper utility for PostgreSQL database cleanup operations.
 * Provides PostgreSQL-specific logic such as resolving the
 * current schema from a {@link DataSource} by inspecting
 * JDBC connection metadata and parsing PostgreSQL JDBC URLs.
 */
@Slf4j
@CompileStatic
class PostgresDatabaseCleanupHelper {

    /**
     * Resolves the current schema for the given PostgreSQL datasource
     * by inspecting the JDBC connection metadata. If the JDBC URL
     * contains a `currentSchema` parameter, returns that schema.
     * Otherwise, returns the connection's current schema.
     *
     * @param dataSource the datasource to resolve the schema for
     * @return the schema name, or {@code null} if it cannot be determined
     */
    static String resolveCurrentSchema(DataSource dataSource) {
        try (def con = dataSource.connection) {
            def schema = con.getSchema()
            if (schema) {
                log.debug('Resolved current schema from connection: {}', schema)
                return schema
            }

            def url = con.metaData.URL
            if (url) {
                schema = extractCurrentSchemaFromUrl(url)
                log.debug('Resolved current schema from URL {}: {}', url, schema)
                return schema
            }
        }
        throw new IllegalStateException(
                'Because postgres defaults to the search_path ' +
                'when currentSchema is not defined, a schema ' +
                'should always be found'
        )
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

        int q = url.indexOf('?')
        if (q < 0) {
            return null
        }

        def query = url.substring(q + 1)
        for (def param : query.split('&')) {
            if (param.startsWith('currentSchema=')) {
                def schema = param.substring('currentSchema='.length())
                return schema ?: null
            }
        }
        return null
    }
}
