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
package grails.gorm.tests

import grails.gorm.annotation.Entity
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14343')
class FetchJoinToOneSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(FjFlight, FjLocation)
    }

    private long statements() {
        manager.sessionFactory.statistics.prepareStatementCount
    }

    void setup() {
        manager.sessionFactory.statistics.statisticsEnabled = true
        (1..3).each { new FjFlight(number: "F$it", destination: new FjLocation(city: "c$it").save(), origin: new FjLocation(city: "o$it").save()).save() }
        session.flush()
        session.clear()
    }

    void "findAll loads a to-one association mapped with fetch join in one statement"() {
        given:
        long before = statements()

        when:
        List<FjFlight> flights = FjFlight.findAll()
        List<String> cities = flights*.destination*.city

        then:
        cities.sort() == ['c1', 'c2', 'c3']
        statements() - before <= 2
    }

    void "list and the criteria builder honour the mapping too"() {
        when:
        long before = statements()
        FjFlight.list()*.destination*.city
        long listStatements = statements() - before
        session.clear()
        before = statements()
        FjFlight.createCriteria().list { }*.destination*.city

        then: 'the join-mapped association costs no extra statement per row'
        listStatements <= 2
        statements() - before <= 2
    }

    void "an association without fetch join stays lazy"() {
        when:
        long before = statements()
        FjFlight.findAll()*.origin*.city
        long used = statements() - before

        then: 'one statement for the flights, one per origin location'
        used >= 4
    }
}

@Entity
class FjLocation {
    String city
}

@Entity
class FjFlight {
    String number
    FjLocation destination
    FjLocation origin

    static mapping = {
        destination fetch: 'join'
    }
}
