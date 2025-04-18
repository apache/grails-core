package org.grails.datastore.gorm.neo4j.config

import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceSettings
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceSettingsBuilder
import org.grails.datastore.mapping.core.DatastoreUtils
import org.neo4j.driver.Config
import spock.lang.Specification

/**
 * Created by graemerocher on 04/07/16.
 */
class Neo4jConnectionSourceSettingsSpec extends Specification {

    void "test neo4j connection source settings"() {
        when:"A connection source settings is built"

        Map myMap = ['grails.neo4j.options.maxConnectionPoolSize': '10',
                     'grails.neo4j.options.encrypted': false,
                     'grails.neo4j.embedded.options': [foo:'bar']]

        Neo4jConnectionSourceSettingsBuilder builder = new Neo4jConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver(myMap))

        Neo4jConnectionSourceSettings settings = builder.build()


        then:"The settings are correct"
        settings.options.build().maxConnectionPoolSize() == 10
        !settings.options.build().encrypted()
        settings.embedded.options == [foo: 'bar']

    }
}
