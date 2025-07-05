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
package org.grails.datastore.gorm

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class CircularManyToManySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([CircularPerson])
    }

    void "Test that a circular one-to-many persists correctly"() {
        when: "A domain model with circular one-to-manys is created and queried"
        def p1 = new CircularPerson(name: "Fred").save()
        def p2 = new CircularPerson(name: "Bob").save()
        def p3 = new CircularPerson(name: "Joe").save()
        def p4 = new CircularPerson(name: "Homer").save()

        p1.addToFriends(p2)
        p1.addToFriends(p3)

        p1.addToEnemies(p4)

        p1.save(flush: true)

        p2.addToEnemies(p1)

        p2.save(flush: true)
        manager.session.clear()

        p1 = CircularPerson.findByName("Fred")
        p2 = CircularPerson.findByName("Bob")

        then: "The persisted model is correct"
        p1.name == "Fred"
        p1.friends.size() == 2
        p1.friends.find { it.name == 'Bob' }
        p1.friends.find { it.name == 'Joe' }
        p1.enemies.size() == 1
        p1.enemies.find { it.name == 'Homer' }
        p2.enemies.size() == 1
        p2.enemies.find { it.name == 'Fred' }
    }
}

@Entity
class CircularPerson {

    Long id
    String name
    List<CircularPerson> friends = []

    static hasMany = [
            friends: CircularPerson,
            enemies: CircularPerson
    ]

    static mapping = {
        friends joinTable: "person_friends"
        enemies joinTable: "person_enemies"
    }
}
