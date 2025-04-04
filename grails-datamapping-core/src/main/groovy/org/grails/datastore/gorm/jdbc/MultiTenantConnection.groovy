package org.grails.datastore.gorm.jdbc

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException

/**
 * Allows restoring the target schema prior to releasing the connection to the pool in Multi-Tenant environment
 *
 * @author Graeme
 * @since 6.1.7
 */
@CompileStatic
class MultiTenantConnection implements Connection {

    final @Delegate Connection target
    final SchemaHandler schemaHandler

    MultiTenantConnection(Connection target, SchemaHandler schemaHandler) {
        this.target = target
        this.schemaHandler = schemaHandler
    }

    @Override
    void close() throws SQLException {
        try {
            if(!isClosed()) {
                schemaHandler.useDefaultSchema(this)
            }
        } finally {
            target.close()
        }
    }
}
