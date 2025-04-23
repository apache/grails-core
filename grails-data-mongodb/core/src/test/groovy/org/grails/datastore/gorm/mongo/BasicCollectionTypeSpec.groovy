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

package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class BasicCollectionTypeSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [MyCollections]
    }

    def "Test persist basic collection types"() {
        given:"An entity persisted with basic collection types"
            def mc = new MyCollections(names:['Bob', 'Charlie'], pets:[chuck:"Dog", eddie:'Parrot'])
            mc.save(flush:true)

            session.clear()

        when:"When the object is read"
            mc = MyCollections.get(mc.id)

        then:"The basic collection types are populated correctly"
            mc.names != null
            mc.names == ['Bob', 'Charlie']
            mc.names.size() > 0
            mc.pets != null
            mc.pets.size() == 2
            mc.pets.chuck == "Dog"

        when:"The object is updated"
            mc.names << "Fred"
            mc.pets.joe = "Turtle"
            mc.save(flush:true)
            session.clear()
            mc = MyCollections.get(mc.id)

        then:"The basic collection types are correctly updated"
            mc.names != null
            mc.names == ['Bob', 'Charlie', 'Fred']
            mc.names.size() > 0
            mc.pets != null
            mc.pets.size() == 3
            mc.pets.chuck == "Dog"

        when:"An entity is queried by a basic collection type"
            session.clear()
            mc = MyCollections.findByNames("Bob")

        then:"The correct result is returned"

            mc.names != null
            mc.names == ['Bob', 'Charlie', 'Fred']
            mc.names.size() > 0
            mc.pets != null
            mc.pets.size() == 3
            mc.pets.chuck == "Dog"

        when:"A collection of strings is queried by GString"
            session.clear()
            mc = MyCollections.findByNames("${'Bob'}")
        then:"The correct result is returned"
            mc != null

        when:"An entity with a basic collection type is deleted"
            mc.delete(flush:true)

        then:'The delete works'
            MyCollections.count() == 0
    }
}

@Entity
class MyCollections {
    Long id
    List<String> names = []
    Map pets = [:]
}
