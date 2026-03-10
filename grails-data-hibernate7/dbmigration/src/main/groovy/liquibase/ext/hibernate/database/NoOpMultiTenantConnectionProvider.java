package liquibase.ext.hibernate.database;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;

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
    public Connection getAnyConnection() throws SQLException {
        return null;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {

    }

    public Connection getConnection(String s) throws SQLException {
        return null;
    }

    public void releaseConnection(String s, Connection connection) throws SQLException {

    }

    public Connection getConnection(Object tenantIdentifier) throws SQLException {
        return null;
    }

    public void releaseConnection(Object tenantIdentifier, Connection connection) throws SQLException {

    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

}
