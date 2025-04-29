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

package grails.gorm.tests.cypher

import grails.gorm.tests.cypher.domain.*
import grails.gorm.tests.cypher.domain.Pet
import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import spock.lang.*

class OneToManyCreateSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(Owner, Pet)

    @Rollback
    void "test save one-to-many"() {
        given:
        // tag::save[]
        Owner owner = new Owner(name:"David")
            .addToPets(name: "Dino")
            .save(flush:true)
            .discard()
        // end::save[]
        expect:
        Owner.count == 1
        Owner.first().pets.size() == 1

        cleanup:
        Owner.deleteAll(owner)
    }

    @Rollback
    void "test save one-to-many with dynamic attributes"() {

        given:
        def owner = new Owner(name: "John")
                            .addToPets(name: "Dino")
        owner.age = 40
        owner
            .save(flush:true)
            .discard()
        def result = Owner.first()

        expect:
        Owner.count == 1
        result.pets.size() == 1
        result.name == "John"
        result.age == 40

        cleanup:
        Owner.deleteAll(owner)
    }
}
