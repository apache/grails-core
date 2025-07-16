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

package grails.gorm.tests.path

import grails.gorm.tests.path.domain.Person
import grails.gorm.transactions.Rollback
import grails.neo4j.Path
import grails.neo4j.Relationship
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 16/03/2017.
 */
class RelationshipSpec extends Specification {
    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(getClass().getPackage())

    @Rollback
    void "test find relationship"() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
                .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        // tag::findRelationship[]
        String from = "Fred"
        String to = "Barney"
        Relationship<Person, Person> rel = Person.findRelationship(Person.proxy(from), Person.proxy(to))
        // end::findRelationship[]

        then:
        rel.from.name == "Fred"
        rel.to.name == "Barney"
        rel.type == "FRIENDS"
        rel.id != null
    }

    @Rollback
    void "test find relationships"() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
                .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        // tag::findRelationships[]
        String from = "Fred"
        String to = "Barney"
        List<Relationship<Person, Person>> rels = Person.findRelationships(Person.proxy(from), Person.proxy(to))
        // end::findRelationships[]

        then:
        rels.size() == 1
        rels.first().from.name == "Fred"
        rels.first().to.name == "Barney"
        rels.first().type == "FRIENDS"
        rels.first().id != null
    }

    @Rollback
    void "test find relationships for types"() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
                .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        // tag::findRelationshipsForTypes[]
        List<Relationship<Person, Person>> rels = Person.findRelationships(Person, Person, [max:10])
        for(rel in rels) {
            println("Type ${rel.type}")
            println("From ${rel.from.name} to ${rel.to.name}")
        }
        // end::findRelationshipsForTypes[]

        then:
        rels.size() == 4
        rels.first().from.name != null
        rels.first().to.name != null
        rels.first().type == "FRIENDS"
        rels.first().id != null
    }
}
