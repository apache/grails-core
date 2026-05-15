/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * 'License'); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.datastore.gorm.jdbc.schema

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Statement

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for {@link DefaultSchemaHandler}.
 *
 * Verifies that schema names are always quoted via JDBC identifier-quote characters before being
 * interpolated into DDL statements, preventing SQL injection through malicious tenant identifiers.
 */
class DefaultSchemaHandlerSpec extends Specification {

    // -------------------------------------------------------------------------
    // quoteName — unit tests (protected static helper)
    // -------------------------------------------------------------------------

    @Unroll
    void "quoteName wraps '#name' with quote char '#quote' → '#expected'"() {
        given:
        def meta = Mock(DatabaseMetaData) { getIdentifierQuoteString() >> quote }
        def conn = Mock(Connection)       { getMetaData() >> meta }

        expect:
        DefaultSchemaHandler.quoteName(conn, name) == expected

        where:
        name                        | quote | expected
        'myschema'                  | '"'   | '"myschema"'
        'MY_SCHEMA'                 | '"'   | '"MY_SCHEMA"'
        'tenant_1'                  | '"'   | '"tenant_1"'
        // injection attempt wrapped inside quotes — semicolon cannot start a new statement
        'public; DROP TABLE users'  | '"'   | '"public; DROP TABLE users"'
        // embedded quote chars are stripped before re-quoting to prevent breakout
        'bad"name'                  | '"'   | ''badname''
        // backtick quote (MySQL style)
        'myschema'                  | '`'   | '`myschema`'
        'bad`name'                  | '`'   | '`badname`'
        // quoting not supported (driver returns space)
        'myschema'                  | ' '   | 'myschema'
        'myschema'                  | null  | 'myschema'
        'myschema'                  | ''    | 'myschema'
    }

    // -------------------------------------------------------------------------
    // useSchema — quoted DDL is executed
    // -------------------------------------------------------------------------

    void 'useSchema executes SET SCHEMA with quoted name'() {
        given:
        def executedSql = []
        def statement   = Mock(Statement) { execute(_ as String) >> { String sql -> executedSql << sql; true } }
        def meta        = Mock(DatabaseMetaData) { getIdentifierQuoteString() >> '"' }
        def conn        = Mock(Connection) { getMetaData() >> meta; createStatement() >> statement }
        def handler     = new DefaultSchemaHandler()

        when:
        handler.useSchema(conn, 'myschema')

        then:
        executedSql == ['SET SCHEMA 'myschema'']
    }

    void 'useSchema wraps injection payload inside quotes so it cannot break out'() {
        given:
        def executedSql = []
        def statement   = Mock(Statement) { execute(_ as String) >> { String sql -> executedSql << sql; true } }
        def meta        = Mock(DatabaseMetaData) { getIdentifierQuoteString() >> '"' }
        def conn        = Mock(Connection) { getMetaData() >> meta; createStatement() >> statement }
        def handler     = new DefaultSchemaHandler()

        when:
        handler.useSchema(conn, 'public; DROP TABLE users--')

        then: 'dangerous payload is contained inside the identifier quotes'
        executedSql == ['SET SCHEMA 'public; DROP TABLE users--'']
    }

    void 'useSchema strips embedded quote chars before wrapping to prevent identifier breakout'() {
        given:
        def executedSql = []
        def statement   = Mock(Statement) { execute(_ as String) >> { String sql -> executedSql << sql; true } }
        def meta        = Mock(DatabaseMetaData) { getIdentifierQuoteString() >> '"' }
        def conn        = Mock(Connection) { getMetaData() >> meta; createStatement() >> statement }
        def handler     = new DefaultSchemaHandler()

        when:
        handler.useSchema(conn, 'bad"; DROP TABLE users; --')

        then: 'embedded quote is removed — breakout is impossible'
        executedSql == ['SET SCHEMA 'bad; DROP TABLE users; --'']
    }

    // -------------------------------------------------------------------------
    // createSchema — quoted DDL is executed
    // -------------------------------------------------------------------------

    void 'createSchema executes CREATE SCHEMA with quoted name'() {
        given:
        def executedSql = []
        def statement   = Mock(Statement) { execute(_ as String) >> { String sql -> executedSql << sql; true } }
        def meta        = Mock(DatabaseMetaData) { getIdentifierQuoteString() >> '"' }
        def conn        = Mock(Connection) { getMetaData() >> meta; createStatement() >> statement }
        def handler     = new DefaultSchemaHandler()

        when:
        handler.createSchema(conn, 'tenant_42')

        then:
        executedSql == ['CREATE SCHEMA 'tenant_42'']
    }

    void 'createSchema wraps injection payload inside quotes'() {
        given:
        def executedSql = []
        def statement   = Mock(Statement) { execute(_ as String) >> { String sql -> executedSql << sql; true } }
        def meta        = Mock(DatabaseMetaData) { getIdentifierQuoteString() >> '"' }
        def conn        = Mock(Connection) { getMetaData() >> meta; createStatement() >> statement }
        def handler     = new DefaultSchemaHandler()

        when:
        handler.createSchema(conn, 'tenant; DROP TABLE users--')

        then:
        executedSql == ['CREATE SCHEMA 'tenant; DROP TABLE users--'']
    }

    // -------------------------------------------------------------------------
    // useDefaultSchema
    // -------------------------------------------------------------------------

    void 'useDefaultSchema calls useSchema with the configured default name'() {
        given:
        def executedSql = []
        def statement   = Mock(Statement) { execute(_ as String) >> { String sql -> executedSql << sql; true } }
        def meta        = Mock(DatabaseMetaData) { getIdentifierQuoteString() >> '"' }
        def conn        = Mock(Connection) { getMetaData() >> meta; createStatement() >> statement }
        def handler     = new DefaultSchemaHandler()   // default is PUBLIC

        when:
        handler.useDefaultSchema(conn)

        then:
        executedSql == ['SET SCHEMA 'PUBLIC'']
    }

    // -------------------------------------------------------------------------
    // Custom statement templates
    // -------------------------------------------------------------------------

    void 'custom useSchemaStatement template is honoured with quoting'() {
        given:
        def executedSql = []
        def statement   = Mock(Statement) { execute(_ as String) >> { String sql -> executedSql << sql; true } }
        def meta        = Mock(DatabaseMetaData) { getIdentifierQuoteString() >> '`' }
        def conn        = Mock(Connection) { getMetaData() >> meta; createStatement() >> statement }
        def handler     = new DefaultSchemaHandler('USE %s', 'CREATE SCHEMA IF NOT EXISTS %s', 'main')

        when:
        handler.useSchema(conn, 'mydb')

        then:
        executedSql == ['USE `mydb`']
    }

    // -------------------------------------------------------------------------
    // Fall-through when quoting is unsupported
    // -------------------------------------------------------------------------

    void 'when driver reports quoting unsupported (space) the name is used unquoted'() {
        given:
        def executedSql = []
        def statement   = Mock(Statement) { execute(_ as String) >> { String sql -> executedSql << sql; true } }
        def meta        = Mock(DatabaseMetaData) { getIdentifierQuoteString() >> ' ' }
        def conn        = Mock(Connection) { getMetaData() >> meta; createStatement() >> statement }
        def handler     = new DefaultSchemaHandler()

        when:
        handler.useSchema(conn, 'plainschema')

        then:
        executedSql == ['SET SCHEMA plainschema']
    }
}
