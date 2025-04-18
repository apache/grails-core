package myapp

import org.bson.types.ObjectId

class Bar {

    ObjectId id
    String name

    static constraints = {
    }

    static mapWith = "mongo"

    static graphql = true
}
