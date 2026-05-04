package liquibase.ext.hibernate.database.connection;

import liquibase.database.LiquibaseExtDriver;
import liquibase.resource.ResourceAccessor;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Implements the standard java.sql.Driver interface to allow the Hibernate integration to better fit into
 * what Liquibase expects.
 */
public class HibernateDriver implements Driver, LiquibaseExtDriver {

    private ResourceAccessor resourceAccessor;

    public Connection connect(String url, Properties info) throws SQLException {
        return new HibernateConnection(url, resourceAccessor);
    }

    public boolean acceptsURL(String url) throws SQLException {
        return url.startsWith("hibernate:");
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    public int getMajorVersion() {
        return 0;
    }

    public int getMinorVersion() {
        return 0;
    }

    public boolean jdbcCompliant() {
        return false;
    }

    //@Override only override for java 1.7
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setResourceAccessor(ResourceAccessor accessor) {
        this.resourceAccessor = accessor;
    }
}
