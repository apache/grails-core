package liquibase.ext.hibernate.database.connection

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.RowIdLifetime

import groovy.transform.CompileStatic
import org.hibernate.Version

/**
 * Implements the standard java.sql.DatabaseMetaData interface to allow the Hibernate integration to better fit into
 * what Liquibase expects.
 */
@CompileStatic
class HibernateConnectionMetadata implements DatabaseMetaData {

    private final String url

    HibernateConnectionMetadata(String url) {
        this.url = url
    }

    @Override
    boolean allProceduresAreCallable() {
        return false
    }

    @Override
    boolean allTablesAreSelectable() {
        return false
    }

    @Override
    String getURL() {
        return url
    }

    @Override
    String getUserName() {
        return null
    }

    @Override
    boolean isReadOnly() {
        return true
    }

    @Override
    boolean nullsAreSortedHigh() {
        return false
    }

    @Override
    boolean nullsAreSortedLow() {
        return false
    }

    @Override
    boolean nullsAreSortedAtStart() {
        return false
    }

    @Override
    boolean nullsAreSortedAtEnd() {
        return false
    }

    @Override
    String getDatabaseProductName() {
        return 'Hibernate'
    }

    @Override
    String getDatabaseProductVersion() {
        return Version.getVersionString()
    }

    @Override
    String getDriverName() {
        return null
    }

    @Override
    String getDriverVersion() {
        return '0'
    }

    @Override
    int getDriverMajorVersion() {
        return 0
    }

    @Override
    int getDriverMinorVersion() {
        return 0
    }

    @Override
    boolean usesLocalFiles() {
        return false
    }

    @Override
    boolean usesLocalFilePerTable() {
        return false
    }

    @Override
    boolean supportsMixedCaseIdentifiers() {
        return false
    }

    @Override
    boolean storesUpperCaseIdentifiers() {
        return false
    }

    @Override
    boolean storesLowerCaseIdentifiers() {
        return false
    }

    @Override
    boolean storesMixedCaseIdentifiers() {
        return false
    }

    @Override
    boolean supportsMixedCaseQuotedIdentifiers() {
        return false
    }

    @Override
    boolean storesUpperCaseQuotedIdentifiers() {
        return false
    }

    @Override
    boolean storesLowerCaseQuotedIdentifiers() {
        return false
    }

    @Override
    boolean storesMixedCaseQuotedIdentifiers() {
        return false
    }

    @Override
    String getIdentifierQuoteString() {
        return null
    }

    @Override
    String getSQLKeywords() {
        return '' // do not return null here due to liquibase.database.jvm.JdbcConnection:30 to avoid NPE's there
    }

    @Override
    String getNumericFunctions() {
        return null
    }

    @Override
    String getStringFunctions() {
        return null
    }

    @Override
    String getSystemFunctions() {
        return null
    }

    @Override
    String getTimeDateFunctions() {
        return null
    }

    @Override
    String getSearchStringEscape() {
        return null
    }

    @Override
    String getExtraNameCharacters() {
        return null
    }

    @Override
    boolean supportsAlterTableWithAddColumn() {
        return false
    }

    @Override
    boolean supportsAlterTableWithDropColumn() {
        return false
    }

    @Override
    boolean supportsColumnAliasing() {
        return false
    }

    @Override
    boolean nullPlusNonNullIsNull() {
        return false
    }

    @Override
    boolean supportsConvert() {
        return false
    }

    @Override
    boolean supportsConvert(int fromType, int toType) {
        return false
    }

    @Override
    boolean supportsTableCorrelationNames() {
        return false
    }

    @Override
    boolean supportsDifferentTableCorrelationNames() {
        return false
    }

    @Override
    boolean supportsExpressionsInOrderBy() {
        return false
    }

    @Override
    boolean supportsOrderByUnrelated() {
        return false
    }

    @Override
    boolean supportsGroupBy() {
        return false
    }

    @Override
    boolean supportsGroupByUnrelated() {
        return false
    }

    @Override
    boolean supportsGroupByBeyondSelect() {
        return false
    }

    @Override
    boolean supportsLikeEscapeClause() {
        return false
    }

    @Override
    boolean supportsMultipleResultSets() {
        return false
    }

    @Override
    boolean supportsMultipleTransactions() {
        return false
    }

    @Override
    boolean supportsNonNullableColumns() {
        return false
    }

    @Override
    boolean supportsMinimumSQLGrammar() {
        return false
    }

    @Override
    boolean supportsCoreSQLGrammar() {
        return false
    }

    @Override
    boolean supportsExtendedSQLGrammar() {
        return false
    }

    @Override
    boolean supportsANSI92EntryLevelSQL() {
        return false
    }

