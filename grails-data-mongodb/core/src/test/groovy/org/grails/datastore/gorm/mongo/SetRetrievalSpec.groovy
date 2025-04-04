package org.grails.datastore.gorm.mongo

import com.mongodb.client.MongoDatabase
import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.bson.Document
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * Created by graemerocher on 01/04/16.
 */
class SetRetrievalSpec extends GormDatastoreSpec {

    @Issue('https://github.com/grails/grails-data-mapping/issues/675')
    void "Test retrieve an existing set"() {
        when:"a set is retrieved"
        MongoDatabase db = Team.DB
        db.getCollection('team').insertOne(new Document(name:"Manchester United", nicknames:['Red Devils'] as Set))

        def teams = Team.list()
        then:"the result is correct"
        teams.size() == 1
        teams[0].name == 'Manchester United'
        teams[0].nicknames == ['Red Devils'] as Set
    }

    void "Test persist and retrieve sets"() {
        when:"An object with sets is persisted"
        new Team(name: "Real Madrid", nicknames: ['Los Blancos'] as Set, sports: [Sport.FOOTBALL, Sport.BASKETBALL] as Set ).save(flush:true)
        session.clear()
        List<Team> teams = Team.list()

        then:"It is retrievable"
        teams[0].name == "Real Madrid"
        teams[0].sports == [Sport.FOOTBALL, Sport.BASKETBALL] as Set

    }
    @Override
    List getDomainClasses() {
        [Team, Player]
    }
}

@Entity
class Team implements MongoEntity<Team> {
    ObjectId id
    String name
    Set<String> nicknames = []
    Set<Sport> sports = []
    static hasMany = [players:Player]
}

@Entity
class Player {
    String name
}

enum Sport {
    FOOTBALL, BASKETBALL
}
