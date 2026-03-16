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

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.JoinTable

import spock.lang.Unroll
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.TableForManyCalculator

class TableForManyCalculatorSpec extends HibernateGormDatastoreSpec {

    @Unroll
    def "Test calculateTableForMany for #scenario"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new BackticksRemover()

        def calculator = new TableForManyCalculator(namingStrategy, backticksRemover)

        GrailsHibernatePersistentEntity ownerEntityInstance
        GrailsHibernatePersistentEntity associatedEntityInstance
        HibernatePersistentProperty propertyToTest

        // Setup entities and properties based on scenario
        switch (scenario) {
            case "an owning OneToMany":
                ownerEntityInstance = createPersistentEntity(OwningSide)
                associatedEntityInstance = createPersistentEntity(AssociatedSide)
                propertyToTest = ownerEntityInstance.getPropertyByName("associated") as HibernatePersistentProperty
                break
            case "a Basic property":
                ownerEntityInstance = createPersistentEntity(BasicCollectionOwner)
                propertyToTest = ownerEntityInstance.getPropertyByName("items") as HibernatePersistentProperty
                break
            case "a Map property":
                ownerEntityInstance = createPersistentEntity(MapCollectionOwner)
                propertyToTest = ownerEntityInstance.getPropertyByName("data") as HibernatePersistentProperty
                break
            case "an owning ManyToMany":
                ownerEntityInstance = createPersistentEntity(OwningSide)
                associatedEntityInstance = createPersistentEntity(Tag)
                propertyToTest = ownerEntityInstance.getPropertyByName("tags") as HibernatePersistentProperty
                break
            case "an inverse ManyToMany":
                ownerEntityInstance = createPersistentEntity(Tag)
                associatedEntityInstance = createPersistentEntity(OwningSide)
                propertyToTest = ownerEntityInstance.getPropertyByName("owners") as HibernatePersistentProperty
                break
            case "a ManyToMany with explicit joinTable":
                ownerEntityInstance = createPersistentEntity(OwningSide)
                associatedEntityInstance = createPersistentEntity(Tag)
                propertyToTest = ownerEntityInstance.getPropertyByName("tags") as HibernatePersistentProperty
                propertyToTest.getMappedForm().setJoinTable(new JoinTable(name: "my_custom_join_table"))
                break
            case "a ToMany with supportsJoinColumnMapping":
                ownerEntityInstance = createPersistentEntity(UnidirectionalOwner)
                associatedEntityInstance = createPersistentEntity(UnidirectionalItem)
                propertyToTest = ownerEntityInstance.getPropertyByName("items") as HibernatePersistentProperty
                break
            default:
                throw new IllegalArgumentException("Unknown scenario: $scenario")
        }


        when:
        def result = calculator.calculateTableForMany(propertyToTest)

        then:
        result == expectedTableName

        where:
        scenario                              | expectedTableName

        "a Map property"                      | "map_collection_owner_data"
        "a Basic property"                    | "basic_collection_owner_items"
        "an owning OneToMany"                 | "owning_side_associated_side"
                "an owning ManyToMany"        | "tag_owners"
        "an inverse ManyToMany"               | "owning_side_tags" // MappedBy logic ensures single join table
        "a ManyToMany with explicit joinTable" | "my_custom_join_table"
        "a ToMany with supportsJoinColumnMapping" | "unidirectional_owner_unidirectional_item"
    }
}

@Entity
class AssociatedSide {
    static belongsTo = [owningSide: OwningSide]
}

@Entity
class OwningSide {
    static hasMany = [associated: AssociatedSide, tags: Tag] // For one-to-many and many-to-many
    static mappedBy = [tags: 'owners'] // For many-to-many inverse
}

@Entity
class BasicCollectionOwner {
    List<String> items
}


@Entity
class MapCollectionOwner {
    Map<String, String> data
}

@Entity
class Tag {
    static hasMany = [owners: OwningSide]
}

@Entity
class UnidirectionalItem {
    // No belongsTo, making it unidirectional
}

@Entity
class UnidirectionalOwner {
    static hasMany = [items: UnidirectionalItem]
}