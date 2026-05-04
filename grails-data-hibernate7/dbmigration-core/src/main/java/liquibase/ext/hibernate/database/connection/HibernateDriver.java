package liquibase.ext.hibernate.database.connection;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import liquibase.database.LiquibaseExtDriver;
import liquibase.resource.ResourceAccessor;

/**
 * Implements the standard java.sql.Driver interface to allow the Hibernate integration to better fit into
 * what Liquibase expects.
 */
public class HibernateDriver implements Driver, LiquibaseExtDriver {

    private ResourceAccessor resourceAccessor;

    @Override
    public Connection connect(String url, Properties info) {
        return new HibernateConnection(url, resourceAccessor);
    }

    @Override
    public boolean acceptsURL(String url) {
        return url.startsWith("hibernate:");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setResourceAccessor(ResourceAccessor accessor) {
        this.resourceAccessor = accessor;
    }
}
