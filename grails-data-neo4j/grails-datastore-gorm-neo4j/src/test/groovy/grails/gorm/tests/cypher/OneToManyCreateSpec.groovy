package grails.gorm.tests.cypher

import grails.gorm.tests.cypher.domain.*
import grails.gorm.tests.cypher.domain.Pet
import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import spock.lang.*

class OneToManyCreateSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(Owner, Pet)

    @Rollback
    void "test save one-to-many"() {
        given:
        // tag::save[]
        Owner owner = new Owner(name:"David")
            .addToPets(name: "Dino")
            .save(flush:true)
            .discard()
        // end::save[]
        expect:
        Owner.count == 1
        Owner.first().pets.size() == 1

        cleanup:
        Owner.deleteAll(owner)
    }

    @Rollback
    void "test save one-to-many with dynamic attributes"() {

        given:
        def owner = new Owner(name: "John")
                            .addToPets(name: "Dino")
        owner.age = 40
        owner
            .save(flush:true)
            .discard()
        def result = Owner.first()

        expect:
        Owner.count == 1
        result.pets.size() == 1
        result.name == "John"
        result.age == 40

        cleanup:
        Owner.deleteAll(owner)
    }
}
