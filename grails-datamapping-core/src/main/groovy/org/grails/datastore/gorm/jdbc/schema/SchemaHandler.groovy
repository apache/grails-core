package org.grails.datastore.gorm.jdbc.schema

import javax.sql.DataSource
import java.sql.Connection

/**
 * A resolver that helps resolve information about the database schema. Required by multi tenancy support
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface SchemaHandler {

    /**
     * @return Resolves the schema names
     */
    Collection<String> resolveSchemaNames(DataSource dataSource)

    /**
     * Uses the given schema. Defaults to "SET SCHEMA %s"
     */
    void useSchema(Connection connection, String name)

    /**
     * Uses the given schema. Defaults to "SET SCHEMA PUBLIC"
     */
    void useDefaultSchema(Connection connection)

    /**
     * Creates the given schema. Defaults to "CREATE SCHEMA %s"
     */
    void createSchema(Connection connection, String name)
}