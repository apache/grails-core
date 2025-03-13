package org.grails.datastore.mapping.mongo.config

import com.mongodb.ReadPreference
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettingsBuilder
import spock.lang.Specification
/**
 * Created by graemerocher on 29/06/16.
 */
class MongoConnectionSourceSettingsSpec extends Specification {

    void "test mongo client settings builder"() {
        when:"using a property resolver"
        Map myMap = ['grails.mongodb.options.readPreference': 'secondary',
                     'grails.mongodb.host': 'mycompany',
                     'grails.mongodb.port': '1234',
                     'grails.mongodb.username': 'foo',
                     'grails.mongodb.password': 'bar',
                     'grails.mongodb.options.clusterSettings.maxWaitQueueSize': '10']

        def builder = new MongoConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver(myMap))
        MongoConnectionSourceSettings settings = builder.build()

        then:"The settings are correct"
        builder.clientOptionsBuilder
        settings.host == 'mycompany'
        settings.password == 'bar'
        settings.username == 'foo'
        settings.port == 1234
        settings.options.build().readPreference == ReadPreference.secondary()
    }

    void "test mongo client settings builder with URL"() {
        when:"using a property resolver"
        Map myMap = ['grails.mongodb.url': 'mongodb://foo:bar@mycompany/mydb?maxPoolSize=5']

        def builder = new MongoConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver(myMap))
        MongoConnectionSourceSettings settings = builder.build()

        then:"The settings are correct"
        builder.clientOptionsBuilder
        settings.url != null
        settings.url.database == 'mydb'
        settings.url.username == 'foo'
        settings.url.password == 'bar'.toCharArray()
        settings.url.maxConnectionPoolSize == 5

    }

}
