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
 * Exercises the validation of the {@code sort}, {@code order} and {@code fetch} query arguments
 * against real Hibernate mappings, through every public entry point that accepts them:
 * {@code list()}, dynamic finders, where queries, criteria queries and {@code listOrderBy*}. Alongside the rejection
 * cases it pins the shapes that must keep working: identity and version properties, inherited
 * properties, embedded and association paths, composite identities, the default sort declared in
 * the mapping, and dotted aliases that are not persistent properties at all.
 */
class SortArgumentValidationSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(SavClub, SavTeam, SavLabelled, SavAnimal, SavDog, SavComposite)
    }

    void setup() {
        def united = new SavClub(name: 'United').save()
        def arsenal = new SavClub(name: 'Arsenal').save()
        new SavTeam(club: united, name: 'United First', address: new SavAddress(city: 'Manchester'))
                .addToTags('north')
                .save()
        new SavTeam(club: arsenal, name: 'Arsenal First', address: new SavAddress(city: 'London'))
                .addToTags('south')
                .save()
        session.flush()
        session.clear()
    }

    void "sorting by the identity and version properties still works"() {
        expect:
        SavClub.list(sort: 'id')*.name == ['United', 'Arsenal']
        SavClub.list(sort: 'id', order: 'desc')*.name == ['Arsenal', 'United']
        SavClub.list(sort: 'version').size() == 2
        SavClub.where { name != null }.list(sort: 'id', order: 'desc')*.name == ['Arsenal', 'United']
        SavClub.findAllByNameLike('%', [sort: 'version']).size() == 2
    }

    void "sorting a subclass by a property inherited from its superclass still works"() {
        given:
        new SavDog(name: 'Rex', breed: 'Collie').save()
        new SavDog(name: 'Ace', breed: 'Beagle').save(flush: true)

        expect:
        SavDog.list(sort: 'name')*.name == ['Ace', 'Rex']
        SavDog.where { breed != null }.list(sort: 'name')*.name == ['Ace', 'Rex']
        SavDog.findAllByBreedLike('%', [sort: 'name', order: 'desc'])*.name == ['Rex', 'Ace']
    }

    void "sorting by a path into an embedded component still works"() {
        expect:
        SavTeam.list(sort: 'address.city')*.name == ['Arsenal First', 'United First']
        SavTeam.where { name != null }.list(sort: 'address.city', order: 'desc')*.name == ['United First', 'Arsenal First']
    }

    void "the default sort declared in the mapping still applies"() {
        given:
        new SavLabelled(label: 'b').save()
        new SavLabelled(label: 'a').save()
        new SavLabelled(label: 'c').save(flush: true)

        expect:
        SavLabelled.list()*.label == ['a', 'b', 'c']
    }

    void "sorting by a path through an association, including the associated identity, still works"() {
        expect:
        SavTeam.list(sort: 'club.name')*.name == ['Arsenal First', 'United First']
        SavTeam.list(sort: 'club.id')*.name == ['United First', 'Arsenal First']
        SavTeam.where { club.name != null }.list(sort: 'club.name')*.name == ['Arsenal First', 'United First']
        SavTeam.where { club.name != null }.list(sort: 'club.id', order: 'desc')*.name == ['Arsenal First', 'United First']
    }

    void "sorting by an alias that is not a persistent property still works for criteria and where queries"() {
        when:
        def byCriteriaAlias = SavTeam.createCriteria().list(sort: 'c.name') {
            createAlias('club', 'c')
        }
        def byWhereAlias = SavTeam.where {
            def c1 = club
            c1.name != null
        }.list(sort: 'c1.name', order: 'desc')

        then:
        byCriteriaAlias*.name == ['Arsenal First', 'United First']
        byWhereAlias*.name == ['United First', 'Arsenal First']
    }

    void "sort keys may name the members of a composite identity"() {
        given:
        new SavComposite(a: 'x', b: '2').save()
        new SavComposite(a: 'x', b: '1').save(flush: true)

        expect:
        SavComposite.list(sort: 'b')*.b == ['1', '2']
        SavComposite.list(sort: [a: 'asc', b: 'desc'])*.b == ['2', '1']
        SavComposite.where { a == 'x' }.list(sort: 'b', order: 'desc')*.b == ['2', '1']
        SavComposite.where { a == 'x' }.list(order: 'desc')*.b == ['2', '1']
    }

    void "list normalizes the sort direction regardless of case and surrounding whitespace"() {
        expect:
        SavClub.list(sort: 'name', order: ' DESC ')*.name == ['United', 'Arsenal']
        SavClub.list(sort: 'name', order: 'Asc')*.name == ['Arsenal', 'United']
        SavClub.list(sort: [name: ' desc'])*.name == ['United', 'Arsenal']
        SavClub.findAllByNameLike('%', [sort: 'name', order: ' DESC '])*.name == ['United', 'Arsenal']
        SavClub.findAllByNameLike('%', [sort: 'name', order: ' asc '])*.name == ['Arsenal', 'United']
        SavClub.where { name != null }.list(sort: 'name', order: ' DESC ')*.name == ['United', 'Arsenal']
    }

    void "criteria queries normalize the sort direction regardless of case and surrounding whitespace"() {
        expect:
        SavClub.createCriteria().list(sort: 'name', order: ' DESC ') { }*.name == ['United', 'Arsenal']
        SavClub.createCriteria().list(sort: 'name', order: 'Asc') { }*.name == ['Arsenal', 'United']
        SavClub.createCriteria().list(max: 10, sort: 'name', order: ' desc') { }*.name == ['United', 'Arsenal']
    }

    void "listOrderBy normalizes the order direction regardless of case and surrounding whitespace"() {
        expect:
        SavClub.listOrderByName(order: ' DESC ')*.name == ['United', 'Arsenal']
        SavClub.listOrderByName(order: 'Asc')*.name == ['Arsenal', 'United']
    }

    void "an order argument is validated even when there is no sort key"() {
        when:
        def listed = SavClub.list(order: 'desc')
        def byCriteria = SavClub.createCriteria().list(order: 'desc') { }

        then:
        listed.size() == 2
        byCriteria.size() == 2

        when:
        SavClub.list(order: 'sideways')

        then:
        def list = thrown(IllegalArgumentException)
        list.message == 'Invalid sort direction'

        when:
        SavClub.createCriteria().list(order: 'sideways') { }

        then:
        def criteria = thrown(IllegalArgumentException)
        criteria.message == 'Invalid sort direction'
    }

    void "each sort map entry takes its direction from its value rather than the order argument"() {
        expect:
        SavClub.list(sort: [name: 'desc'], order: 'asc')*.name == ['United', 'Arsenal']
        SavClub.where { name != null }.list(sort: [name: 'desc'], order: 'asc')*.name == ['United', 'Arsenal']
        SavClub.findAllByNameLike('%', [sort: [name: 'desc'], order: 'asc'])*.name == ['United', 'Arsenal']
    }

    void "list still join-fetches a mapped association and a basic collection"() {
        expect:
        SavTeam.list(fetch: [club: 'join'], sort: 'name')*.name == ['Arsenal First', 'United First']
        SavTeam.list(fetch: [tags: 'eager'], sort: 'name')*.name == ['Arsenal First', 'United First']
    }

    @Unroll
    void "list rejects the sort key #description without echoing it"() {
        when:
        SavTeam.list(sort: sort)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort property'

        where:
        sort                      | description
        'name, e.id'              | 'carrying a second expression'
        'name desc'               | 'carrying a direction'
        'upper(name)'             | 'wrapped in a function call'
        "name'"                   | 'containing a quote'
        ''                        | 'that is empty'
        'notAProperty'            | 'naming an unknown property'
        'club.notAProperty'       | 'naming an unknown property of an association'
        'name.length'             | 'descending into a property that is not an association'
        [name: 'asc', '': 'desc'] | 'that is a map with a blank key'
    }

    void "list rejects a sort direction other than asc or desc without echoing it"() {
        when:
        SavTeam.list(sort: 'name', order: 'desc; drop table sav_team')

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort direction'
    }

    @Unroll
    void "dynamic finders and where queries reject order direction #description without echoing it"() {
        when:
        SavClub.findAllByNameLike('%', [sort: 'name', order: order])

        then:
        def finder = thrown(IllegalArgumentException)
        finder.message == 'Invalid sort direction'

        when:
        SavClub.where { name != null }.list(sort: 'name', order: order)

        then:
        def where = thrown(IllegalArgumentException)
        where.message == 'Invalid sort direction'

        where:
        order        | description
        'sideways'   | 'that is not asc or desc'
        'desc extra' | 'carrying extra tokens'
    }

    @Unroll
    void "criteria queries and listOrderBy reject order direction #description without echoing it"() {
        when:
        SavClub.createCriteria().list(sort: 'name', order: order) { }

        then:
        def criteria = thrown(IllegalArgumentException)
        criteria.message == 'Invalid sort direction'

        when:
        SavClub.createCriteria().list(max: 10, sort: 'name', order: order) { }

        then:
        def paged = thrown(IllegalArgumentException)
        paged.message == 'Invalid sort direction'

        when:
        SavClub.listOrderByName(order: order)

        then:
        def listOrderBy = thrown(IllegalArgumentException)
        listOrderBy.message == 'Invalid sort direction'

        where:
        order        | description
        'sideways'   | 'that is not asc or desc'
        'desc extra' | 'carrying extra tokens'
    }

    @Unroll
    void "a criteria query rejects the sort key #description without echoing it"() {
        when:
        SavTeam.createCriteria().list(sort: sort) { }

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort property'

        when:
        SavTeam.createCriteria().list(max: 10, sort: sort) { }

        then:
        def paged = thrown(IllegalArgumentException)
        paged.message == 'Invalid sort property'

        where:
        sort                | description
        'name, e.id'        | 'carrying a second expression'
        'name desc'         | 'carrying a direction'
        'notAProperty'      | 'naming an unknown property'
        'club.notAProperty' | 'naming an unknown property of an association'
        'name.length'       | 'descending into a property that is not an association'
    }

    @Unroll
    void "list rejects the fetch key #description without echoing it"() {
        when:
        SavTeam.list(fetch: [(key): 'join'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid fetch property'

        where:
        key                           | description
        'club left join fetch e.tags' | 'carrying a second join'
        'club, e.tags'                | 'carrying a second expression'
        'notAnAssociation'            | 'naming an unknown property'
        ''                            | 'that is empty'
    }

    @Unroll
    void "a dynamic finder rejects the sort key #description without echoing it"() {
        when:
        SavTeam.findAllByNameLike('%', [sort: sort])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort property'

        where:
        sort                | description
        'name, id'          | 'carrying a second expression'
        'notAProperty'      | 'naming an unknown property'
        'club.notAProperty' | 'naming an unknown property of an association'
        'name.length'       | 'descending into a property that is not an association'
        'tags.value'        | 'descending into a basic collection'
    }

    @Unroll
    void "a where query rejects the sort key #description without echoing it"() {
        when:
        SavTeam.where { name != null }.list(sort: sort)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort property'

        where:
        sort                | description
        'name; drop table'  | 'carrying a statement separator'
        'notAProperty'      | 'naming an unknown property'
        'club.notAProperty' | 'naming an unknown property of an association'
        'name.length'       | 'descending into a property that is not an association'
        'tags.value'        | 'descending into a basic collection'
    }
}

@Entity
class SavClub {
    String name
}

@Entity
class SavTeam {
    SavClub club
    String name
    SavAddress address

    static hasMany = [tags: String]
    static embedded = ['address']
}

class SavAddress {
    String city
}

@Entity
class SavLabelled {
    String label

    static mapping = {
        sort 'label'
    }
}

@Entity
class SavAnimal {
    String name
}

@Entity
class SavDog extends SavAnimal {
    String breed
}

@Entity
class SavComposite implements Serializable {
    String a
    String b

    static mapping = {
        id composite: ['a', 'b']
    }
}
