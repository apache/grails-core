package org.grails.datastore.mapping.mongo.config

import com.mongodb.MongoClientSettings
import com.mongodb.ReadPreference
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

/**
 * Created by graemerocher on 13/06/16.
 */
class MongoClientOptionsBuilderSpec extends Specification {

    void "test mongo client settings builder"() {
        when:"using a property resolver"
        StandardEnvironment resolver = new StandardEnvironment()
        Map myMap = ['grails.mongodb.options.readPreference': 'secondary',
                     'grails.mongodb.host': 'mycompany',
                     'grails.mongodb.port': '1234',
                     'grails.mongodb.username': 'foo',
                     'grails.mongodb.password': 'bar',
                     'grails.mongodb.options.clusterSettings.maxWaitQueueSize': '10']
        resolver.propertySources.addFirst(new MapPropertySource("test", myMap))

        def builder = new MongoClientOptionsBuilder(resolver)
        MongoClientSettings clientSettings = builder.build().build()

        then:"The settings are correct"
        clientSettings.readPreference == ReadPreference.secondary()
    }
}
