package example

import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 20/03/2017.
 */
class PersonServiceSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(getClass().getPackage())
    @Shared PersonService personService = datastore.getService(PersonService)

    @Rollback
    void "test person service"() {
        when:"A new person is saved"
        new Person(name: "Fred").save(flush:true)

        then:"the person can be found"
        personService.findPerson("Fred") != null
    }
}
