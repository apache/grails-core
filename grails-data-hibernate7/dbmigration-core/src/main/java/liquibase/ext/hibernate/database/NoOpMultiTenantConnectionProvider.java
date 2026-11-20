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
