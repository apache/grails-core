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
package grails.gorm.specs

import grails.gorm.DetachedCriteria
import grails.gorm.specs.entities.Club
import grails.gorm.specs.entities.Contract
import grails.gorm.specs.entities.Player
import grails.gorm.specs.entities.Team
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.orm.hibernate.query.HibernateQuery
import org.hibernate.Hibernate

import jakarta.persistence.criteria.JoinType

class DetachedCriteriaJoinSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Team, Club])
    }

    def "check if count works as expected"() {
        given:
        new Club(name: "Real Madrid").save()
        new Club(name: "Barcelona").save()
        new Club(name: "Chelsea").save()
        new Club(name: "Manchester United").save()

        expect: "max and offset should always be ignored when calling count()"
        Club.where {}.max(10).offset(0).count() == 4
        new DetachedCriteria<>(Club).max(10).offset(0).count() == 4
        Club.where {}.max(2).offset(0).count() == 4
        new DetachedCriteria<>(Club).max(2).offset(0).count() == 4
        Club.where {}.max(10).offset(10).count() == 4
        new DetachedCriteria<>(Club).max(10).offset(10).count() == 4
    }

    def 'check if inner join is applied correctly'() {
        given:
        def dc = new DetachedCriteria(Team).build {
            join('club', JoinType.INNER)
            createAlias('club', 'c')
        }
        HibernateQuery query = manager.session.createQuery(Team)

        DynamicFinder.applyDetachedCriteria(query, dc)
        def joinType = query.hibernateCriteria.subcriteriaList.first().joinType
        expect:
        joinType == org.hibernate.sql.JoinType.INNER_JOIN
    }

    def 'check if left join is applied correctly'() {
        given:
        def dc = new DetachedCriteria(Team).build {
            join('club', JoinType.LEFT)
            createAlias('club', 'c')
        }
        HibernateQuery query = manager.session.createQuery(Team)

        DynamicFinder.applyDetachedCriteria(query, dc)
        def joinType = query.hibernateCriteria.subcriteriaList.first().joinType
        expect:
        joinType == org.hibernate.sql.JoinType.LEFT_OUTER_JOIN
    }

    def 'check if right join is applied correctly'() {
        given:
        def dc = new DetachedCriteria(Team).build {
            join('club', JoinType.RIGHT)
            createAlias('club', 'c')
        }
        HibernateQuery query = manager.session.createQuery(Team)

        DynamicFinder.applyDetachedCriteria(query, dc)
        def joinType = query.hibernateCriteria.subcriteriaList.first().joinType
        expect:
        joinType == org.hibernate.sql.JoinType.RIGHT_OUTER_JOIN
    }

    def 'check get honours join and eagerly loads association'() {
        given:
        def club = new Club(name: 'Juventus').save(flush: true)
        new Team(name: 'Torino', club: club).save(flush: true)

        when:
        Team team = Team.where { name == 'Torino' }.join('club').get()

        then:
        team != null
        Hibernate.isInitialized(team.club)
    }

    def 'check list with join and projected association property works without explicit alias'() {
        given:
        def club = new Club(name: 'Milan').save(flush: true)
        new Team(name: 'Rossoneri', club: club).save(flush: true)

        when:
        def result = Team.where { name == 'Rossoneri' }.join('club').property('club.name').list()

        then:
        result == ['Milan']
    }

    def 'check get with join and projected association property works without explicit alias'() {
        given:
        def club = new Club(name: 'Inter').save(flush: true)
        new Team(name: 'Nerazzurri', club: club).save(flush: true)

        when:
        def result = Team.where { name == 'Nerazzurri' }.join('club').property('club.name').get()

        then:
        result == 'Inter'
    }

    def 'check list with association subquery plus join and projection works'() {
        given:
        def club = new Club(name: 'Ajax').save(flush: true)
        new Team(name: 'Amsterdammers', club: club).save(flush: true)

        when:
        def result = Team.where {
            club {
                name == 'Ajax'
            }
        }.join('club').property('club.name').list()

        then:
        result == ['Ajax']
    }

    def 'check list can sort by joined association property'() {
        given:
        def clubA = new Club(name: 'A Club').save(flush: true)
        def clubB = new Club(name: 'B Club').save(flush: true)
        new Team(name: 'Team B', club: clubB).save(flush: true)
        new Team(name: 'Team A', club: clubA).save(flush: true)

        when:
        def result = Team.where {}.join('club').sort('club.name', 'asc').property('name').list()

        then:
        result == ['Team A', 'Team B']
    }

    def 'check get honours join with join type and eagerly loads association'() {
        given:
        def club = new Club(name: 'PSG').save(flush: true)
        new Team(name: 'Paris', club: club).save(flush: true)

        when:
        Team team = Team.where { name == 'Paris' }.join('club', JoinType.LEFT).get()

        then:
        team != null
        Hibernate.isInitialized(team.club)
    }

    def 'check list with multiple projections on joined association'() {
        given:
        def club = new Club(name: 'Benfica').save(flush: true)
        new Team(name: 'Lisbon', club: club).save(flush: true)

        when:
        def result = Team.where { name == 'Lisbon' }.join('club').property('club.name').property('name').list()

        then:
        result.size() == 1
        result[0][0] == 'Benfica'
        result[0][1] == 'Lisbon'
    }

    def 'check list with deep nested projection path on players'() {
        given:
        def club = new Club(name: 'Boca Juniors').save(flush: true)
        def team = new Team(name: 'Xeneizes', club: club).save(flush: true)
        def player = new Player(name: 'Roman', team: team)
        player.contract = new Contract(salary: 5_000_000G, player: player)
        player.save(flush: true)

        when:
        def result = Team.where { name == 'Xeneizes' }.join('players').property('players.name').list()

        then:
        result == ['Roman']
    }

    def 'check invalid projection path throws exception'() {
        when:
        new DetachedCriteria(Team).build {
            projections {
                property('nonexistent.field')
            }
        }.list()

        then:
        thrown(Exception)
    }
}
