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
import spock.lang.Unroll

/**
 * Exercises the validation of the {@code sort} and {@code order} query arguments against real
 * Hibernate 5 mappings, through every public entry point that accepts them: {@code list()},
 * dynamic finders, where queries, criteria queries and {@code listOrderBy*}. The Hibernate 5
 * {@code list()} and paged criteria paths build their ordering through the legacy and JPA
 * criteria APIs rather than through {@code DynamicFinder}, so the same rejection cases are pinned
 * here alongside the shapes that must keep working: identity properties, association paths, and
 * dotted criteria aliases that are not persistent properties at all.
 */
class SortArgumentValidationSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(Sav5Club, Sav5Team)
    }

    void setup() {
        def united = new Sav5Club(name: 'United').save()
        def arsenal = new Sav5Club(name: 'Arsenal').save()
        new Sav5Team(club: united, name: 'United First').save()
        new Sav5Team(club: arsenal, name: 'Arsenal First').save()
        session.flush()
        session.clear()
    }

    void "sorting by the identity property and by a path through an association still works"() {
        expect:
        Sav5Club.list(sort: 'id')*.name == ['United', 'Arsenal']
        Sav5Club.list(sort: 'id', order: 'desc')*.name == ['Arsenal', 'United']
        Sav5Team.list(sort: 'club.name')*.name == ['Arsenal First', 'United First']
        Sav5Team.list(sort: 'club.name', order: 'desc')*.name == ['United First', 'Arsenal First']
        Sav5Team.createCriteria().list(max: 10, sort: 'club.name') { }*.name == ['Arsenal First', 'United First']
    }

    void "sorting by a criteria alias that is not a persistent property still works"() {
        expect:
        Sav5Team.createCriteria().list(max: 10, sort: 'c.name', order: 'desc') {
            createAlias('club', 'c')
        }*.name == ['United First', 'Arsenal First']
    }

    void "every entry point normalizes the sort direction regardless of case and surrounding whitespace"() {
        expect:
        Sav5Club.list(sort: 'name', order: ' DESC ')*.name == ['United', 'Arsenal']
        Sav5Club.list(sort: [name: ' desc'])*.name == ['United', 'Arsenal']
        Sav5Club.findAllByNameLike('%', [sort: 'name', order: ' DESC '])*.name == ['United', 'Arsenal']
        Sav5Club.where { name != null }.list(sort: 'name', order: ' DESC ')*.name == ['United', 'Arsenal']
        Sav5Club.createCriteria().list(sort: 'name', order: ' DESC ') { }*.name == ['United', 'Arsenal']
        Sav5Club.createCriteria().list(max: 10, sort: 'name', order: 'Desc') { }*.name == ['United', 'Arsenal']
        Sav5Club.listOrderByName(order: ' DESC ')*.name == ['United', 'Arsenal']
    }

    void "an order argument is validated even when there is no sort key"() {
        when:
        def listed = Sav5Club.list(order: 'desc')
        def byCriteria = Sav5Club.createCriteria().list(max: 10, order: 'desc') { }

        then:
        listed.size() == 2
        byCriteria.size() == 2

        when:
        Sav5Club.list(order: 'sideways')

        then:
        def list = thrown(IllegalArgumentException)
        list.message == 'Invalid sort direction'

        when:
        Sav5Club.createCriteria().list(max: 10, order: 'sideways') { }

        then:
        def criteria = thrown(IllegalArgumentException)
        criteria.message == 'Invalid sort direction'
    }

    @Unroll
    void "every entry point rejects order direction #description without echoing it"() {
        when:
        Sav5Club.list(sort: 'name', order: order)

        then:
        def list = thrown(IllegalArgumentException)
        list.message == 'Invalid sort direction'

        when:
        Sav5Club.list(sort: [name: order])

        then:
        def sortMap = thrown(IllegalArgumentException)
        sortMap.message == 'Invalid sort direction'

        when:
        Sav5Club.findAllByNameLike('%', [sort: 'name', order: order])

        then:
        def finder = thrown(IllegalArgumentException)
        finder.message == 'Invalid sort direction'

        when:
        Sav5Club.where { name != null }.list(sort: 'name', order: order)

        then:
        def where = thrown(IllegalArgumentException)
        where.message == 'Invalid sort direction'

        when:
        Sav5Club.createCriteria().list(max: 10, sort: 'name', order: order) { }

        then:
        def criteria = thrown(IllegalArgumentException)
        criteria.message == 'Invalid sort direction'

        when:
        Sav5Club.listOrderByName(order: order)

        then:
        def listOrderBy = thrown(IllegalArgumentException)
        listOrderBy.message == 'Invalid sort direction'

        where:
        order        | description
        'sideways'   | 'that is not asc or desc'
        'desc extra' | 'carrying extra tokens'
    }

    @Unroll
    void "list and criteria queries reject the sort key #description without echoing it"() {
        when:
        Sav5Team.list(sort: sort)

        then:
        def list = thrown(IllegalArgumentException)
        list.message == 'Invalid sort property'

        when:
        Sav5Team.createCriteria().list(max: 10, sort: sort) { }

        then:
        def criteria = thrown(IllegalArgumentException)
        criteria.message == 'Invalid sort property'

        where:
        sort                | description
        'name, e.id'        | 'carrying a second expression'
        'name desc'         | 'carrying a direction'
        'upper(name)'       | 'wrapped in a function call'
        ''                  | 'that is empty'
        'notAProperty'      | 'naming an unknown property'
        'club.notAProperty' | 'naming an unknown property of an association'
        'name.length'       | 'descending into a property that is not an association'
    }

    void "list rejects a sort map with a blank key without echoing it"() {
        when:
        Sav5Team.list(sort: [name: 'asc', '': 'desc'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort property'
    }

    void "a criteria query rejects a sort key that carries a second expression behind an alias"() {
        when:
        Sav5Team.createCriteria().list(max: 10, sort: 'c.name, e.id') {
            createAlias('club', 'c')
        }

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort property'
    }
}

@Entity
class Sav5Club {
    String name
}

@Entity
class Sav5Team {
    Sav5Club club
    String name
}
