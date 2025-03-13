package functional.tests

import grails.mongodb.MongoEntity
import org.bson.types.ObjectId

class Team implements MongoEntity<Team> {

    ObjectId id

    String name
}