    @Override
    boolean supportsANSI92IntermediateSQL() {
        return false
    }

    @Override
    boolean supportsANSI92FullSQL() {
        return false
    }

    @Override
    boolean supportsIntegrityEnhancementFacility() {
        return false
    }

    @Override
    boolean supportsOuterJoins() {
        return false
    }

    @Override
    boolean supportsFullOuterJoins() {
        return false
    }

    @Override
    boolean supportsLimitedOuterJoins() {
        return false
    }

    @Override
    String getSchemaTerm() {
        return null
    }

    @Override
    String getProcedureTerm() {
        return null
    }

    @Override
    String getCatalogTerm() {
        return null
    }

    @Override
    boolean isCatalogAtStart() {
        return false
    }

    @Override
    String getCatalogSeparator() {
        return null
    }

    @Override
    boolean supportsSchemasInDataManipulation() {
        return false
    }

    @Override
    boolean supportsSchemasInProcedureCalls() {
        return false
    }

    @Override
    boolean supportsSchemasInTableDefinitions() {
        return false
    }

    @Override
    boolean supportsSchemasInIndexDefinitions() {
        return false
    }

    @Override
    boolean supportsSchemasInPrivilegeDefinitions() {
        return false
    }

    @Override
    boolean supportsCatalogsInDataManipulation() {
        return false
    }

    @Override
    boolean supportsCatalogsInProcedureCalls() {
        return false
    }

    @Override
    boolean supportsCatalogsInTableDefinitions() {
        return false
    }

    @Override
    boolean supportsCatalogsInIndexDefinitions() {
        return false
    }

    @Override
    boolean supportsCatalogsInPrivilegeDefinitions() {
        return false
    }

    @Override
    boolean supportsPositionedDelete() {
        return false
    }

    @Override
    boolean supportsPositionedUpdate() {
        return false
    }

    @Override
    boolean supportsSelectForUpdate() {
        return false
    }

    @Override
    boolean supportsStoredProcedures() {
        return false
    }

    @Override
    boolean supportsSubqueriesInComparisons() {
        return false
    }

    @Override
    boolean supportsSubqueriesInExists() {
        return false
    }

    @Override
    boolean supportsSubqueriesInIns() {
        return false
    }

    @Override
    boolean supportsSubqueriesInQuantifieds() {
        return false
    }

    @Override
    boolean supportsCorrelatedSubqueries() {
        return false
    }

    @Override
    boolean supportsUnion() {
        return false
    }

    @Override
    boolean supportsUnionAll() {
        return false
    }

    @Override
    boolean supportsOpenCursorsAcrossCommit() {
        return false
    }

    @Override
    boolean supportsOpenCursorsAcrossRollback() {
        return false
    }

    @Override
    boolean supportsOpenStatementsAcrossCommit() {
        return false
    }

    @Override
    boolean supportsOpenStatementsAcrossRollback() {
        return false
    }

    @Override
    int getMaxBinaryLiteralLength() {
        return 0
    }

    @Override
    int getMaxCharLiteralLength() {
        return 0
    }

    @Override
    int getMaxColumnNameLength() {
        return 0
    }

    @Override
    int getMaxColumnsInGroupBy() {
        return 0
    }

    @Override
    int getMaxColumnsInIndex() {
        return 0
    }

    @Override
    int getMaxColumnsInOrderBy() {
        return 0
    }

    @Override
    int getMaxColumnsInSelect() {
        return 0
    }

    @Override
    int getMaxColumnsInTable() {
        return 0
    }

    @Override
    int getMaxConnections() {
        return 0
    }

    @Override
    int getMaxCursorNameLength() {
        return 0
    }

    @Override
    int getMaxIndexLength() {
        return 0
    }

    @Override
    int getMaxSchemaNameLength() {
        return 0
    }

    @Override
    int getMaxProcedureNameLength() {
        return 0
    }

    @Override
    int getMaxCatalogNameLength() {
        return 0
    }

    @Override
    int getMaxRowSize() {
        return 0
    }

    @Override
    boolean doesMaxRowSizeIncludeBlobs() {
        return false
    }

    @Override
    int getMaxStatementLength() {
        return 0
    }

    @Override
    int getMaxStatements() {
        return 0
    }

    @Override
    int getMaxTableNameLength() {
        return 0
    }

    @Override
    int getMaxTablesInSelect() {
        return 0
    }

    @Override
    int getMaxUserNameLength() {
        return 0
    }

    @Override
    int getDefaultTransactionIsolation() {
        return 0
    }

    @Override
    boolean supportsTransactions() {
        return false
    }

    @Override
    boolean supportsTransactionIsolationLevel(int level) {
        return false
    }

