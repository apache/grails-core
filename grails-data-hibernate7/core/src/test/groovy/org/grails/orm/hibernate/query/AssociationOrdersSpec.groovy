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
package org.grails.orm.hibernate.query

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query
import spock.lang.Specification

class AssociationOrdersSpec extends Specification {

    private DetachedAssociationCriteria block(String name, List<String> properties) {
        def entity = Mock(PersistentEntity)
        properties.each { String property ->
            entity.getPropertyByName(property) >> Mock(PersistentProperty)
        }
        def association = Mock(Association)
        association.getName() >> name
        association.getAssociatedEntity() >> entity
        new DetachedAssociationCriteria(Object, association)
    }

    def 'an order by a property of the association moves out as an order by the path of the property'() {
        given:
        def club = block('club', ['name'])
        club.order(new Query.Order('name', Query.Order.Direction.DESC).ignoreCase())
        List<Query.Order> moved = []

        when:
        AssociationOrders.moveOutOf(club, moved.&add)

        then:
        club.orders.empty
        moved.size() == 1
        moved[0].class == AssociationOrder
        moved[0].property == 'club.name'
        moved[0].direction == Query.Order.Direction.DESC
        moved[0].ignoreCase

        and: 'it keeps its block, of which the query has the joins, and the property as the block names it'
        moved[0].association.is(club)
        moved[0].associationProperty == 'name'
    }

    def 'an order by a path through an association of the block keeps the block and the path'() {
        given:
        def owner = block('owner', ['scores'])
        owner.order('scores.score')
        List<Query.Order> moved = []

        when:
        AssociationOrders.moveOutOf(owner, moved.&add)

        then:
        moved[0].property == 'owner.scores.score'
        moved[0].association.is(owner)
        moved[0].associationProperty == 'scores.score'
    }

    def 'an order of a block inside another moves out with its own block'() {
        given:
        def club = block('club', ['name'])
        club.order('name')
        def team = block('team', ['club', 'name'])
        List<Query.Order> moved = []

        when: 'the orders of the inner block move to the block around it, and from there to the criteria'
        AssociationOrders.moveOutOf(club, { Query.Order order -> team.order(order) })
        AssociationOrders.moveOutOf(team, moved.&add)

        then: 'only the path from the root changes on the way out'
        club.orders.empty
        team.orders.empty
        moved*.property == ['team.club.name']
        moved[0].association.is(club)
        moved[0].associationProperty == 'name'
    }

    def 'an order by a single name that is no property of the association stays in its block'() {
        given:
        def club = block('club', ['name'])
        club.order('unknown')
        List<Query.Order> moved = []

        when:
        AssociationOrders.moveOutOf(club, moved.&add)

        then:
        moved.empty
        club.orders*.property == ['unknown']
        AssociationOrders.inBlocks([club]).empty
    }

    def 'an order by an alias that the block declares keeps the block and the path of the alias'() {
        given:
        def team = block('team', ['name'])
        team.add(declaration)
        team.order('c.name')
        List<Query.Order> moved = []

        when:
        AssociationOrders.moveOutOf(team, moved.&add)

        then:
        moved[0] instanceof AssociationOrder
        moved[0].property == 'team.club.name'
        moved[0].association.is(team)
        moved[0].associationProperty == 'c.name'

        where: 'the criteria builder declares an alias with a HibernateAlias, other criteria with aliased criteria'
        declaration << [
                new HibernateAlias('club', 'c'),
                new DetachedAssociationCriteria(Object, Stub(Association), 'club', 'c')
        ]
    }

    def 'an alias wins over a property of the association that has its name'() {
        given:
        def team = block('team', ['club'])
        team.add(new HibernateAlias('players', 'club'))
        team.order('club.name')
        List<Query.Order> moved = []

        when:
        AssociationOrders.moveOutOf(team, moved.&add)

        then:
        moved[0].property == 'team.players.name'
        moved[0].associationProperty == 'club.name'
    }

    def 'an order by an alias that the block does not declare is left to the block to resolve'() {
        given:
        def contract = block('contract', ['salary'])
        contract.order('t.name', 'desc')
        List<Query.Order> moved = []

        when:
        AssociationOrders.moveOutOf(contract, moved.&add)

        then: 'the alias can be one of the criteria around the block, which the block sees'
        contract.orders.empty
        moved[0] instanceof AssociationOrder
        moved[0].association.is(contract)
        moved[0].associationProperty == 't.name'
        moved[0].property == 'contract.t.name'
        moved[0].direction == Query.Order.Direction.DESC
    }

    def 'the orders of nested blocks are read through every block and junction around them'() {
        given:
        def club = block('club', ['name'])
        club.add(new HibernateAlias('sponsors', 's'))
        club.order('name')
        club.order('s.name')
        def team = block('team', ['club', 'name'])
        def junction = new Query.Disjunction()
        junction.add(club)
        team.add(junction)
        team.order('name')

        when:
        def orders = AssociationOrders.inBlocks([new Query.Conjunction().add(team)])

        then: 'the orders of a block come before those of the blocks inside it'
        orders*.property == ['team.name', 'team.club.name', 'team.club.sponsors.name']

        and: 'each keeps the block that declares it'
        orders*.association == [team, club, club]
        orders*.associationProperty == ['name', 'name', 's.name']
    }

    def 'the orders in the blocks are read without taking them out of their blocks'() {
        given:
        def club = block('club', ['name'])
        club.order('name')
        def team = block('team', ['club', 'name'])
        team.add(club)
        team.order('name', 'desc')
        def criteria = [new Query.Equals('name', 'x'), new Query.Negation().add(team)]

        when:
        def first = AssociationOrders.inBlocks(criteria)
        def second = AssociationOrders.inBlocks(criteria)

        then:
        first*.property == ['team.name', 'team.club.name']
        first*.direction == [Query.Order.Direction.DESC, Query.Order.Direction.ASC]
        first.every { it instanceof AssociationOrder }
        first*.association == [team, club]

        and: 'so that each query built from the criteria has the same orders'
        second*.property == first*.property
        club.orders*.property == ['name']
        team.orders*.property == ['name']
    }
}
