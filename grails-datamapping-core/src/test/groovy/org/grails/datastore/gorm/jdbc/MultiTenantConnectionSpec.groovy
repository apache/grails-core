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

import org.grails.datastore.gorm.jdbc.schema.SchemaHandler
import spock.lang.Specification

class MultiTenantConnectionSpec extends Specification {

    void "close restores the default schema before delegating to the target connection when open"() {
        given:
        def target = Mock(Connection) { isClosed() >> false }
        def schemaHandler = Mock(SchemaHandler)
        def connection = new MultiTenantConnection(target, schemaHandler)

        when:
        connection.close()

        then:
        1 * schemaHandler.useDefaultSchema(connection)
        1 * target.close()
    }

    void "close does not restore the default schema when the connection is already closed"() {
        given:
        def target = Mock(Connection) { isClosed() >> true }
        def schemaHandler = Mock(SchemaHandler)
        def connection = new MultiTenantConnection(target, schemaHandler)

        when:
        connection.close()

        then:
        0 * schemaHandler.useDefaultSchema(_)
        1 * target.close()
    }

    void "close still closes the target connection when restoring the schema throws"() {
        given:
        def target = Mock(Connection) { isClosed() >> false }
        def schemaHandler = Mock(SchemaHandler) {
            useDefaultSchema(_) >> { throw new RuntimeException('boom') }
        }
        def connection = new MultiTenantConnection(target, schemaHandler)

        when:
        connection.close()

        then:
        thrown(RuntimeException)
        1 * target.close()
    }

    void "other Connection methods delegate to the target connection"() {
        given:
        def target = Mock(Connection)
        def schemaHandler = Mock(SchemaHandler)
        def connection = new MultiTenantConnection(target, schemaHandler)

        when:
        connection.isReadOnly()

        then:
        1 * target.isReadOnly() >> true

        when:
        def autoCommit = connection.getAutoCommit()

        then:
        1 * target.getAutoCommit() >> false
        !autoCommit
    }

    void "target and schemaHandler are exposed"() {
        given:
        def target = Mock(Connection)
        def schemaHandler = Mock(SchemaHandler)

        when:
        def connection = new MultiTenantConnection(target, schemaHandler)

        then:
        connection.target.is(target)
        connection.schemaHandler.is(schemaHandler)
    }
}
