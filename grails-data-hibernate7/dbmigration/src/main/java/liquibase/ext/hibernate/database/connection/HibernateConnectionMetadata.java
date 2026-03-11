package liquibase.ext.hibernate.database.connection;

import java.sql.*;

import org.hibernate.Version;

/**
 * Implements the standard java.sql.DatabaseMetaData interface to allow the Hibernate integration to better fit into
 * what Liquibase expects.
 */
public class HibernateConnectionMetadata implements DatabaseMetaData {

    private final String url;

    public HibernateConnectionMetadata(String url) {
        this.url = url;
    }

    public boolean allProceduresAreCallable() {
        return false;
    }

    public boolean allTablesAreSelectable() {
        return false;
    }

    public String getURL() {
        return url;
    }

    public String getUserName() {
        return null;
    }

    public boolean isReadOnly() {
        return true;
    }

    public boolean nullsAreSortedHigh() {
        return false;
    }

    public boolean nullsAreSortedLow() {
        return false;
    }

    public boolean nullsAreSortedAtStart() {
        return false;
    }

    public boolean nullsAreSortedAtEnd() {
        return false;
    }

    public String getDatabaseProductName() {
        return "Hibernate";
    }

    public String getDatabaseProductVersion() {
        return Version.getVersionString();
    }

    public String getDriverName() {
        return null;
    }

    public String getDriverVersion() {
        return "0";
    }

    public int getDriverMajorVersion() {
        return 0;
    }

    public int getDriverMinorVersion() {
        return 0;
    }

    public boolean usesLocalFiles() {
        return false;
    }

    public boolean usesLocalFilePerTable() {
        return false;
    }

    public boolean supportsMixedCaseIdentifiers() {
        return false;
    }

    public boolean storesUpperCaseIdentifiers() {
        return false;
    }

    public boolean storesLowerCaseIdentifiers() {
        return false;
    }

    public boolean storesMixedCaseIdentifiers() {
        return false;
    }

    public boolean supportsMixedCaseQuotedIdentifiers() {
        return false;
    }

    public boolean storesUpperCaseQuotedIdentifiers() {
        return false;
    }

    public boolean storesLowerCaseQuotedIdentifiers() {
        return false;
    }

    public boolean storesMixedCaseQuotedIdentifiers() {
        return false;
    }

    public String getIdentifierQuoteString() {
        return null;
    }

    public String getSQLKeywords() {
        return ""; // do not return null here due to liquibase.database.jvm.JdbcConnection:30 to avoid NPE's there
    }

    public String getNumericFunctions() {
        return null;
    }

    public String getStringFunctions() {
        return null;
    }

    public String getSystemFunctions() {
        return null;
    }

    public String getTimeDateFunctions() {
        return null;
    }

    public String getSearchStringEscape() {
        return null;
    }

    public String getExtraNameCharacters() {
        return null;
    }

    public boolean supportsAlterTableWithAddColumn() {
        return false;
    }

    public boolean supportsAlterTableWithDropColumn() {
        return false;
    }

    public boolean supportsColumnAliasing() {
        return false;
    }

    public boolean nullPlusNonNullIsNull() {
        return false;
    }

    public boolean supportsConvert() {
        return false;
    }

    public boolean supportsConvert(int fromType, int toType) {
        return false;
    }

    public boolean supportsTableCorrelationNames() {
        return false;
    }

    public boolean supportsDifferentTableCorrelationNames() {
        return false;
    }

    public boolean supportsExpressionsInOrderBy() {
        return false;
    }

    public boolean supportsOrderByUnrelated() {
        return false;
    }

    public boolean supportsGroupBy() {
        return false;
    }

    public boolean supportsGroupByUnrelated() {
        return false;
    }

    public boolean supportsGroupByBeyondSelect() {
        return false;
    }

    public boolean supportsLikeEscapeClause() {
        return false;
    }

    public boolean supportsMultipleResultSets() {
        return false;
    }

    public boolean supportsMultipleTransactions() {
        return false;
    }

    public boolean supportsNonNullableColumns() {
        return false;
    }

    public boolean supportsMinimumSQLGrammar() {
        return false;
    }

    public boolean supportsCoreSQLGrammar() {
        return false;
    }

    public boolean supportsExtendedSQLGrammar() {
        return false;
    }

