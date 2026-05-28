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

import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class OneToManyWithInheritanceSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Animal, Donkey, Carrot])
    }

    void "Test that a one-to-many with inheritances behaves correctly"() {
        given: "A one-to-many association inherited from a parent"
        Animal animal = new Animal().save()
        Donkey donkey = new Donkey(name: "Eeyore").save()
        new Carrot(leaves: 1, animal: animal).save()
        new Carrot(leaves: 2, animal: animal).save()
        new Carrot(leaves: 3, animal: donkey).save()
        new Carrot(leaves: 4, animal: donkey).save(flush: true)
        manager.session.clear()

        when: "The association is loaded"
        animal = Animal.get(animal.id)
        donkey = Donkey.get(donkey.id)

        then: "The association is correctly loaded"
        animal.carrots.size() == 2
        donkey.carrots.size() == 2
    }
}

@Entity
class Donkey extends Animal implements MongoEntity<Donkey> {
    String name
}

@Entity
class Animal implements MongoEntity<Animal> {
    String id
    Set carrots = []
    static hasMany = [carrots: Carrot]
}

@Entity
class Carrot implements MongoEntity<Carrot> {
    Long id
    Integer leaves
    Animal animal
    static belongsTo = [animal: Animal]
}
