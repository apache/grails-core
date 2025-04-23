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

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.Issue

class AddToAndInjectedServiceSpec extends GormDatastoreSpec {

    @Issue('GRAILS-9119')
    void "Test add to method with injected service present"() {
        given:"A domain with an addTo relationship"
            def pirate = new Pirate(name: 'Billy')
            def ship = new Ship()
        when:"The addTo method is called"
            ship.addToPirates(pirate)

        then:"It adds an associated entity correctly"
            assert 1 == ship.pirates.size()
    }

    @Override
    List getDomainClasses() {
        [Pirate, Ship]
    }
}

@Entity
class Pirate {
    Long id
    String name
    def pirateShipService
}

@Entity
class Ship {
    Long id
    Set pirates
    static hasMany = [pirates: Pirate]
}
