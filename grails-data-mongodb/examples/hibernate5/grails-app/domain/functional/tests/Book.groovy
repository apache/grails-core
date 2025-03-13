package functional.tests

import grails.mongodb.MongoEntity

class Book implements MongoEntity<Book> {

    String title

    static mapWith = "mongo"

    static constraints = {
        title blank:false
    }
}
