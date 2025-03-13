package functional.tests

import grails.mongodb.MongoEntity

class Book implements MongoEntity<Book> {

    String title

    static constraints = {
        title blank:false
    }
}
