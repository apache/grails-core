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
package org.grails.orm.hibernate

import java.sql.Connection
import javax.sql.DataSource

import spock.lang.Specification
import spock.lang.Subject

import org.grails.datastore.gorm.jdbc.MultiTenantConnection
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler

class SchemaTenantDataSourceSpec extends Specification {

    static final String SCHEMA = 'tenant_schema'

    DataSource targetDataSource = Mock()
    Connection rawConnection = Mock()
    SchemaHandler schemaHandler = Mock()

    @Subject
    SchemaTenantDataSource dataSource = new SchemaTenantDataSource(targetDataSource, SCHEMA, schemaHandler)

    def 'getConnection() switches to the tenant schema and returns a MultiTenantConnection'() {
        given:
        targetDataSource.getConnection() >> rawConnection

        when:
        Connection result = dataSource.getConnection()

        then:
        1 * schemaHandler.useSchema(rawConnection, SCHEMA)
        result instanceof MultiTenantConnection
        (result as MultiTenantConnection).target == rawConnection
        (result as MultiTenantConnection).schemaHandler == schemaHandler
    }

    def 'getConnection(username, password) switches to the tenant schema and returns a MultiTenantConnection'() {
        given:
        targetDataSource.getConnection('user', 'pass') >> rawConnection

        when:
        Connection result = dataSource.getConnection('user', 'pass')

        then:
        1 * schemaHandler.useSchema(rawConnection, SCHEMA)
        result instanceof MultiTenantConnection
        (result as MultiTenantConnection).target == rawConnection
        (result as MultiTenantConnection).schemaHandler == schemaHandler
    }

    def 'tenantId is stored correctly'() {
        expect:
        dataSource.tenantId == SCHEMA
    }

    def 'target DataSource is stored correctly'() {
        expect:
        dataSource.target == targetDataSource
    }
}
