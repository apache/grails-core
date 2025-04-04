package org.grails.datastore.gorm.jdbc.schema

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.sql.DataSource
import java.sql.Connection
import java.sql.ResultSet

/**
 * Resolves the schema names
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
@Slf4j
class DefaultSchemaHandler implements SchemaHandler {

    final String useSchemaStatement
    final String createSchemaStatement
    final String defaultSchemaName

    DefaultSchemaHandler() {
        useSchemaStatement = "SET SCHEMA %s"
        createSchemaStatement = "CREATE SCHEMA %s"
        defaultSchemaName = "PUBLIC"
    }

    DefaultSchemaHandler(String useSchemaStatement, String createSchemaStatement, String defaultSchemaName) {
        this.useSchemaStatement = useSchemaStatement
        this.createSchemaStatement = createSchemaStatement
        this.defaultSchemaName = defaultSchemaName
    }

    @Override
    void useSchema(Connection connection, String name) {
        String useStatement = String.format(useSchemaStatement, name)
        log.debug("Executing SQL Set Schema Statement: ${useStatement}")
        connection
                .createStatement()
                .execute(useStatement)
    }

    @Override
    void useDefaultSchema(Connection connection) {
        useSchema(connection, defaultSchemaName)
    }

    @Override
    void createSchema(Connection connection, String name) {
        String schemaCreateStatement = String.format(createSchemaStatement, name)
        log.debug("Executing SQL Create Schema Statement: ${schemaCreateStatement}")
        connection
                .createStatement()
                .execute(schemaCreateStatement)
    }

    @Override
    Collection<String> resolveSchemaNames(DataSource dataSource) {
        Collection<String> schemaNames = []
        Connection connection = null
        try {
            connection = dataSource.getConnection()
            ResultSet schemas = connection.getMetaData().getSchemas()
            while(schemas.next()) {
                schemaNames.add(schemas.getString("TABLE_SCHEM"))
            }
        } finally {
            try {
                connection?.close()
            } catch (Throwable e) {
                log.debug("Error closing SQL connection: $e.message", e)
            }
        }
        return schemaNames
    }
}
