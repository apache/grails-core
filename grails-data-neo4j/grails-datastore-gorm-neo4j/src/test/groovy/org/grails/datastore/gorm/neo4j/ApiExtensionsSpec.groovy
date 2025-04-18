package org.grails.datastore.gorm.neo4j

import grails.gorm.tests.Club
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.League
import grails.gorm.tests.Person
import grails.gorm.tests.Pet
import grails.gorm.tests.PetType
import grails.gorm.tests.Team

/**
 * check the traverser extension
 */
class ApiExtensionsSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Club, Team, League]
    }

    def "test cypher queries"() {
        setup:
        new Club(name:'person1').save()
        new Club(name:'person2').save()
        session.flush()
        session.clear()

        when:
        def result = Club.cypherStatic("MATCH (p:Club) RETURN p")

        then:
        result.iterator().size()==2

        when: "test with parameters"
        result = Club.cypherStatic("MATCH (p:Club) WHERE p.name={1} RETURN p", [ 'person1'])

        then:
        result.iterator().size()==1
    }

    def "test instance based cypher query"() {
        setup:

        def team = new Team(name:"Manchester United FC")
        def club = new Club(name: "Manchester United")
        def league = new League(name:"EPL")
        league.addToClubs(club)
        club.addToTeams(team)
        club.save(flush: true,validate:false)
        session.clear()

        when:
        def result = team.cypher("MATCH (p:Team)<-[:TEAMS]->(m) WHERE ID(p) = \$this return m")

        then:
        result.iterator().size() == 1
    }

}
