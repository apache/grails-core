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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity
import spock.lang.Ignore

/**
 * Created by graemerocher on 09/09/2016.
 */
@Ignore
class MapPropertySpec extends GormDatastoreSpec {

    @Ignore
    void "Test persist map property"() {
        when:"a object with a map property is persisted"

        new Animal(name: 'Dog', attributes: [legs:4]).save(flush:true)
                                                     .discard()
        Animal a = Animal.first()

        then:
        a.name == "Dog"
        a.attributes.size() == 1
    }

    @Override
    List getDomainClasses() {
        [Animal]
    }
}

@Entity
class Animal implements Neo4jEntity<Animal> {
    String name
    Map attributes
}
