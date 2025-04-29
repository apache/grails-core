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

class GeospacialQuerySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Hotel])
    }

    void "Test geolocation with BigDecimal values"() {
        given: "Some entities stored with BigDecimal locations"
        new Hotel(name: "Hilton", location: [50.34d, 50.12d]).save()
        new Hotel(name: "Raddison", location: [150.45d, 130.67d]).save(flush: true)
        manager.session.clear()

        when: "We query by location"
        def h = Hotel.findByLocation([50.34d, 50.12d])

        then: "The location is found"
        h != null
    }

    void "Test that we can query within a circle"() {
        given:
        new Hotel(name: "Hilton", location: [50, 50]).save()
        new Hotel(name: "Raddison", location: [150, 130]).save(flush: true)
        manager.session.clear()

        when:
        def h = Hotel.findByLocation([50, 50])

        then:
        h != null

        when:
        h = Hotel.findByLocationWithinCircle([[40, 30], 40])

        then:
        h != null
        h.name == "Hilton"
        when:
        h = Hotel.findByLocationWithinCircle([[10, 10], 30])

        then:
        h == null
    }

    void "Test that we can query within a box"() {
        given:
        new Hotel(name: "Hilton", location: [50, 50]).save()
        new Hotel(name: "Raddison", location: [150, 130]).save(flush: true)
        manager.session.clear()

        when:
        def h = Hotel.findByLocation([50, 50])

        then:
        h != null

        when:
        h = Hotel.findByLocationWithinBox([[40, 30], [60, 70]])

        then:
        h != null
        h.name == "Hilton"
        when:
        h = Hotel.findByLocationWithinBox([[20, 10], [40, 30]])

        then:
        h == null
    }

    void "Test that we can query within a polygon with criteria"() {
        given:
        new Hotel(name: "Hilton", location: [50, 50]).save()
        new Hotel(name: "Raddison", location: [150, 130]).save(flush: true)
        manager.session.clear()

        when:
        def h = Hotel.findByLocation([50, 50])

        then:
        h != null

        when:
        h = Hotel.createCriteria().get {
            withinPolygon("location", [[40, 30], [40, 70], [60, 70], [60, 30]])
        }

        then:
        h != null
        h.name == "Hilton"

        when:
        h = Hotel.createCriteria().get {
            withinPolygon("location", [[20, 10], [20, 30], [40, 30], [40, 10]])
        }

        then:
        h == null
    }

    void "Test that we can query for nearby location"() {
        given:
        new Hotel(name: "Hilton", location: [50, 50]).save()
        new Hotel(name: "Raddison", location: [150, 130]).save(flush: true)
        manager.session.clear()

        when:
        def h = Hotel.findByLocation([50, 50])

        then:
        h != null

        when:
        h = Hotel.findByLocationNear([50, 60])

        then:
        h != null
        h.name == "Hilton"
        when:
        h = Hotel.findByLocationNear([170, 160])

        then:
        h != null
        h.name == "Raddison"
    }

    void "Test that we can query for nearby location with criteria"() {
        given:
        new Hotel(name: "Hilton", location: [50, 50]).save()
        new Hotel(name: "Raddison", location: [150, 130]).save(flush: true)
        manager.session.clear()

        when:
        def h = Hotel.findByLocation([50, 50])

        then:
        h != null

        when:
        h = Hotel.createCriteria().get { near("location", [50, 60]) }

        then:
        h != null
        h.name == "Hilton"
        when:
        h = Hotel.createCriteria().get { near("location", [170, 160]) }

        then:
        h != null
        h.name == "Raddison"
    }
}

@Entity
class Hotel {
    Long id
    String name
    List location

    static mapping = {
        location geoIndex: true
    }
}
