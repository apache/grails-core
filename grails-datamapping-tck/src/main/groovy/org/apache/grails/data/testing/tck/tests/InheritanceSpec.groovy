/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.City
import org.apache.grails.data.testing.tck.domains.Country
import org.apache.grails.data.testing.tck.domains.FleetCar
import org.apache.grails.data.testing.tck.domains.FleetGarage
import org.apache.grails.data.testing.tck.domains.FleetSedan
import org.apache.grails.data.testing.tck.domains.FleetSportsCar
import org.apache.grails.data.testing.tck.domains.FleetVehicle
import org.apache.grails.data.testing.tck.domains.Location
import org.apache.grails.data.testing.tck.domains.Practice
import org.grails.datastore.mapping.proxy.ProxyHandler
import spock.lang.Issue

/**
 * @author graemerocher
 */
class InheritanceSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.registerDomainClasses(
                City, Country, Location, Practice,
                FleetVehicle, FleetCar, FleetSportsCar, FleetSedan, FleetGarage
        )
    }

    void 'Test inheritance with dynamic finder'() {

        given:
        def city = new City([code: 'UK', name: 'London', longitude: 49.1, latitude: 53.1])
        def country = new Country([code: 'UK', name: 'United Kingdom', population: 10000000])

        city.save()
        country.save(flush: true)
        manager.session.clear()

        when:
        def locations = Location.findAllByCode('UK')
        def cities = City.findAllByCode('UK')
        def countries = Country.findAllByCode('UK')

        then:
        2 == locations.size()
        1 == cities.size()
        1 == countries.size()
        'London' == cities[0].name
        'United Kingdom' == countries[0].name
    }

    void 'Test querying with inheritance'() {

        given:
        def city = new City([code: 'LON', name: 'London', longitude: 49.1, latitude: 53.1])
        def location = new Location([code: 'XX', name: 'The World'])
        def country = new Country([code: 'UK', name: 'United Kingdom', population: 10000000])

        country.save()
        city.save()
        location.save()

        manager.session.flush()

        when:
        city = City.get(city.id)
        def london = Location.get(city.id)
        country = Location.findByName('United Kingdom')
        def london2 = Location.findByName('London')

        then:
        1 == City.count()
        1 == Country.count()
        3 == Location.count()

        city != null
        city instanceof City
        london instanceof City
        london2 instanceof City
        'London' == london2.name
        49.1 == london2.longitude
        'LON' == london2.code

        country instanceof Country
        'UK' == country.code
        10000000 == country.population
    }

    void 'Test hasMany with inheritance should return appropriate class'() {
        given: 'a practice with two locations'
        Practice practice = new Practice(name: 'Test practice')
        practice.addToLocations(new City(name: 'Austin', latitude: 30.2672, longitude: 97.7431))
        practice.addToLocations(new Country(name: 'United States'))
        practice.save()
        manager.session.flush()

        expect:
        Location.findByName('Austin').class == City
    }

    @Issue('https://github.com/apache/grails-core/issues/16464')
    void 'Test querying an intermediate class returns the instances of its subclasses'() {
        given: 'a hierarchy with an instance of every class'
        new FleetVehicle(name: 'vehicle').save()
        def car = new FleetCar(name: 'car').save()
        def sportsCar = new FleetSportsCar(name: 'sports car').save()
        def sedan = new FleetSedan(name: 'sedan').save(flush: true)
        manager.session.clear()

        expect: 'queries on the intermediate class include its subclasses'
        FleetCar.count() == 3
        FleetCar.list()*.name.sort() == ['car', 'sedan', 'sports car']
        FleetCar.findAllByNameLike('s%')*.name.sort() == ['sedan', 'sports car']
        FleetCar.withCriteria { like('name', '%car%') }*.name.sort() == ['car', 'sports car']

        and: 'loading through the intermediate class returns the subclass instance'
        FleetCar.get(car.id).getClass() == FleetCar
        FleetCar.get(sportsCar.id) instanceof FleetSportsCar
        FleetCar.get(sedan.id) instanceof FleetSedan

        and: 'queries on the root and the leaves are unchanged'
        FleetVehicle.count() == 4
        FleetSportsCar.list()*.name == ['sports car']
        FleetSedan.count() == 1
    }

    void 'Test loading a superclass instance through a subclass returns null'() {
        given:
        def vehicle = new FleetVehicle(name: 'vehicle').save()
        def location = new Location(name: 'The World').save(flush: true)
        manager.session.clear()

        expect:
        FleetCar.get(vehicle.id) == null
        FleetSportsCar.get(vehicle.id) == null
        City.get(location.id) == null
    }

    @Issue('https://github.com/apache/grails-core/issues/16464')
    void 'Test associations typed to an intermediate class load instances of its subclasses'() {
        given: 'a garage referencing subclasses of the intermediate class'
        def sportsCar = new FleetSportsCar(name: 'sports car').save()
        def sedan = new FleetSedan(name: 'sedan').save()
        def garage = new FleetGarage(name: 'garage', car: sportsCar)
        garage.addToCars(sportsCar)
        garage.addToCars(sedan)
        garage.save(flush: true)
        manager.session.clear()

        when:
        ProxyHandler proxyHandler = manager.session.mappingContext.proxyHandler
        garage = FleetGarage.get(garage.id)

        then: 'the single-ended association loads the subclass instance'
        garage.car.name == 'sports car'
        proxyHandler.unwrap(garage.car) instanceof FleetSportsCar

        and: 'the collection holds every subclass instance'
        garage.cars*.name.sort() == ['sedan', 'sports car']
        garage.cars.collect { proxyHandler.unwrap(it).getClass() } as Set == [FleetSportsCar, FleetSedan] as Set
    }

    def clearSession() {
        City.withSession { session -> manager.session.flush() }
    }
}
