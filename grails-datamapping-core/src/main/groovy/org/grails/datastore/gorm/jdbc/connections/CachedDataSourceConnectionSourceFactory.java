package org.grails.datastore.gorm.jdbc.connections;

import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.springframework.core.env.PropertyResolver;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extends {@link DataSourceConnectionSourceFactory} and caches the created {@link DataSourceConnectionSource} instances ensuring they are singletons
 *
 * @author Graeme Rocher
 * @since 6.1.7
 */
public class CachedDataSourceConnectionSourceFactory extends DataSourceConnectionSourceFactory {
    private final Map<String, ConnectionSource<DataSource, DataSourceSettings>> dataSources = new LinkedHashMap<>();

    @Override
    public ConnectionSource<DataSource, DataSourceSettings> create(String name, PropertyResolver configuration) {
        if(dataSources.containsKey(name)) {
            return dataSources.get(name);
        }
        else {
            ConnectionSource<DataSource, DataSourceSettings> connectionSource = super.create(name, configuration);
            dataSources.put(name, connectionSource);
            return connectionSource;
        }
    }

    @Override
    public ConnectionSource<DataSource, DataSourceSettings> create(String name, DataSourceSettings settings) {
        if(dataSources.containsKey(name)) {
            return dataSources.get(name);
        }
        else {
            ConnectionSource<DataSource, DataSourceSettings> connectionSource = super.create(name, settings);
            dataSources.put(name, connectionSource);
            return connectionSource;
        }
    }
}
