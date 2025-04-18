package grails.gorm.tests

import grails.gorm.annotation.Entity
import javassist.util.proxy.ProxyObject
import org.grails.datastore.gorm.neo4j.collection.Neo4jList
import org.grails.datastore.gorm.neo4j.collection.Neo4jPersistentList
import org.grails.datastore.gorm.neo4j.collection.Neo4jPersistentSet
import org.grails.datastore.gorm.neo4j.collection.Neo4jSet
import spock.lang.Specification

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
class EagerFetchingSpec extends GormDatastoreSpec {

    void "Test eager fetch with query"() {
        given:
        def league = new League(name:"Bundesliga").save()
        def club = new Club(name: 'FC Bayern Muenchen', league: league)
        club.addToTeams(new Team(name: 'FCB Team 1'))
        club.addToTeams(new Team(name: 'FCB Team 2'))
        club.save(flush:true,validate:false)
        session.clear()

        when:"an object query is executed"
        club = Club.get(club.id)

        then:"the default is that a collection is lazily fetched with no proxies in it"
        club.teams instanceof Neo4jPersistentList
        !club.teams.isInitialized()
        club.teams[0] instanceof Team
        !(club.teams[0] instanceof ProxyObject)
        !(club.teams[0].club instanceof ProxyObject)
        club.teams[0].name == 'FCB Team 1'
        club.teams[1].name == 'FCB Team 2'
        League.count() == 1

        when:"A join query is executed"
        session.clear()
        club = Club.findByName('FC Bayern Muenchen', [fetch:[teams:'eager']])

        then:"A join query was issued and so the collection is initialized"
        club.teams
        club.teams instanceof Neo4jList
        club.teams[0] instanceof Team
        club.teams[0].name == 'FCB Team 1'
        club.teams[1].name == 'FCB Team 2'

        when:"A lazy to one association is queried"
        session.clear()
        def team = Team.findByName('FCB Team 1')

        then:"The association is a proxy"
        !session.mappingContext.proxyFactory.isInitialized(team, 'club')

        when:"A an eager fetch is used"
        session.clear()
         team = Team.findByName('FCB Team 1', [fetch:[club:'eager']])

        then:"The association is a not proxy"
        !session.mappingContext.proxyFactory.isProxy(team.club)
    }


    void "Test eager fetch with domain class mapping"() {
        given:
        def club = new Club(name: 'FC Bayern Muenchen')
        club.addToTeams(new Team(name: 'FCB Team 1'))
        club.addToTeams(new Team(name: 'FCB Team 2'))
        club.save(flush:true,validate:false)
        League league = new League(name:"Bundesliga")
        league.addToClubs(club)
        league.teams.addAll(club.teams)
        league.save(flush:true,validate:false)
        session.clear()

        when:"an object query is executed"
        league = League.findById(league.id)

        then:"The associations were eagerly fetched"
        league.clubs instanceof Neo4jPersistentSet
        league.clubs.size() == 1
        league.teams instanceof Neo4jSet
        league.teams.size() == 2
    }
    @Override
    List getDomainClasses() {
        [League, Club, Team]
    }
}

@Entity
class League {
    Long id
    Long version
    String name
    Set<Club> clubs
    Set<Team> teams = []

    static hasMany = [clubs:Club, teams:Team]

    static mapping = {
        clubs fetch:"eager", lazy:true
        teams fetch:"eager", lazy:false
    }
}