    public boolean supportsANSI92EntryLevelSQL() {
        return false;
    }

    public boolean supportsANSI92IntermediateSQL() {
        return false;
    }

    public boolean supportsANSI92FullSQL() {
        return false;
    }

    public boolean supportsIntegrityEnhancementFacility() {
        return false;
    }

    public boolean supportsOuterJoins() {
        return false;
    }

    public boolean supportsFullOuterJoins() {
        return false;
    }

    public boolean supportsLimitedOuterJoins() {
        return false;
    }

    public String getSchemaTerm() {
        return null;
    }

    public String getProcedureTerm() {
        return null;
    }

    public String getCatalogTerm() {
        return null;
    }

    public boolean isCatalogAtStart() {
        return false;
    }

    public String getCatalogSeparator() {
        return null;
    }

    public boolean supportsSchemasInDataManipulation() {
        return false;
    }

    public boolean supportsSchemasInProcedureCalls() {
        return false;
    }

    public boolean supportsSchemasInTableDefinitions() {
        return false;
    }

    public boolean supportsSchemasInIndexDefinitions() {
        return false;
    }

    public boolean supportsSchemasInPrivilegeDefinitions() {
        return false;
    }

    public boolean supportsCatalogsInDataManipulation() {
        return false;
    }

    public boolean supportsCatalogsInProcedureCalls() {
        return false;
    }

    public boolean supportsCatalogsInTableDefinitions() {
        return false;
    }

    public boolean supportsCatalogsInIndexDefinitions() {
        return false;
    }

    public boolean supportsCatalogsInPrivilegeDefinitions() {
        return false;
    }

    public boolean supportsPositionedDelete() {
        return false;
    }

    public boolean supportsPositionedUpdate() {
        return false;
    }

    public boolean supportsSelectForUpdate() {
        return false;
    }

    public boolean supportsStoredProcedures() {
        return false;
    }

    public boolean supportsSubqueriesInComparisons() {
        return false;
    }

    public boolean supportsSubqueriesInExists() {
        return false;
    }

    public boolean supportsSubqueriesInIns() {
        return false;
    }

    public boolean supportsSubqueriesInQuantifieds() {
        return false;
    }

    public boolean supportsCorrelatedSubqueries() {
        return false;
    }

    public boolean supportsUnion() {
        return false;
    }

    public boolean supportsUnionAll() {
        return false;
    }

    public boolean supportsOpenCursorsAcrossCommit() {
        return false;
    }

    public boolean supportsOpenCursorsAcrossRollback() {
        return false;
    }

    public boolean supportsOpenStatementsAcrossCommit() {
        return false;
    }

    public boolean supportsOpenStatementsAcrossRollback() {
        return false;
    }

    public int getMaxBinaryLiteralLength() {
        return 0;
    }

    public int getMaxCharLiteralLength() {
        return 0;
    }

    public int getMaxColumnNameLength() {
        return 0;
    }

    public int getMaxColumnsInGroupBy() {
        return 0;
    }

    public int getMaxColumnsInIndex() {
        return 0;
    }

    public int getMaxColumnsInOrderBy() {
        return 0;
    }

    public int getMaxColumnsInSelect() {
        return 0;
    }

    public int getMaxColumnsInTable() {
        return 0;
    }

    public int getMaxConnections() {
        return 0;
    }

    public int getMaxCursorNameLength() {
        return 0;
    }

    public int getMaxIndexLength() {
        return 0;
    }

    public int getMaxSchemaNameLength() {
        return 0;
    }

    public int getMaxProcedureNameLength() {
        return 0;
    }

    public int getMaxCatalogNameLength() {
        return 0;
    }

    public int getMaxRowSize() {
        return 0;
    }

    public boolean doesMaxRowSizeIncludeBlobs() {
        return false;
    }

    public int getMaxStatementLength() {
        return 0;
    }

    public int getMaxStatements() {
        return 0;
    }

    public int getMaxTableNameLength() {
        return 0;
    }

    public int getMaxTablesInSelect() {
        return 0;
    }

    public int getMaxUserNameLength() {
        return 0;
    }

    public int getDefaultTransactionIsolation() {
        return 0;
    }

    public boolean supportsTransactions() {
        return false;
    }

    public boolean supportsTransactionIsolationLevel(int level) {
        return false;
    }

    public boolean supportsDataDefinitionAndDataManipulationTransactions() {
        return false;
    }

