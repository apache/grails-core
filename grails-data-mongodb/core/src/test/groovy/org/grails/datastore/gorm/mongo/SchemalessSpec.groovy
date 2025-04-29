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

import grails.gorm.tests.Plant
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class SchemalessSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses += [Plant]
    }

    def "Test attach additional data"() {
        given:
        def p = new Plant(name: "Pineapple")
        p['color'] = "Yellow"
        p.save(flush: true)
        manager.session.clear()

        when:
        p = Plant.get(p.id)

        then:
        p.name == 'Pineapple'
        p.dbo.color == 'Yellow'
        p['color'] == 'Yellow'

        when:
        p['hasLeaves'] = true
        p.save(flush: true)
        manager.session.clear()
        p = Plant.get(p.id)

        then:
        p.name == 'Pineapple'
        p.dbo.color == 'Yellow'
        p['color'] == 'Yellow'
        p['hasLeaves'] == true

        when: "All objects are listed"
        manager.session.clear()
        def results = Plant.list()

        then: "The right data is returned and the schemaless properties accessible"
        results.size() == 1
        results[0].name == 'Pineapple'
        results[0]['color'] == 'Yellow'

        when: "A groovy finderAll method is executed"
        def newResults = results.findAll { it['color'] == 'Yellow' }

        then: "The embedded data is stil there"
        newResults.size() == 1
        newResults[0].name == 'Pineapple'
        newResults[0]['color'] == 'Yellow'

        when: "A dynamic finder is used on a schemaless property"
        manager.session.clear()
        def plant = Plant.findByColor("Yellow")

        then: "The dynamic finder works"
        plant.name == "Pineapple"

        when: "A criteria query is used on a schemaless property"
        manager.session.clear()
        plant = Plant.createCriteria().get {
            eq 'color', 'Yellow'
        }

        then: "The criteria query works"
        plant.name == "Pineapple"

        when: "A dynamic property is accessed via a getter"
        def color = plant.color

        then: "The getter works"
        color == 'Yellow'

        when: "A dynamic property is set via a setter"
        plant.color = "Red"

        then: "The setter works"
        plant.color == 'Red'

    }
}
