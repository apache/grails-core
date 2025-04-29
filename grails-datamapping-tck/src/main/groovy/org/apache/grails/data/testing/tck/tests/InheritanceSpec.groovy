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

import org.apache.grails.data.testing.tck.domains.City
import org.apache.grails.data.testing.tck.domains.Country
import org.apache.grails.data.testing.tck.domains.Location
import org.apache.grails.data.testing.tck.domains.Practice
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author graemerocher
 */
class InheritanceSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses += [Practice]
    }

    void "Test inheritance with dynamic finder"() {

        given:
        def city = new City([code: "UK", name: "London", longitude: 49.1, latitude: 53.1])
        def country = new Country([code: "UK", name: "United Kingdom", population: 10000000])

        city.save()
        country.save(flush:true)
        manager.session.clear()

        when:
        def locations = Location.findAllByCode("UK")
        def cities = City.findAllByCode("UK")
        def countries = Country.findAllByCode("UK")

        then:
        2 == locations.size()
        1 == cities.size()
        1 == countries.size()
        "London" == cities[0].name
        "United Kingdom" == countries[0].name
    }

    void "Test querying with inheritance"() {

        given:
        def city = new City([code: "LON", name: "London", longitude: 49.1, latitude: 53.1])
        def location = new Location([code: "XX", name: "The World"])
        def country = new Country([code: "UK", name: "United Kingdom", population: 10000000])

        country.save()
        city.save()
        location.save()

        manager.session.flush()

        when:
        city = City.get(city.id)
        def london = Location.get(city.id)
        country = Location.findByName("United Kingdom")
        def london2 = Location.findByName("London")

        then:
        1 == City.count()
        1 == Country.count()
        3 == Location.count()

        city != null
        city instanceof City
        london instanceof City
        london2 instanceof City
        "London" == london2.name
        49.1 == london2.longitude
        "LON" == london2.code

        country instanceof Country
        "UK" == country.code
        10000000 == country.population
    }

    void "Test hasMany with inheritance should return appropriate class"() {
        given: "a practice with two locations"
        Practice practice = new Practice(name: "Test practice")
        practice.addToLocations(new City(name: "Austin", latitude: 30.2672, longitude: 97.7431))
        practice.addToLocations(new Country(name: "United States"))
        practice.save()
        manager.session.flush()

        expect:
        Location.findByName("Austin").class == City
    }

    def clearSession() {
        City.withSession { session -> manager.session.flush() }
    }
}
