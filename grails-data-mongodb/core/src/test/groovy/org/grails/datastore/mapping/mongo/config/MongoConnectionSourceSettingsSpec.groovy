/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.mongo.config

import com.mongodb.ConnectionString
import com.mongodb.ReadPreference
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettingsBuilder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
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

    void "test index building is enabled unless it is switched off in configuration"() {
        when: "no buildIndexes setting is supplied"
        def settings = new MongoConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver([:])).build()

        then: "declared indexes are built on startup"
        settings.buildIndexes

        when: "the setting is switched off"
        def resolver = DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_BUILD_INDEXES): 'false'])
        settings = new MongoConnectionSourceSettingsBuilder(resolver).build()

        then: "index building is disabled"
        !settings.buildIndexes
    }

    void "test the index build is synchronous unless it is switched to asynchronous in configuration"() {
        when: "no buildIndexesAsync setting is supplied"
        def settings = new MongoConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver([:])).build()

        then: "the index build blocks the thread creating the datastore"
        !settings.buildIndexesAsync

        when: "the setting is switched on"
        def resolver = DatastoreUtils.createPropertyResolver([(MongoSettings.SETTING_BUILD_INDEXES_ASYNC): 'true'])
        settings = new MongoConnectionSourceSettingsBuilder(resolver).build()

        then: "the index build is asynchronous"
        settings.buildIndexesAsync
    }

    void "test MongoDB setting names require the documented camel case spelling"() {
        given:
        def defaults = new MongoConnectionSourceSettings()

        when:
        def settings = new MongoConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver([
                'grails.mongodb.database-name': 'ignoredDb',
                'grails.mongodb.build-indexes': false,
                'grails.mongodb.build-indexes-async': true
        ])).build()

        then:
        settings.databaseName == defaults.databaseName
        settings.buildIndexes
        !settings.buildIndexesAsync

        when:
        settings = new MongoConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver([
                'grails.mongodb.databaseName': 'configuredDb',
                'grails.mongodb.buildIndexes': false,
                'grails.mongodb.buildIndexesAsync': true
        ])).build()

        then:
        settings.databaseName == 'configuredDb'
        !settings.buildIndexes
        settings.buildIndexesAsync
    }

    void "test a kebab-case setting name is not relaxed-bound under Spring Boot either"() {
        given: "the environment a Spring Boot application hands GORM, with Boot's relaxed-binding source attached"
        def environment = new StandardEnvironment()
        environment.propertySources.addLast(new MapPropertySource('application.yml', [
                'grails.mongodb.database-name'      : 'kebabDb',
                'grails.mongodb.build-indexes'      : 'false',
                'grails.mongodb.build-indexes-async': 'true'
        ] as Map<String, Object>))
        ConfigurationPropertySources.attach(environment)

        expect: "the value is there under the name it was written with, but not under the one GORM asks for"
        environment.getProperty('grails.mongodb.database-name') == 'kebabDb'
        environment.getProperty('grails.mongodb.databaseName') == null

        when:
        def settings = new MongoConnectionSourceSettingsBuilder(environment).build()

        then: "the settings are looked up by their camel case names, which Boot's source does not answer"
        settings.databaseName == new MongoConnectionSourceSettings().databaseName
        settings.buildIndexes
        !settings.buildIndexesAsync
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

    void "test getUrl builds a connection string from host, port and credentials when no url is set"() {
        given:
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettings(
                host: 'mycompany', port: 1234, username: 'foo', password: 'bar', databaseName: 'mydb')

        expect:
        settings.url.toString() == 'mongodb://foo:bar@mycompany:1234/mydb'
    }

    void "test getUrl omits credentials when only one of username/password is set"() {
        given:
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettings(
                host: 'mycompany', port: 1234, username: 'foo', password: null, databaseName: 'mydb')

        expect:
        settings.url.toString() == 'mongodb://mycompany:1234/mydb'
    }

    void "test getUrl omits the port when it is not set"() {
        given:
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettings(
                host: 'mycompany', port: null, databaseName: 'mydb')

        expect:
        settings.url.toString() == 'mongodb://mycompany/mydb'
    }

    void "test getDatabase falls back to databaseName when no url is set"() {
        given:
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettings(databaseName: 'mydb')

        expect:
        settings.database == 'mydb'
    }

    void "test getDatabase falls back to databaseName when the url has no database segment"() {
        given:
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettings(databaseName: 'fallbackDb')
        settings.url(new ConnectionString('mongodb://mycompany:1234'))

        expect:
        settings.database == 'fallbackDb'
    }

}
