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
package org.grails.orm.hibernate

import java.sql.Connection

import groovy.transform.CompileStatic
import javax.sql.DataSource
import org.grails.datastore.gorm.jdbc.MultiTenantConnection
import org.grails.datastore.gorm.jdbc.MultiTenantDataSource
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler

/**
 * A {@link MultiTenantDataSource} that switches to a specific schema on every connection
 * and wraps the returned connection in a {@link MultiTenantConnection} so that the schema
 * is restored when the connection is closed.
 */
@CompileStatic
class SchemaTenantDataSource extends MultiTenantDataSource {

    private final SchemaHandler schemaHandler

    SchemaTenantDataSource(DataSource target, String schemaName, SchemaHandler schemaHandler) {
        super(target, schemaName)
        this.schemaHandler = schemaHandler
    }

    @Override
    Connection getConnection() {
        Connection connection = super.getConnection()
        schemaHandler.useSchema(connection, tenantId)
        new MultiTenantConnection(connection, schemaHandler)
    }

    @Override
    Connection getConnection(String username, String password) {
        Connection connection = super.getConnection(username, password)
        schemaHandler.useSchema(connection, tenantId)
        new MultiTenantConnection(connection, schemaHandler)
    }
}
