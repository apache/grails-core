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

    void "test a named connection inherits the options the default connection customized, and keeps its own"() {
        given:
        def configuration = DatastoreUtils.createPropertyResolver([
                'grails.neo4j.options.maxConnectionPoolSize'            : '7',
                'grails.neo4j.connections.other.url'                    : 'bolt://localhost:7688',
                'grails.neo4j.connections.other.options.fetchSize'      : '50'
        ])
        Neo4jConnectionSourceSettings defaultSettings = new Neo4jConnectionSourceSettingsBuilder(configuration).build()

        when: "the named connection's settings are built with the default connection's as their fallback"
        Neo4jConnectionSourceSettings other = new Neo4jConnectionSourceSettingsBuilder(configuration,
                'grails.neo4j.connections.other', defaultSettings).build()
        Config config = other.options.build()

        then: "the driver's own defaults are not passed on as if they were settings, so the options build at all"
        notThrown(IllegalArgumentException)
        config.eventLoopThreads() == Config.defaultConfig().eventLoopThreads()

        and:
        config.maxConnectionPoolSize() == 7
        config.fetchSize() == 50
        other.url == 'bolt://localhost:7688'
    }
}
