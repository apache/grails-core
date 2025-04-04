package org.grails.datastore.gorm.internal

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.model.DatastoreConfigurationException

/**
 * Utility methods to support AST transforms at runtime
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class RuntimeSupport {

    /**
     * Finds the default datastore from an array of datastores
     *
     * @param datastores The default datastore
     * @return
     */
    static Datastore findDefaultDatastore(Datastore[] datastores) {
        for(Datastore d in datastores) {
            if( d instanceof ConnectionSourcesProvider) {
                ConnectionSourcesProvider provider = (ConnectionSourcesProvider)d
                if(ConnectionSource.DEFAULT == provider.getConnectionSources().defaultConnectionSource.name) {
                    return d
                }
            }
        }
        if(datastores) {
            return datastores[0]
        }
        throw new DatastoreConfigurationException("No default datastore configured")
    }
}
