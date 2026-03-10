package liquibase.ext.hibernate.database.connection;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import liquibase.resource.ResourceAccessor;

/**
 * Implements java.sql.Connection in order to pretend a hibernate configuration is a database in order to fit into the Liquibase framework.
 * Beyond standard Connection methods, this class exposes {@link #getPrefix()}, {@link #getPath()} and {@link #getProperties()} to access the setting passed in the JDBC URL.
 */
public class HibernateConnection implements Connection {
    private final String prefix;
    private final String url;

    private String path;
    private final ResourceAccessor resourceAccessor;
    private final Properties properties;

    public HibernateConnection(String url, ResourceAccessor resourceAccessor) {
        this.url = url;

        this.prefix = url.replaceFirst(":[^:]+$", "");

        // Trim the prefix off the URL for the path
        path = url.substring(prefix.length() + 1);
        this.resourceAccessor = resourceAccessor;

        // Check if there is a parameter/query string value.
        properties = new Properties();

        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            // Convert the query string into properties
            properties.putAll(readProperties(path.substring(queryIndex + 1)));

            if (properties.containsKey("dialect") && !properties.containsKey("hibernate.dialect")) {
                properties.put("hibernate.dialect", properties.getProperty("dialect"));
            }

            // Remove the query string
            path = path.substring(0, queryIndex);
        }
    }

    /**
     * Creates properties to attach to this connection based on the passed query string.
     */
    protected Properties readProperties(String queryString) {
        Properties properties = new Properties();
        queryString = queryString.replaceAll("&", System.lineSeparator());
        try {
            queryString = URLDecoder.decode(queryString, StandardCharsets.UTF_8);
            properties.load(new StringReader(queryString));
        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to read properties from url", ioe);
        }

        return properties;
    }

    /**
     * Returns the entire connection URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the 'protocol' of the URL. For example, "hibernate:classic" or "hibernate:ejb3"
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * The portion of the url between the path and the query string. Normally a filename or a class name.
     */
    public String getPath() {
        return path;
    }

    /**
     * The set of properties provided by the URL. Eg:
     * <p/>
     * <code>hibernate:classic:/path/to/hibernate.cfg.xml?foo=bar</code>
     * <p/>
     * This will have a property called 'foo' with a value of 'bar'.
     */
    public Properties getProperties() {
        return properties;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// JDBC METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Statement createStatement() {
        return null;
    }

    public PreparedStatement prepareStatement(String sql) {
        return null;
    }

    public CallableStatement prepareCall(String sql) {
        return null;
    }

    public String nativeSQL(String sql) {
        return null;
    }

    public void setAutoCommit(boolean autoCommit) {}

    public boolean getAutoCommit() {
        return false;
    }

    public void commit() {}

    public void rollback() {}

    public void close() {}

    public boolean isClosed() {
        return false;
    }

    public DatabaseMetaData getMetaData() {
        return new HibernateConnectionMetadata(url);
    }

    public void setReadOnly(boolean readOnly) {}

    public boolean isReadOnly() {
        return true;
    }

    public void setCatalog(String catalog) {}

    public String getCatalog() {
        return "HIBERNATE";
    }

    public void setTransactionIsolation(int level) {}

    public int getTransactionIsolation() {
        return Connection.TRANSACTION_NONE;
    }

    public SQLWarning getWarnings() {
        return null;
    }

    public void clearWarnings() {}

    public Statement createStatement(int resultSetType, int resultSetConcurrency) {
        return null;
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) {
        return null;
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) {
        return null;
    }

    public Map<String, Class<?>> getTypeMap() {
        return null;
    }

    public void setTypeMap(Map<String, Class<?>> map) {}

    public void setHoldability(int holdability) {}

    public int getHoldability() {
        return 0;
    }

    public Savepoint setSavepoint() {
        return null;
    }

    public Savepoint setSavepoint(String name) {
        return null;
    }

    public void rollback(Savepoint savepoint) {}

    public void releaseSavepoint(Savepoint savepoint) {}

    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        return null;
    }

    public PreparedStatement prepareStatement(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        return null;
    }

    public CallableStatement prepareCall(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        return null;
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) {
        return null;
    }

    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) {
        return null;
    }

    public PreparedStatement prepareStatement(String sql, String[] columnNames) {
        return null;
    }

    public Clob createClob() {
        return null;
    }

    public Blob createBlob() {
        return null;
    }

    public NClob createNClob() {
        return null;
    }

    public SQLXML createSQLXML() {
        return null;
    }

    public boolean isValid(int timeout) {
        return false;
    }

    public void setClientInfo(String name, String value) {}

    public void setClientInfo(Properties properties) {}

    public String getClientInfo(String name) {
        return null;
    }

    public Properties getClientInfo() {
        return null;
    }

    public Array createArrayOf(String typeName, Object[] elements) {
        return null;
    }

    public Struct createStruct(String typeName, Object[] attributes) {
        return null;
    }

    public <T> T unwrap(Class<T> iface) {
        return null;
    }

    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }

    // @Override only in java 1.7
    public void abort(Executor arg0) {}

    // @Override only in java 1.7
    public int getNetworkTimeout() {
        return 0;
    }

    // @Override only in java 1.7
    public String getSchema() {
        return "HIBERNATE";
    }

    // @Override only in java 1.7
    public void setNetworkTimeout(Executor arg0, int arg1) {}

    // @Override only in java 1.7
    public void setSchema(String arg0) {}

    public ResourceAccessor getResourceAccessor() {
        return resourceAccessor;
    }
}