    @Override
    boolean supportsDataDefinitionAndDataManipulationTransactions() {
        return false
    }

    @Override
    boolean supportsDataManipulationTransactionsOnly() {
        return false
    }

    @Override
    boolean dataDefinitionCausesTransactionCommit() {
        return false
    }

    @Override
    boolean dataDefinitionIgnoredInTransactions() {
        return false
    }

    @Override
    ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) {
        return null
    }

    @Override
    ResultSet getProcedureColumns(
            String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) {
        return null
    }

    @Override
    ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) {
        return null
    }

    @Override
    ResultSet getSchemas() {
        return null
    }

    @Override
    ResultSet getCatalogs() {
        return null
    }

    @Override
    ResultSet getTableTypes() {
        return null
    }

    @Override
    ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) {
        return null
    }

    @Override
    ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) {
        return null
    }

    @Override
    ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) {
        return null
    }

    @Override
    ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) {
        return null
    }

    @Override
    ResultSet getVersionColumns(String catalog, String schema, String table) {
        return null
    }

    @Override
    ResultSet getPrimaryKeys(String catalog, String schema, String table) {
        return null
    }

    @Override
    ResultSet getImportedKeys(String catalog, String schema, String table) {
        return null
    }

    @Override
    ResultSet getExportedKeys(String catalog, String schema, String table) {
        return null
    }

    @Override
    ResultSet getCrossReference(
            String parentCatalog,
            String parentSchema,
            String parentTable,
            String foreignCatalog,
            String foreignSchema,
            String foreignTable) {
        return null
    }

    @Override
    ResultSet getTypeInfo() {
        return null
    }

    @Override
    ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) {
        return null
    }

    @Override
    boolean supportsResultSetType(int type) {
        return false
    }

    @Override
    boolean supportsResultSetConcurrency(int type, int concurrency) {
        return false
    }

    @Override
    boolean ownUpdatesAreVisible(int type) {
        return false
    }

    @Override
    boolean ownDeletesAreVisible(int type) {
        return false
    }

    @Override
    boolean ownInsertsAreVisible(int type) {
        return false
    }

    @Override
    boolean othersUpdatesAreVisible(int type) {
        return false
    }

    @Override
    boolean othersDeletesAreVisible(int type) {
        return false
    }

    @Override
    boolean othersInsertsAreVisible(int type) {
        return false
    }

    @Override
    boolean updatesAreDetected(int type) {
        return false
    }

    @Override
    boolean deletesAreDetected(int type) {
        return false
    }

    @Override
    boolean insertsAreDetected(int type) {
        return false
    }

    @Override
    boolean supportsBatchUpdates() {
        return false
    }

    @Override
    ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) {
        return null
    }

    @Override
    Connection getConnection() {
        return null
    }

    @Override
    boolean supportsSavepoints() {
        return false
    }

    @Override
    boolean supportsNamedParameters() {
        return false
    }

    @Override
    boolean supportsMultipleOpenResults() {
        return false
    }

    @Override
    boolean supportsGetGeneratedKeys() {
        return false
    }

    @Override
    ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) {
        return null
    }

    @Override
    ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) {
        return null
    }

    @Override
    ResultSet getAttributes(
            String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) {
        return null
    }

    @Override
    boolean supportsResultSetHoldability(int holdability) {
        return false
    }

    @Override
    int getResultSetHoldability() {
        return 0
    }

    @Override
    int getDatabaseMajorVersion() {
        return 0
    }

    @Override
    int getDatabaseMinorVersion() {
        return 0
    }

    @Override
    int getJDBCMajorVersion() {
        return 0
    }

    @Override
    int getJDBCMinorVersion() {
        return 0
    }

    @Override
    int getSQLStateType() {
        return DatabaseMetaData.sqlStateSQL
    }

    @Override
    boolean locatorsUpdateCopy() {
        return false
    }

    @Override
    boolean supportsStatementPooling() {
        return false
    }

    @Override
    RowIdLifetime getRowIdLifetime() {
        return null
    }

    @Override
    ResultSet getSchemas(String catalog, String schemaPattern) {
        return null
    }

    @Override
    boolean supportsStoredFunctionsUsingCallSyntax() {
        return false
    }

    @Override
    boolean autoCommitFailureClosesAllResultSets() {
        return false
    }

    @Override
    ResultSet getClientInfoProperties() {
        return null
    }

    @Override
    ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) {
        return null
    }

    @Override
    ResultSet getFunctionColumns(
            String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) {
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
    boolean generatedKeyAlwaysReturned() {
        return false
    }

    @Override
    ResultSet getPseudoColumns(String arg0, String arg1, String arg2, String arg3) {
        return null
    }

}
