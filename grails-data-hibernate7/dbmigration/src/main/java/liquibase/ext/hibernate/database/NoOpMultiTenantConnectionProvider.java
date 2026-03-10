package liquibase.ext.hibernate.database;

import java.sql.Connection;
import java.sql.SQLException;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/**
 * Used by hibernate to ensure no database access is performed.
 */
class NoOpMultiTenantConnectionProvider implements MultiTenantConnectionProvider {

    @Override
    public boolean isUnwrappableAs(Class unwrapType) {
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
    public void releaseAnyConnection(Connection connection) {}

    public Connection getConnection(String s) {
        return null;
    }

    public void releaseConnection(String s, Connection connection) {}

    public Connection getConnection(Object tenantIdentifier) {
        return null;
    }

    public void releaseConnection(Object tenantIdentifier, Connection connection) {}

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }
}
