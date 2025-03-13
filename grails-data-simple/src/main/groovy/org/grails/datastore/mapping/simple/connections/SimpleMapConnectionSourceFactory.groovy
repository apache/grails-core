package org.grails.datastore.mapping.simple.connections

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.connections.*
import org.springframework.core.env.PropertyResolver

import java.util.concurrent.ConcurrentHashMap

/**
 * Simple implementation that just builds {@link ConnectionSource} instances from Maps
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class SimpleMapConnectionSourceFactory extends AbstractConnectionSourceFactory<Map<String,Map>, ConnectionSourceSettings> {
    @Override
    ConnectionSource<Map<String, Map>, ConnectionSourceSettings> create(String name, ConnectionSourceSettings settings) {
        return new DefaultConnectionSource<Map<String,Map>, ConnectionSourceSettings>(name, new ConcurrentHashMap<String, Map>(), settings)
    }
    @Override
    Serializable getConnectionSourcesConfigurationKey() {
        return PREFIX + ".connections"
    }

    @Override
    protected <F extends ConnectionSourceSettings> ConnectionSourceSettings buildSettings(String name, PropertyResolver configuration, F fallbackSettings, boolean isDefaultDataSource) {
        ConnectionSourceSettingsBuilder builder = new ConnectionSourceSettingsBuilder(configuration, PREFIX)
        return builder.build()
    }
}
