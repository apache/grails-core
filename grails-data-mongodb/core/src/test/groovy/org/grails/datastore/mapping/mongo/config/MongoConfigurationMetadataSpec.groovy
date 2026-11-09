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

import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings

/**
 * The configuration metadata the plugin publishes is what an IDE shows as each setting's default, so it has to state
 * the default the settings actually have.
 */
class MongoConfigurationMetadataSpec extends Specification {

    /**
     * Published by the grails-plugin module, which has no tests of its own; read from its sources.
     */
    private static final File METADATA =
            new File('../grails-plugin/src/main/resources/META-INF/additional-spring-configuration-metadata.json')

    @Shared
    Map<String, Object> publishedDefaults

    void setupSpec() {
        assert METADATA.isFile()
        Map metadata = (Map) new JsonSlurper().parse(METADATA)
        publishedDefaults = ((List<Map>) metadata.get('properties')).collectEntries { Map property ->
            [(property.get('name')): property.get('defaultValue')]
        }
    }

    @Unroll
    void 'test the metadata states the default of #setting that the settings have'() {
        expect:
        publishedDefaults.containsKey(setting)
        publishedDefaults[setting] == new MongoConnectionSourceSettings()."$property"

        where:
        setting                            | property
        'grails.mongodb.stateless'         | 'stateless'
        'grails.mongodb.decimalType'       | 'decimalType'
        'grails.mongodb.buildIndexes'      | 'buildIndexes'
        'grails.mongodb.buildIndexesAsync' | 'buildIndexesAsync'
        'grails.mongodb.host'              | 'host'
        'grails.mongodb.port'              | 'port'
    }

    void 'test the metadata states the url the settings connect to when none is configured'() {
        expect:
        publishedDefaults['grails.mongodb.url'] == new MongoConnectionSourceSettings().url.toString()
    }
}
