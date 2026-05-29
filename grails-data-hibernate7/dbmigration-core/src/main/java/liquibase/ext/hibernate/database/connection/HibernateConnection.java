package liquibase.ext.hibernate.database.connection;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Collections;
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
    protected final Properties readProperties(String queryString) {
        Properties properties = new Properties();
        String propertiesString = queryString.replaceAll("&", System.lineSeparator());
        try {
            propertiesString = URLDecoder.decode(propertiesString, StandardCharsets.UTF_8);
            properties.load(new StringReader(propertiesString));
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

    @Override
    public Statement createStatement() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setAutoCommit(boolean autoCommit) {}

    @Override
    public boolean getAutoCommit() {
        return false;
    }

    @Override
    public void commit() {}

    @Override
    public void rollback() {}

    @Override
    public void close() {}

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public DatabaseMetaData getMetaData() {
        return new HibernateConnectionMetadata(url);
    }

    @Override
    public void setReadOnly(boolean readOnly) {}

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public void setCatalog(String catalog) {}

    @Override
    public String getCatalog() {
        return "HIBERNATE";
    }

    @Override
    public void setTransactionIsolation(int level) {}

    @Override
    public int getTransactionIsolation() {
        return Connection.TRANSACTION_NONE;
    }

    @Override
    public SQLWarning getWarnings() {
        return null;
    }

    @Override
    public void clearWarnings() {}

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) {
        return null;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) {
        return null;
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) {
        return null;
    }

    @Override
    public Map<String, Class<?>> getTypeMap() {
        return Collections.emptyMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) {}

    @Override
    public void setHoldability(int holdability) {}

    @Override
    public int getHoldability() {
        return 0;
    }

    @Override
    public Savepoint setSavepoint() {
        return null;
    }

    @Override
    public Savepoint setSavepoint(String name) {
        return null;
    }

    @Override
    public void rollback(Savepoint savepoint) {}

    @Override
    public void releaseSavepoint(Savepoint savepoint) {}

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        return null;
    }

    @Override
    public PreparedStatement prepareStatement(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        return null;
    }

    @Override
    public CallableStatement prepareCall(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        return null;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) {
        return null;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) {
        return null;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) {
        return null;
    }

    @Override
    public Clob createClob() {
        return null;
    }

    @Override
    public Blob createBlob() {
        return null;
    }

    @Override
    public NClob createNClob() {
        return null;
    }

    @Override
    public SQLXML createSQLXML() {
        return null;
    }

    @Override
    public boolean isValid(int timeout) {
        return false;
    }

    @Override
    public void setClientInfo(String name, String value) {}

    @Override
    public void setClientInfo(Properties properties) {}

    @Override
    public String getClientInfo(String name) {
        return null;
    }

    @Override
    public Properties getClientInfo() {
        return new Properties();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) {
        return null;
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) {
        return null;
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }

    @Override
    public void abort(Executor arg0) {}

    @Override
    public int getNetworkTimeout() {
        return 0;
    }

    @Override
    public String getSchema() {
        return "HIBERNATE";
    }

    @Override
    public void setNetworkTimeout(Executor arg0, int arg1) {}

    @Override
    public void setSchema(String arg0) {}

    public ResourceAccessor getResourceAccessor() {
        return resourceAccessor;
    }
}
