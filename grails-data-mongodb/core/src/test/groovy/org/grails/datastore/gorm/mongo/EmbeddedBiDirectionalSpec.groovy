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

import grails.gorm.annotation.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Created by Jim on 8/18/2016.
 */
class EmbeddedBiDirectionalSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([EBDDogOwner, EBDDog, EBDToy])
    }

    void "test nested backreferences"() {
        when: "A domain is created with nested embedded collections"
        def owner = new EBDDogOwner(name: "Joe")
        EBDDog dog = new EBDDog(name: "Rex")
        dog.addToToys(manufacturer: 'Mattel')
        owner.addToDogs(dog)
        owner.save(flush: true)
        manager.session.clear()

        owner = EBDDogOwner.findByName("Joe")

        then: "All entities are saved with back references"
        owner != null
        owner.dogs.size() == 1
        owner.dogs[0].owner != null
        owner.dogs[0].toys.size() == 1
        owner.dogs[0].toys[0].dog != null
    }
}

@Entity
class EBDDogOwner {
    String name
    static hasMany = ['dogs': EBDDog]
    static embedded = ['dogs']
}

@Entity
class EBDDog {
    String name
    static belongsTo = ['owner': EBDDogOwner]
    static hasMany = ['toys': EBDToy]
    static embedded = ['toys']
}

@Entity
class EBDToy {
    String manufacturer
    static belongsTo = ['dog': EBDDog]
}