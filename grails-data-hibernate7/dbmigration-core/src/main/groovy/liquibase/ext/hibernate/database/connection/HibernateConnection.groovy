package liquibase.ext.hibernate.database.connection

import java.nio.charset.StandardCharsets
import java.sql.Array
import java.sql.Blob
import java.sql.CallableStatement
import java.sql.Clob
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.NClob
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.SQLWarning
import java.sql.SQLXML
import java.sql.Savepoint
import java.sql.Statement
import java.sql.Struct
import java.util.concurrent.Executor

import groovy.transform.CompileStatic
import liquibase.resource.ResourceAccessor

/**
 * Implements java.sql.Connection in order to pretend a hibernate configuration is a database in order to fit into the Liquibase framework.
 * Beyond standard Connection methods, this class exposes {@link #getPrefix()}, {@link #getPath()} and {@link #getProperties()} to access the setting passed in the JDBC URL.
 */
@CompileStatic
class HibernateConnection implements Connection {

    private final String prefix
    private final String url

    private String path
    private final ResourceAccessor resourceAccessor
    private final Properties properties

    HibernateConnection(String url, ResourceAccessor resourceAccessor) {
        this.url = url

        this.prefix = url.replaceFirst(':[^:]+$', '')

        // Trim the prefix off the URL for the path
        path = url.substring(prefix.length() + 1)
        this.resourceAccessor = resourceAccessor

        // Check if there is a parameter/query string value.
        properties = new Properties()

        int queryIndex = path.indexOf('?')
        if (queryIndex >= 0) {
            // Convert the query string into properties
            properties.putAll(readProperties(path.substring(queryIndex + 1)))

            if (properties.containsKey('dialect') && !properties.containsKey('hibernate.dialect')) {
                properties.put('hibernate.dialect', properties.getProperty('dialect'))
            }

            // Remove the query string
            path = path.substring(0, queryIndex)
        }
    }

    /**
     * Creates properties to attach to this connection based on the passed query string.
     */
    protected final Properties readProperties(String queryString) {
        Properties properties = new Properties()
        String propertiesString = queryString.replaceAll('&', System.lineSeparator())
        try {
            propertiesString = URLDecoder.decode(propertiesString, StandardCharsets.UTF_8)
            properties.load(new StringReader(propertiesString))
        } catch (IOException ioe) {
            throw new IllegalStateException('Failed to read properties from url', ioe)
        }

        return properties
    }

    /**
     * Returns the entire connection URL
     */
    String getUrl() {
        return url
    }

    /**
     * Returns the 'protocol' of the URL. For example, "hibernate:classic" or "hibernate:ejb3"
     */
    String getPrefix() {
        return prefix
    }

    /**
     * The portion of the url between the path and the query string. Normally a filename or a class name.
     */
    String getPath() {
        return path
    }

    /**
     * The set of properties provided by the URL. Eg:
     * <p/>
     * <code>hibernate:classic:/path/to/hibernate.cfg.xml?foo=bar</code>
     * <p/>
     * This will have a property called 'foo' with a value of 'bar'.
     */
    Properties getProperties() {
        return properties
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// JDBC METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    Statement createStatement() throws SQLException {
        throw new SQLFeatureNotSupportedException()
    }

    @Override
    PreparedStatement prepareStatement(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException()
    }

    @Override
    CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException()
    }

    @Override
    String nativeSQL(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException()
    }

    @Override
    void setAutoCommit(boolean autoCommit) {}

    @Override
    boolean getAutoCommit() {
        return false
    }

    @Override
    void commit() {}

    @Override
    void rollback() {}

    @Override
    void close() {}

    @Override
    boolean isClosed() {
        return false
    }

    @Override
    DatabaseMetaData getMetaData() {
        return new HibernateConnectionMetadata(url)
    }

    @Override
    void setReadOnly(boolean readOnly) {}

    @Override
    boolean isReadOnly() {
        return true
    }

    @Override
    void setCatalog(String catalog) {}

    @Override
    String getCatalog() {
        return 'HIBERNATE'
    }

    @Override
    void setTransactionIsolation(int level) {}

    @Override
    int getTransactionIsolation() {
        return Connection.TRANSACTION_NONE
    }

    @Override
    SQLWarning getWarnings() {
        return null
    }

    @Override
    void clearWarnings() {}

    @Override
    Statement createStatement(int resultSetType, int resultSetConcurrency) {
        return null
    }

    @Override
    PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) {
        return null
    }

    @Override
    CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) {
        return null
    }

    @Override
    Map<String, Class<?>> getTypeMap() {
        return (Map<String, Class<?>>) Collections.emptyMap()
    }

    @Override
    void setTypeMap(Map<String, Class<?>> map) {}

    @Override
    void setHoldability(int holdability) {}

    @Override
    int getHoldability() {
        return 0
    }

    @Override
    Savepoint setSavepoint() {
        return null
    }

    @Override
    Savepoint setSavepoint(String name) {
        return null
    }

    @Override
    void rollback(Savepoint savepoint) {}

    @Override
    void releaseSavepoint(Savepoint savepoint) {}

    @Override
    Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        return null
    }

    @Override
    PreparedStatement prepareStatement(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        return null
    }

    @Override
    CallableStatement prepareCall(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        return null
    }

    @Override
    PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) {
        return null
    }

    @Override
    PreparedStatement prepareStatement(String sql, int[] columnIndexes) {
        return null
    }

    @Override
    PreparedStatement prepareStatement(String sql, String[] columnNames) {
        return null
    }

    @Override
    Clob createClob() {
        return null
    }

    @Override
    Blob createBlob() {
        return null
    }

    @Override
    NClob createNClob() {
        return null
    }

    @Override
    SQLXML createSQLXML() {
        return null
    }

    @Override
    boolean isValid(int timeout) {
        return false
    }

    @Override
    void setClientInfo(String name, String value) {}

    @Override
    void setClientInfo(Properties properties) {}

    @Override
    String getClientInfo(String name) {
        return null
    }

    @Override
    Properties getClientInfo() {
        return new Properties()
    }

    @Override
    Array createArrayOf(String typeName, Object[] elements) {
        return null
    }

    @Override
    Struct createStruct(String typeName, Object[] attributes) {
        return null
    }

    @Override
    def <T> T unwrap(Class<T> iface) {
        return null
    }

    @Override
    boolean isWrapperFor(Class<?> iface) {
        return false
    }

    @Override
    void abort(Executor arg0) {}

    @Override
    int getNetworkTimeout() {
        return 0
    }

    @Override
    String getSchema() {
        return 'HIBERNATE'
    }

    @Override
    void setNetworkTimeout(Executor arg0, int arg1) {}

    @Override
    void setSchema(String arg0) {}

    ResourceAccessor getResourceAccessor() {
        return resourceAccessor
    }

}
