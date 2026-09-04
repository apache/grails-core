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
