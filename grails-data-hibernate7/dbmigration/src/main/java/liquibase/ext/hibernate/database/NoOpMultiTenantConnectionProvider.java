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
package liquibase.ext.hibernate.database;

import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/**
 * Used by hibernate to ensure no database access is performed.
 */
class NoOpMultiTenantConnectionProvider implements MultiTenantConnectionProvider {

    // Fix: Classes implementing Serializable should set a serialVersionUID (PMD #13)
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }

    @Override
    public Connection getAnyConnection() {
        return null;
    }

    @Override
    public void releaseAnyConnection(Connection connection) {
        // No-op
    }

    public Connection getConnection(String tenantIdentifier) throws SQLException {
        return null;
    }

    public void releaseConnection(String tenantIdentifier, Connection connection) {
        // No-op
    }

    @Override
    public Connection getConnection(Object tenantIdentifier) throws SQLException {
        // Fix: Added missing @Override annotation (PMD #14)
        return null;
    }

    @Override
    public void releaseConnection(Object tenantIdentifier, Connection connection) {
        // Fix: Added missing @Override annotation (PMD #15)
        // No-op
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }
}
