package liquibase.ext.hibernate.database

import java.sql.Connection
import java.sql.SQLException

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider

/**
 * Used by hibernate to ensure no database access is performed.
 */
@CompileStatic
@PackageScope
class NoOpConnectionProvider implements ConnectionProvider {

    // Fix: Classes implementing Serializable should set a serialVersionUID (PMD #12)
    @Serial
    private static final long serialVersionUID = 1L

    @Override
    Connection getConnection() throws SQLException {
        throw new SQLException('No connection')
    }

    @Override
    void closeConnection(Connection conn) {
        // No-op
    }

    @Override
    boolean supportsAggressiveRelease() {
        return false
    }

    @Override
    boolean isUnwrappableAs(Class<?> unwrapType) {
        return false
    }

    @Override
    def <T> T unwrap(Class<T> unwrapType) {
        return null
    }

    /**
     * Helper for multi-tenant or legacy calls.
     */
    Connection getConnection(String tenantIdentifier) throws SQLException {
        return getConnection()
    }

    /**
     * Helper for Hibernate 5/6 SPI calls.
     */
    Connection getConnection(Object o) throws SQLException {
        return getConnection()
    }

    /**
     * No-op release.
     */
    void releaseConnection(Object tenantIdentifier, Connection connection) {
        // No-op
    }

    /**
     * No-op release.
     */
    void releaseConnection(String tenantIdentifier, Connection connection) {
        // No-op
    }

}
