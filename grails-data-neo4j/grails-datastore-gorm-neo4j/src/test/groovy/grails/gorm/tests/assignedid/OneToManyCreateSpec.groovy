package grails.gorm.tests.assignedid

import grails.gorm.tests.assignedid.domain.Owner
import grails.gorm.tests.assignedid.domain.Pet
import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 14/03/2017.
 */
class OneToManyCreateSpec extends Specification {
    // tag::setup[]
    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(getClass().getPackage())
    // end::setup[]

    @Rollback
    void "test save one-to-many"() {
        given:
        List<Owner> deleteOwners = []
        // tag::save[]
        deleteOwners << new Owner(name:"Fred")
                .addToPets(name: "Dino")
                .addToPets(name: "Joe")
                .save()
        deleteOwners << new Owner(name:"Barney")
                .addToPets(name: "Hoppy")
                .save(flush:true)
        // end::save[]
        Owner.withSession { it.clear() }

        expect:
        Owner.count == 2
        Owner.findByName("Fred").pets.size() == 2
        Owner.findByName("Barney").pets.size() == 1

        cleanup:
        Owner.deleteAll(deleteOwners)
    }
}
