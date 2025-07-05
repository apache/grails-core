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

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class EmbeddedWithinEmbeddedAssociationSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Customer, Vehicle, Maker, Part, Component])
    }

    void "Test that nested embedded associations can be persisted"() {
        given: "a domain model with lots of nesting"
        def customer = new Customer(name: 'Kruttik Aggarwal').save(failOnError: true, flush: true)
        def maker = new Maker(name: 'Good Year').save(failOnError: true, flush: true)
        def vehicle1 = new Vehicle(type: 'car', owners: [customer], parts: [new Part(type: 'wheel', maker: maker)]).save(failOnError: true, flush: true)
        def vehicle2 = new Vehicle(type: 'truck', owners: [customer], parts: [new Part(type: 'headlight', maker: maker)]).save(failOnError: true, flush: true)
        customer.vehicles = [vehicle1, vehicle2]
        customer.save(failOnError: true, flush: true)
        def vehicle3 = new Vehicle(type: 'bus', owners: [customer], parts: [new Part(type: 'plate', maker: maker, components: [new Component(type: 'scribble')])]).save(failOnError: true, flush: true)

        manager.session.clear()

        when: "The domain model is queries"
        customer = Customer.findByName('Kruttik Aggarwal')

        then: "We get the right results back"
        customer != null
        customer.vehicles.size() == 2

        when: "we get the individual cars"
        def car = customer.vehicles.find { it.type == 'car' }
        def truck = customer.vehicles.find { it.type == 'truck' }

        then: "make sure those are present and valid"
        car != null
        truck != null
        car.parts.size() == 1
        car.parts.iterator().next().type == 'wheel'
        car.parts.iterator().next().maker != null
        truck.parts.size() == 1
        truck.parts.iterator().next().type == 'headlight'
        truck.parts.iterator().next().maker != null

        when: "we check the bus"
        def bus = Vehicle.findByType("bus")

        then: "the bus is valid too"
        bus != null
        bus.parts.size() == 1

        when: "We get the part of the bus"
        Part busPart = bus.parts.iterator().next()

        then: "Check that it is valid"
        busPart.type == "plate"
        busPart.maker.name == "Good Year"
        busPart.components.size() == 1
    }
}

@Entity
class Customer {

    Long id
    String name

    Set vehicles
    static hasMany = [vehicles: Vehicle]
}

@Entity
class Vehicle {
    Long id
    String type
    List<Part> parts = new ArrayList<Part>()

    Set owners
    static hasMany = [owners: Customer]
    static belongsTo = Customer
    static embedded = ['parts']
}

@Entity
class Part {
    Long id
    String type
    Maker maker
    List<Component> components = new ArrayList<Component>()
    static embedded = ['components']
    static belongsTo = Maker
}

@Entity
class Maker {
    Long id
    String name
}

@Entity
class Component {
    Long id
    String type
}
