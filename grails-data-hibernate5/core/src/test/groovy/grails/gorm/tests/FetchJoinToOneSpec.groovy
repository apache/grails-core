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
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue
import spock.lang.Unroll

@Issue('https://github.com/apache/grails-core/issues/14343')
class FetchJoinToOneSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([FjFlight, FjLocation])
    }

    private long statements() {
        manager.sessionFactory.statistics.prepareStatementCount
    }

    void setup() {
        manager.sessionFactory.statistics.statisticsEnabled = true
        (1..3).each { new FjFlight(number: "F$it", destination: new FjLocation(city: "c$it").save(), origin: new FjLocation(city: "o$it").save()).save() }
        manager.session.flush()
        manager.session.clear()
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
        manager.session.clear()
        before = statements()
        FjFlight.createCriteria().list { }*.destination*.city

        then: 'the join-mapped association costs no extra statement per row'
        listStatements <= 2
        statements() - before <= 2
    }

    @Unroll
    void "a call that asks to fetch #association with a join loads it with the flights"() {
        when:
        long before = statements()
        List<String> cities = FjFlight.list(fetch: [(association): 'join'])*."$association"*.city
        long used = statements() - before

        then: 'one statement, whether or not the mapping already says to fetch it with a join'
        cities.size() == 3
        used <= 2

        where:
        association << ['destination', 'origin']
    }

    private void moreFlights() {
        new FjFlight(number: 'F4', origin: new FjLocation(city: 'o4').save()).save()
        new FjFlight(number: 'F5', destination: new FjLocation(city: 'c5').save()).save()
        manager.session.flush()
        manager.session.clear()
    }

    @Unroll
    void "the total of a page that fetches #fetched with #route counts the flights the page can hold"() {
        given: 'F4 has no destination, F5 has no origin'
        moreFlights()
        Map args = fetched ? [fetch: fetched.collectEntries { [(it): 'join'] }] : [:]

        when:
        List all = list(route, args)
        def page = list(route, args + [max: 2])

        then: 'the list leaves out the flights the join leaves out, the paged list with an inner join and the others with an outer one, and the total counts the same flights'
        all.size() == expected
        page.size() == 2
        page.totalCount == expected

        where:
        route            | fetched                      | expected
        'list'           | []                           | 5
        'list'           | ['destination']              | 4
        'list'           | ['origin']                   | 4
        'list'           | ['destination', 'origin']    | 3
        'where query'    | []                           | 5
        'where query'    | ['destination']              | 5
        'where query'    | ['origin']                   | 5
        'where query'    | ['destination', 'origin']    | 5
        'criteria'       | []                           | 5
        'criteria'       | ['destination']              | 5
        'criteria'       | ['origin']                   | 5
        'criteria'       | ['destination', 'origin']    | 5
    }

    private static List list(String route, Map args) {
        switch (route) {
            case 'list': return FjFlight.list(args)
            case 'where query': return FjFlight.where { number != null }.list(args)
            default: return FjFlight.createCriteria().list(args) { }
        }
    }

    void "the total count of a paged list does not fetch the join-mapped association"() {
        when:
        def flights = FjFlight.list(max: 2)

        then: 'the page holds two flights, and the count counts the three, without a fetch in it'
        flights.size() == 2
        flights.totalCount == 3
    }

    void "the total count of a paged criteria does not fetch the join-mapped association"() {
        when:
        def flights = FjFlight.createCriteria().list(max: 2) { }

        then:
        flights.size() == 2
        flights.totalCount == 3
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

    static constraints = {
        destination nullable: true
        origin nullable: true
    }

    static mapping = {
        destination fetch: 'join'
    }
}
