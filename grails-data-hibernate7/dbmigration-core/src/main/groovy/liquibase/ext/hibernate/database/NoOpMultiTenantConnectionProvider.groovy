package liquibase.ext.hibernate.database

import java.sql.Connection
import java.sql.SQLException

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider

/**
 * Used by hibernate to ensure no database access is performed.
 */
@CompileStatic
@PackageScope
class NoOpMultiTenantConnectionProvider implements MultiTenantConnectionProvider {

    // Fix: Classes implementing Serializable should set a serialVersionUID (PMD #13)
    @Serial
    private static final long serialVersionUID = 1L

    @Override
    boolean isUnwrappableAs(Class<?> unwrapType) {
        return false
    }

    @Override
    def <T> T unwrap(Class<T> unwrapType) {
        return null
    }

    @Override
    Connection getAnyConnection() {
        return null
    }

    @Override
    void releaseAnyConnection(Connection connection) {
        // No-op
    }

    Connection getConnection(String tenantIdentifier) throws SQLException {
        return null
    }

    void releaseConnection(String tenantIdentifier, Connection connection) {
        // No-op
    }

    @Override
    Connection getConnection(Object tenantIdentifier) throws SQLException {
        // Fix: Added missing @Override annotation (PMD #14)
        return null
    }

    @Override
    void releaseConnection(Object tenantIdentifier, Connection connection) {
        // Fix: Added missing @Override annotation (PMD #15)
        // No-op
    }

    @Override
    boolean supportsAggressiveRelease() {
        return false
    }

}
