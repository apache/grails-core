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

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler

import java.sql.Connection
import java.sql.SQLException

/**
 * Allows restoring the target schema prior to releasing the connection to the pool in Multi-Tenant environment
 *
 * @author Graeme
 * @since 6.1.7
 */
@CompileStatic
class MultiTenantConnection implements Connection {

    final @Delegate
    Connection target
    final SchemaHandler schemaHandler

    MultiTenantConnection(Connection target, SchemaHandler schemaHandler) {
        this.target = target
        this.schemaHandler = schemaHandler
    }

    @Override
    void close() throws SQLException {
        try {
            if (!isClosed()) {
                schemaHandler.useDefaultSchema(this)
            }
        } finally {
            target.close()
        }
    }
}
