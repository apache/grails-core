package grails.gorm.tests

import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Issue

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
class OneToManyUpdateSpec extends GormDatastoreSpec {


    @Issue('https://github.com/grails/grails-data-mapping/issues/575')
    void "Test updates to one to many don't create duplicate relationships"() {
        given:" a one to many relationship"
        Club club = new Club(name:"Manchester United")
        club.addToTeams(name:"First Team")
        club.save(flush:true)

        when:
        Tournament tournament = new Tournament(name: "FA Cup")
        tournament.addToTeams(club.teams.first())
        tournament.save(flush:true)
        then:
        !tournament.errors.hasErrors()

        when:"it is first read"
        session.clear()
        tournament = Tournament.get(tournament.id)
        session.clear()
        Team team = Team.first()

        then: "the relationship is correct"
        tournament != null
        tournament.teams.size() == 1
        team != null

        when:"An existing instance is added"

        tournament.addToTeams(name:"Second Team", club: club)
        tournament.addToTeams(team)
        tournament.save(flush:true)

        then:
        !tournament.errors.hasErrors()

        when:
        session.clear()

        tournament = Tournament.get(tournament.id)

        def result = Club.cypherStatic('MATCH (from:Tournament)-[r:TEAMS]->(to:Team) WHERE ID(from) = \$id  RETURN r', [id:tournament.id])
        then: "the relationship is correct and no duplicates are added"
        tournament != null
        result.iterator().size() == 2
        tournament.teams.size() == 2



        when:"A relationship is removed"
        def secondTeam = tournament.teams.find { it.name == "Second Team"}
        tournament.removeFromTeams(secondTeam)
        tournament.save(flush:true)
        session.clear()

        tournament = Tournament.get(tournament.id)
        result = Club.cypherStatic('MATCH (from:Tournament)-[r:TEAMS]->(to:Team) WHERE ID(from) = \$id  RETURN r', [id:tournament.id])
        then: "the relationship is correct and no duplicates are added"
        tournament != null
        tournament.teams.size() == 1
        result.iterator().size() == 1


    }

    @Issue('https://github.com/grails/gorm-neo4j/issues/26')
    void "test that setting an association to null clears the relationship"() {
        given:
        Club c = new Club(name: "Manchester United").save(validate:false)
        Team t = new Team(name: "First Team", club: c).save(flush:true, validate:false)
        session.clear()

        when:"A instance is retrieved"
        t = Team.first()

        then:"it isn't null"
        t != null
        t.club != null

        when:"The association is set to null"
        t.club = null
        assert t.hasChanged('club')
        t.save(flush:true, validate:false)
        session.clear()

        t = Team.first()
        then:"The association was cleared"
        t != null

        when:
        t.club.name

        then:
        thrown DataIntegrityViolationException

    }

    void "test dirty checkable"() {
        given:
        Club c = new Club(name: "Manchester United").save(validate:false)
        Team t = new Team(name: "First Team", club: c).save(flush:true, validate:false)
        session.clear()

        when:"A instance is retrieved"
        t = Team.first()

        then:"it isn't null"
        t != null
        t.club != null

        when:
        t.trackChanges()
        t.club = null

        then:
        t.hasChanged("club")

    }

    @Override
    List getDomainClasses() {
        [Tournament, Club, Team]
    }
}
