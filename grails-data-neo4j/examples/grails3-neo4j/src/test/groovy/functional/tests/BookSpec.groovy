package functional.tests

import grails.test.neo4j.Neo4jSpec

/**
 * Created by graemerocher on 18/11/16.
 */
class BookSpec extends Neo4jSpec {

    void "test save and retrieve entity"() {
        when:"an entity is saved"
        new Book(title: "The Stand").save(flush:true)

        then:"A book was saved"
        Book.count() == 1
    }
}
