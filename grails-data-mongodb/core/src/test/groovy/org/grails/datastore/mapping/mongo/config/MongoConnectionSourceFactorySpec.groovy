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

import com.mongodb.client.MongoClient
import com.mongodb.ReadPreference
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesInitializer
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceFactory
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings
import spock.lang.Specification

/**
 * Created by graemerocher on 30/06/16.
 */
class MongoConnectionSourceFactorySpec extends Specification {

    void "Test MongoDB connection sources factory creates the correct configuration"() {
        when:"A factory instance"
        MongoConnectionSourceFactory factory = new MongoConnectionSourceFactory()
        ConnectionSources<MongoClient, MongoConnectionSourceSettings> sources = ConnectionSourcesInitializer.create(factory, DatastoreUtils.createPropertyResolver(
                (MongoSettings.SETTING_URL): "mongodb://localhost/myDb",
                (MongoSettings.SETTING_CONNECTIONS): [
                        another:[
                                url:"mongodb://localhost/anotherDb"
                        ]
                ]
        ))

        then:"The connection sources are correct"
        sources.defaultConnectionSource.name == ConnectionSource.DEFAULT
        sources.defaultConnectionSource.settings.url.database == 'myDb'
        sources.allConnectionSources.size() == 2
        sources.getConnectionSource('another').settings.url.database == 'anotherDb'

        cleanup:
        sources?.close()
    }

    void "test mongo client settings builder with fallback"() {
        when:"using a property resolver"
        Map myMap = ['grails.mongodb.options.readPreference': 'secondary',
                     (MongoSettings.SETTING_URL): "mongodb://localhost/myDb",
                     'grails.mongodb.options.clusterSettings.maxWaitQueueSize': '10',
                     (MongoSettings.SETTING_CONNECTIONS): [
                             another:[
                                     url:"mongodb://localhost/anotherDb"
                             ]
                     ]]

        MongoConnectionSourceFactory factory = new MongoConnectionSourceFactory()
        ConnectionSources<MongoClient, MongoConnectionSourceSettings> sources = ConnectionSourcesInitializer.create(factory, DatastoreUtils.createPropertyResolver(myMap))


        then:"The connection sources are correct"
        sources.defaultConnectionSource.name == ConnectionSource.DEFAULT
        sources.defaultConnectionSource.settings.url.database == 'myDb'
        sources.defaultConnectionSource.settings.options.build().readPreference == ReadPreference.secondary()
        sources.allConnectionSources.size() == 2
        sources.getConnectionSource('another').settings.url.database == 'anotherDb'
        sources.getConnectionSource('another').settings.options.build().readPreference == ReadPreference.secondary()

        cleanup:
        sources?.close()
    }

}
