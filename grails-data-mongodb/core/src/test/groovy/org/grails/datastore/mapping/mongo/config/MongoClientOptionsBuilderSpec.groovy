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
