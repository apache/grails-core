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


import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.grails.datastore.gorm.neo4j.RelationshipUtils

/**
 * Created by stefan on 14.04.14.
 */
class RelationshipUtilsSpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(Person, Pet)
    }

    def "unidirectional associations are never reversed"() {

        setup:
            def person = manager.session.mappingContext.getPersistentEntity(Person.class.name)
            def pets = person.getPropertyByName("pets")
            def pet = manager.session.mappingContext.getPersistentEntity(Pet.class.name)
            def owner = pet.getPropertyByName("owner")

        expect:
            pets.bidirectional
            owner.bidirectional
            RelationshipUtils.useReversedMappingFor(pets) == false
            RelationshipUtils.useReversedMappingFor(owner) == true
    }


}
