package org.grails.datastore.gorm.neo4j.connections

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.mapping.config.ConfigurationBuilder
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.springframework.core.env.PropertyResolver

/**
 * A builder for creating {@link Neo4jConnectionSourceSettings} objects
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class Neo4jConnectionSourceSettingsBuilder extends ConfigurationBuilder<Neo4jConnectionSourceSettings, Neo4jConnectionSourceSettings> {

    Neo4jConnectionSourceSettingsBuilder(PropertyResolver propertyResolver, String configurationPrefix = Settings.PREFIX, ConnectionSourceSettings fallBackConfiguration = null) {
        super(propertyResolver, configurationPrefix, fallBackConfiguration)
    }

    @Override
    protected Neo4jConnectionSourceSettings createBuilder() {
        return new Neo4jConnectionSourceSettings()
    }

    @Override
    protected Neo4jConnectionSourceSettings toConfiguration(Neo4jConnectionSourceSettings builder) {
        return builder
    }
}
