package liquibase.ext.hibernate.database;

import java.sql.Connection;
import java.sql.SQLException;

import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

/**
 * Used by hibernate to ensure no database access is performed.
 */
class NoOpConnectionProvider implements ConnectionProvider {

    @Override
    public Connection getConnection() throws SQLException {
        throw new SQLException("No connection");
    }

    @Override
    public void closeConnection(Connection conn) {}

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }

    public Connection getConnection(String tenantIdentifier) throws SQLException {
        return getConnection();
    }

    public Connection getConnection(Object o) throws SQLException {
        return getConnection();
    }

    public void releaseConnection(Object tenantIdentifier, Connection connection) {}

    public void releaseConnection(String tenantIdentifier, Connection connection) {}
}
