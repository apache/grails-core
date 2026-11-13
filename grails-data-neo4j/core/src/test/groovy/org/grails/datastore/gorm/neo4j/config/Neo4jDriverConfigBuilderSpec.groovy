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

import org.neo4j.driver.Config
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

/**
 * Created by graemerocher on 08/06/16.
 */
class Neo4jDriverConfigBuilderSpec extends Specification {

    void "test neo4j client settings builder"() {
        when:"using a property resolver"
        StandardEnvironment resolver = new StandardEnvironment()
        Map myMap = ['grails.neo4j.options.maxConnectionPoolSize': '10',
                     'grails.neo4j.options.encryption': true]
        resolver.propertySources.addFirst(new MapPropertySource("test", myMap))

        def builder = new Neo4jDriverConfigBuilder(resolver)
        Config clientSettings = builder.build()

        then:"The settings are correct"
        clientSettings.maxConnectionPoolSize() == 10
        clientSettings.encrypted()
    }
}
