package liquibase.ext.hibernate.database.connection

import java.sql.Connection
import java.sql.Driver
import java.sql.DriverPropertyInfo
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger

import groovy.transform.CompileStatic
import liquibase.database.LiquibaseExtDriver
import liquibase.resource.ResourceAccessor

/**
 * Implements the standard java.sql.Driver interface to allow the Hibernate integration to better fit into
 * what Liquibase expects.
 */
@CompileStatic
class HibernateDriver implements Driver, LiquibaseExtDriver {

    private ResourceAccessor resourceAccessor

    @Override
    Connection connect(String url, Properties info) {
        return new HibernateConnection(url, resourceAccessor)
    }

    @Override
    boolean acceptsURL(String url) {
        return url.startsWith('hibernate:')
    }

    @Override
    DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0]
    }

    @Override
    int getMajorVersion() {
        return 0
    }

    @Override
    int getMinorVersion() {
        return 0
    }

    @Override
    boolean jdbcCompliant() {
        return false
    }

    @Override
    Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException()
    }

    @Override
    void setResourceAccessor(ResourceAccessor accessor) {
        this.resourceAccessor = accessor
    }

}