    public boolean supportsDataManipulationTransactionsOnly() {
        return false;
    }

    public boolean dataDefinitionCausesTransactionCommit() {
        return false;
    }

    public boolean dataDefinitionIgnoredInTransactions() {
        return false;
    }

    public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) {
        return null;
    }

    public ResultSet getProcedureColumns(
            String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) {
        return null;
    }

    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) {
        return null;
    }

    public ResultSet getSchemas() {
        return null;
    }

    public ResultSet getCatalogs() {
        return null;
    }

    public ResultSet getTableTypes() {
        return null;
    }

    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) {
        return null;
    }

    public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) {
        return null;
    }

    public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) {
        return null;
    }

    public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) {
        return null;
    }

    public ResultSet getVersionColumns(String catalog, String schema, String table) {
        return null;
    }

    public ResultSet getPrimaryKeys(String catalog, String schema, String table) {
        return null;
    }

    public ResultSet getImportedKeys(String catalog, String schema, String table) {
        return null;
    }

    public ResultSet getExportedKeys(String catalog, String schema, String table) {
        return null;
    }

    public ResultSet getCrossReference(
            String parentCatalog,
            String parentSchema,
            String parentTable,
            String foreignCatalog,
            String foreignSchema,
            String foreignTable) {
        return null;
    }

    public ResultSet getTypeInfo() {
        return null;
    }

    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) {
        return null;
    }

    public boolean supportsResultSetType(int type) {
        return false;
    }

    public boolean supportsResultSetConcurrency(int type, int concurrency) {
        return false;
    }

    public boolean ownUpdatesAreVisible(int type) {
        return false;
    }

    public boolean ownDeletesAreVisible(int type) {
        return false;
    }

    public boolean ownInsertsAreVisible(int type) {
        return false;
    }

    public boolean othersUpdatesAreVisible(int type) {
        return false;
    }

    public boolean othersDeletesAreVisible(int type) {
        return false;
    }

    public boolean othersInsertsAreVisible(int type) {
        return false;
    }

    public boolean updatesAreDetected(int type) {
        return false;
    }

    public boolean deletesAreDetected(int type) {
        return false;
    }

    public boolean insertsAreDetected(int type) {
        return false;
    }

    public boolean supportsBatchUpdates() {
        return false;
    }

    public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) {
        return null;
    }

    public Connection getConnection() {
        return null;
    }

    public boolean supportsSavepoints() {
        return false;
    }

    public boolean supportsNamedParameters() {
        return false;
    }

    public boolean supportsMultipleOpenResults() {
        return false;
    }

    public boolean supportsGetGeneratedKeys() {
        return false;
    }

    public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) {
        return null;
    }

    public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) {
        return null;
    }

    public ResultSet getAttributes(
            String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) {
        return null;
    }

    public boolean supportsResultSetHoldability(int holdability) {
        return false;
    }

    public int getResultSetHoldability() {
        return 0;
    }

    public int getDatabaseMajorVersion() {
        return 0;
    }

    public int getDatabaseMinorVersion() {
        return 0;
    }

    public int getJDBCMajorVersion() {
        return 0;
    }

    public int getJDBCMinorVersion() {
        return 0;
    }

    public int getSQLStateType() {
        return DatabaseMetaData.sqlStateSQL;
    }

    public boolean locatorsUpdateCopy() {
        return false;
    }

    public boolean supportsStatementPooling() {
        return false;
    }

    public RowIdLifetime getRowIdLifetime() {
        return null;
    }

    public ResultSet getSchemas(String catalog, String schemaPattern) {
        return null;
    }

    public boolean supportsStoredFunctionsUsingCallSyntax() {
        return false;
    }

    public boolean autoCommitFailureClosesAllResultSets() {
        return false;
    }

    public ResultSet getClientInfoProperties() {
        return null;
    }

    public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) {
        return null;
    }

    public ResultSet getFunctionColumns(
            String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) {
        return null;
    }

    public <T> T unwrap(Class<T> iface) {
        return null;
    }

    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }

    // @Override only override for java 1.7
    public boolean generatedKeyAlwaysReturned() {
        return false;
    }

    // @Override only override for java 1.7
    public ResultSet getPseudoColumns(String arg0, String arg1, String arg2, String arg3) {
        return null;
    }
}
