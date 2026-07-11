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
package org.grails.datastore.gorm.neo4j

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceSettings
import spock.lang.Specification

class Neo4jMappingContextSpec extends Specification {

    void "properties are nullable by default"() {
        when:
        def entity = new Neo4jMappingContext(new Neo4jConnectionSourceSettings())
                .addPersistentEntity(Neo4jEntityWithName)

        then:
        entity.getPropertyByName('name').mapping.mappedForm.nullable
    }

    void "explicit constraints preserve the default nullable mapping"() {
        when:
        def entity = new Neo4jMappingContext(new Neo4jConnectionSourceSettings())
                .addPersistentEntity(ConstrainedNeo4jEntity)

        then:
        entity.getPropertyByName('name').mapping.mappedForm.nullable
    }

    void "default nullable can be disabled"() {
        given:
        def settings = new Neo4jConnectionSourceSettings()
        settings.default.nullable = false

        when:
        def entity = new Neo4jMappingContext(settings)
                .addPersistentEntity(Neo4jEntityWithName)

        then:
        !entity.getPropertyByName('name').mapping.mappedForm.nullable
    }
}

@Entity
class Neo4jEntityWithName {
    String name
}

@Entity
class ConstrainedNeo4jEntity {
    String name

    static constraints = {
        name maxSize: 100
    }
}
