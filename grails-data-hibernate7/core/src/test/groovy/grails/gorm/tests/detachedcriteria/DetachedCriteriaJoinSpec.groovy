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
package grails.gorm.specs.detachedcriteria

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.tests.entities.Club
import grails.gorm.tests.entities.Team
import jakarta.persistence.criteria.JoinType
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.orm.hibernate.query.HibernateQuery
import org.hibernate.Hibernate

class DetachedCriteriaJoinSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([Team, Club])
    }

    def "check if count works as expected"() {
        given:
        def club1 = new Club(name: "Real Madrid").save()
        def club2 = new Club(name: "Barcelona").save()
        def club3 = new Club(name: "Chelsea").save()
        def club4 = new Club(name: "Manchester United").save(flush: true)


        expect:"max and offset should always be ignored when calling count()"
        Club.where {}.max(10).offset(0).count() == 4
        new DetachedCriteria<>(Club).max(10).offset(0).count() == 4
        Club.where {}.max(2).offset(0).count() == 4
        new DetachedCriteria<>(Club).max(2).offset(0).count() == 4
//TODO THESE SHOULD NOT PASS!
//        Club.where {}.max(10).offset(10).count() == 4
//        new DetachedCriteria<>(Club).max(10).offset(10).count() == 4
    }

    def 'check if inner join is applied correctly'(){
        given: 
            def dc = new DetachedCriteria(Team).build{
                join('club', JoinType.INNER)
                createAlias('club','c')
            }
        HibernateQuery query = manager.session.createQuery(Team)
            
            DynamicFinder.applyDetachedCriteria(query,dc)
            def joinType = query.hibernateCriteria.joinTypes['club']
        expect: 
            joinType == JoinType.INNER
    }

    def 'check if left join is applied correctly'(){
        given:
            def dc = new DetachedCriteria(Team).build{
                join('club', JoinType.LEFT)
                createAlias('club','c')
            }
        HibernateQuery query = manager.session.createQuery(Team)

            DynamicFinder.applyDetachedCriteria(query,dc)
            def joinType = query.hibernateCriteria.joinTypes["club"]
        expect:
            joinType == JoinType.LEFT
    }

    def 'check if right join is applied correctly'(){
        given:
            def dc = new DetachedCriteria(Team).build{
                join('club', JoinType.RIGHT)
                createAlias('club','c')
            }
        HibernateQuery query = manager.session.createQuery(Team)

            DynamicFinder.applyDetachedCriteria(query,dc)
            def joinType = query.hibernateCriteria.joinTypes["club"]
        expect:
            joinType == JoinType.RIGHT
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
}
