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
import spock.lang.Issue

class HasOneSetInverseSideSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([House, HouseAddress])
    }

    @Issue('GRAILS-8757')
    void "Test that saving a one-to-one automatically sets the inverse side"() {
        when: "A bidirectional one-to-one is saved"
        def address = new HouseAddress(street: "Street 001")
        def house = new House(name: "Some house", address: address)

        house.save(flush: true)

        then: "The inverse side is autmotically set"
        house.id != null
        address.house != null

        when: "The association is queried"
        manager.session.clear()
        house = House.get(house.id)

        then: "The data model is valid"
        house.id != null
        house.address != null
        house.address.house != null
    }
}

@Entity
class House {
    Long id
    String name

    HouseAddress address
    static hasOne = [address: HouseAddress]
}

@Entity
class HouseAddress {
    Long id
    String street
    House house
}
