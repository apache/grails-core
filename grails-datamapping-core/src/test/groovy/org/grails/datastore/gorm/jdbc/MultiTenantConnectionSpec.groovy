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
package org.grails.datastore.gorm.jdbc

import java.sql.Connection

import spock.lang.Specification

import org.grails.datastore.gorm.jdbc.schema.SchemaHandler

/**
 * Unit tests for {@link MultiTenantConnection}.
 *
 * Verifies the default schema is restored before a pooled connection is released back to the
 * pool, preventing cross-tenant schema leakage on connection reuse in SCHEMA-per-tenant
 * multi-tenancy mode.
 */
class MultiTenantConnectionSpec extends Specification {

    void "close restores the default schema before closing the target connection"() {
        given:
        Connection target = Mock(Connection) { isClosed() >> false }
        SchemaHandler schemaHandler = Mock(SchemaHandler)
        MultiTenantConnection connection = new MultiTenantConnection(target, schemaHandler)

        when:
        connection.close()

        then: "the default schema is restored before the underlying connection is closed"
        1 * schemaHandler.useDefaultSchema(connection)

        then:
        1 * target.close()
    }

    void "close does not attempt to restore the schema when the connection is already closed"() {
        given:
        Connection target = Mock(Connection) { isClosed() >> true }
        SchemaHandler schemaHandler = Mock(SchemaHandler)
        MultiTenantConnection connection = new MultiTenantConnection(target, schemaHandler)

        when:
        connection.close()

        then:
        0 * schemaHandler.useDefaultSchema(_)
        1 * target.close()
    }

    void "close still closes the target connection even if restoring the default schema fails"() {
        given:
        Connection target = Mock(Connection) { isClosed() >> false }
        SchemaHandler schemaHandler = Mock(SchemaHandler) {
            useDefaultSchema(_) >> { throw new RuntimeException('boom') }
        }
        MultiTenantConnection connection = new MultiTenantConnection(target, schemaHandler)

        when:
        connection.close()

        then:
        thrown(RuntimeException)
        1 * target.close()
    }
}
