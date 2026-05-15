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

package org.grails.datastore.gorm.jdbc.schema

import java.sql.Connection
import java.sql.ResultSet

import javax.sql.DataSource

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Resolves the schema names
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
@Slf4j
class DefaultSchemaHandler implements SchemaHandler {

    final String useSchemaStatement
    final String createSchemaStatement
    final String defaultSchemaName

    DefaultSchemaHandler() {
        useSchemaStatement = 'SET SCHEMA %s'
        createSchemaStatement = 'CREATE SCHEMA %s'
        defaultSchemaName = 'PUBLIC'
    }

    DefaultSchemaHandler(String useSchemaStatement, String createSchemaStatement, String defaultSchemaName) {
        this.useSchemaStatement = useSchemaStatement
        this.createSchemaStatement = createSchemaStatement
        this.defaultSchemaName = defaultSchemaName
    }

    @Override
    void useSchema(Connection connection, String name) {
        String useStatement = String.format(useSchemaStatement, quoteName(connection, name))
        System.err.println "Executing SQL: ${useStatement}"
        log.debug('Executing SQL Set Schema Statement: {}', useStatement)
        connection
                .createStatement()
                .execute(useStatement)
    }

    @Override
    void useDefaultSchema(Connection connection) {
        System.err.println "Executing SQL: useDefaultSchema (${defaultSchemaName})"
        useSchema(connection, defaultSchemaName)
    }

    @Override
    void createSchema(Connection connection, String name) {
        String schemaCreateStatement = String.format(createSchemaStatement, quoteName(connection, name))
        System.err.println "Executing SQL: ${schemaCreateStatement}"
        log.debug('Executing SQL Create Schema Statement: {}', schemaCreateStatement)
        connection
                .createStatement()
                .execute(schemaCreateStatement)
    }

    /**
     * Quotes a schema/catalog identifier using the JDBC-reported identifier quote character so
     * that schema names are never spliced as raw SQL tokens.  Any embedded occurrences of the
     * quote character itself are stripped from the name to prevent escaping the enclosure.
     * <p>
     * If the driver reports {@code ' '} (space) as the quote string — meaning identifier quoting
     * is not supported — the name is returned as-is (preserving existing behaviour).
     */
    protected static String quoteName(Connection connection, String name) {
        String q = connection.metaData.identifierQuoteString
        if (q == null || q.trim().isEmpty()) {
            return name
        }
        // Remove every occurrence of the quote char inside the name to prevent breakout
        String sanitized = name.replace(q, '')
        return "${q}${sanitized}${q}"
    }

    @Override
    Collection<String> resolveSchemaNames(DataSource dataSource) {
        Collection<String> schemaNames = []
        Connection connection = null
        try {
            connection = dataSource.getConnection()
            ResultSet schemas = connection.getMetaData().getSchemas()
            while (schemas.next()) {
                schemaNames.add(schemas.getString('TABLE_SCHEM'))
            }
        } finally {
            try {
                connection?.close()
            } catch (Throwable e) {
                log.debug("Error closing SQL connection: $e.message", e)
            }
        }
        return schemaNames
    }
}
