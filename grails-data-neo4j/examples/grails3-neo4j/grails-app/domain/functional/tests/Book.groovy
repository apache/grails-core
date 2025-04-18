package functional.tests

import grails.neo4j.Neo4jEntity

class Book implements Neo4jEntity<Book> {

    String title

    static constraints = {
        title blank:false
    }
}
