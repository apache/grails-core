package functional.tests

import grails.gorm.transactions.Rollback
import grails.neo4j.Path
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 15/03/2017.
 */
class PersonSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(getClass().getPackage())

    @Rollback
    void 'test update person'() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
                .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        PersonService service = datastore.getService(PersonService)
        service.updatePerson("Fred", 40)

        fred = Person.findByName("Fred")

        then:
        fred.age == 40
    }
    @Rollback
    void "test shortest path service implementer"() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
                .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        // tag::service[]
        PersonService service = datastore.getService(PersonService)
        Path<Person, Person> path = service.findPath(fred, joe)
        // end::service[]

        then:
        path.nodes().size() == 3
        path.nodes().contains(joe)
        path.nodes().contains(fred)
        path.nodes().contains(barney)
        path.start().name == "Fred"
        path.end().name == "Joe"
        path.length() == 2
        path.collect({ Path.Segment p -> p.start().name })  == ["Fred", "Barney"]
        path.collect({ Path.Segment p -> p.end().name })  == ["Barney", "Joe"]
        path.first().relationship().from.name == "Fred"
        path.first().relationship().to.name == "Barney"
        path.first().relationship().type == 'FRIENDS'
    }

    @Rollback
    void "test shortest path service implementer via cypher"() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
                .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        // tag::service[]
        PersonService service = datastore.getService(PersonService)
        Path<Person, Person> path = service.findPath("Fred", "Joe")
        // end::service[]

        then:
        path.nodes().size() == 3
        path.nodes().contains(joe)
        path.nodes().contains(fred)
        path.nodes().contains(barney)
        path.start().name == "Fred"
        path.end().name == "Joe"
        path.length() == 2
        path.collect({ Path.Segment p -> p.start().name })  == ["Fred", "Barney"]
        path.collect({ Path.Segment p -> p.end().name })  == ["Barney", "Joe"]
        path.first().relationship().from.name == "Fred"
        path.first().relationship().to.name == "Barney"
        path.first().relationship().type == 'FRIENDS'
    }
}
