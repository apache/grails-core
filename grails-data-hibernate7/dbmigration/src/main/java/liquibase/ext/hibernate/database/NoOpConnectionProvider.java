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

import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

/**
 * Used by hibernate to ensure no database access is performed.
 */
class NoOpConnectionProvider implements ConnectionProvider {

    // Fix: Classes implementing Serializable should set a serialVersionUID (PMD #12)
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Connection getConnection() throws SQLException {
        throw new SQLException("No connection");
    }

    @Override
    public void closeConnection(Connection conn) {
        // No-op
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }

    /**
     * Helper for multi-tenant or legacy calls.
     */
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        return getConnection();
    }

    /**
     * Helper for Hibernate 5/6 SPI calls.
     */
    public Connection getConnection(Object o) throws SQLException {
        return getConnection();
    }

    /**
     * No-op release.
     */
    public void releaseConnection(Object tenantIdentifier, Connection connection) {
        // No-op
    }

    /**
     * No-op release.
     */
    public void releaseConnection(String tenantIdentifier, Connection connection) {
        // No-op
    }
}