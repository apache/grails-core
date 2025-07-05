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
import spock.lang.Issue

class EmbeddedUnsetSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([EmbeddedPetOwner, EmbeddedPet])
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/718')
    void "Test unset value from embedded collection"() {
        given:
        EmbeddedPetOwner o = new EmbeddedPetOwner(name: "bob", pets: [new EmbeddedPet(name: "fido")])
        o.save(flush: true)

        manager.session.clear()

        when:
        EmbeddedPetOwner o2 = EmbeddedPetOwner.findByName("bob")

        then:
        o2.pets[0].name == "fido"

        when:
        o2.pets[0].name = null
//        o2.markDirty('pets')
        o2.save(flush: true)

        then:
        !o2.hasErrors()
        o2.pets[0].name == null

        manager.session.clear()

        when:
        EmbeddedPetOwner o3 = EmbeddedPetOwner.findByName("bob")
        then:
        o3.pets[0].name == null
    }
}

@Entity
class EmbeddedPetOwner {
    String name

    List<EmbeddedPet> pets
    static embedded = ['pets']
}

@Entity
class EmbeddedPet {
    String name
    static constraints = {
        name nullable: true
    }
}