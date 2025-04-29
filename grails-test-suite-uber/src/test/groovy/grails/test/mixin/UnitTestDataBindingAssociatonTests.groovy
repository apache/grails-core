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
package grails.test.mixin

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.persistence.Entity
import grails.artefact.Artefact
import spock.lang.Specification

class UnitTestDataBindingAssociatonTests extends Specification implements ControllerUnitTest<ShipController>, DataTest {

    void setupSpec() {
        mockDomains Ship2, Pirate2
    }

    void testBindingAssociationInUnitTest() {
        when:
        def pirate = new Pirate2(name: 'Joe')
        pirate.save(failOnError: true, validate: false)

        def ship = new Ship2(pirate: pirate).save(failOnError: true, validate: false)

        // TODO: This currently doesn't work. See GRAILS-9120
//        params."pirate" = [id: pirate.id, name: 'new name']
        params."pirate.id" = pirate.id
        params."pirate" = [name:'new name']
        params.id = ship.id
        controller.pirate()

        then:
        'new name' == ship.pirate.name
    }
}

@Entity
class Pirate2 {
    String name
}
@Entity
class Ship2 {
    Pirate2 pirate
}

@Artefact("Controller")
class ShipController {
    def pirate ={
        def shipInstance = Ship2.get(params.id)
        shipInstance.properties = params

        assert 'new name' == shipInstance.pirate.name
        render text: 'I am done'
    }
}
