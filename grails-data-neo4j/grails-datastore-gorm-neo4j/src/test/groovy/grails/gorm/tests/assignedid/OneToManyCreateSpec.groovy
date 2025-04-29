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

package grails.gorm.tests.assignedid

import grails.gorm.tests.assignedid.domain.Owner
import grails.gorm.tests.assignedid.domain.Pet
import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 14/03/2017.
 */
class OneToManyCreateSpec extends Specification {
    // tag::setup[]
    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(getClass().getPackage())
    // end::setup[]

    @Rollback
    void "test save one-to-many"() {
        given:
        List<Owner> deleteOwners = []
        // tag::save[]
        deleteOwners << new Owner(name:"Fred")
                .addToPets(name: "Dino")
                .addToPets(name: "Joe")
                .save()
        deleteOwners << new Owner(name:"Barney")
                .addToPets(name: "Hoppy")
                .save(flush:true)
        // end::save[]
        Owner.withSession { it.clear() }

        expect:
        Owner.count == 2
        Owner.findByName("Fred").pets.size() == 2
        Owner.findByName("Barney").pets.size() == 1

        cleanup:
        Owner.deleteAll(deleteOwners)
    }
}
