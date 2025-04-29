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
package grails.gorm.tests.inheritance

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 29/05/2017.
 */
@Issue('https://github.com/grails/grails-data-mapping/issues/937')
class TablePerConcreteClassAndDateCreatedSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Vehicle, Spaceship])
    }

    void "should set the dateCreated automatically"() {
        given:
        Spaceship ship = new Spaceship(name: "Heart of Gold")
        ship.save(flush: true)

        expect:
        ship.dateCreated != null
    }

    void "should set the dateCreated automatically on update"() {
        given:
        Spaceship ship = new Spaceship(name: "Heart of Gold")
        ship.save()

        when:
        ship.name = "Heart of Gold II"
        ship.save(flush: true)

        then:
        // DataIntegrityViolationException is thrown:
        // NULL not allowed for column "DATE_CREATED"
        ship.dateCreated != null
    }
}

@Entity
abstract class Vehicle {
    String name
    Date dateCreated

    static mapping = {
        tablePerConcreteClass true
        dynamicUpdate true
        id generator: 'increment'
    }
}

@Entity
class Spaceship extends Vehicle {
    static mapping = {
        dynamicUpdate true
    }
}