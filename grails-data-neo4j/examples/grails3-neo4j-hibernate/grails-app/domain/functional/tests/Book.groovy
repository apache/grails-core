package functional.tests

class Book {

    String title

    static mapWith = "neo4j"

    static constraints = {
        title blank:false
    }
}
