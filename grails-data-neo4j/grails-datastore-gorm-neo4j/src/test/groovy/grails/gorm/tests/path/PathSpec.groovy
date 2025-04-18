package grails.gorm.tests.path

import grails.gorm.tests.path.domain.Person
import grails.gorm.transactions.Rollback
import grails.neo4j.Path
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 14/03/2017.
 */
class PathSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(getClass().getPackage())

    @Rollback
    void "test simple shortest path with findShortestPath"() {

        given:
        // tag::model[]
        Person joe = new Person(name: "Joe")
        Person barney = new Person(name: "Barney")
                              .addToFriends(joe)
        Person fred = new Person(name: "Fred")
                              .addToFriends(barney)
        // end::model[]

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        // tag::shortestPath[]
        Path<Person, Person> path = Person.findShortestPath(fred, joe, 15)
        for(Path.Segment<Person, Person> segment in path) {
            println segment.start().name
            println segment.end().name
        }
        // end::shortestPath[]

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
    void "test simple shortest path with findShortestPath with proxies"() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
                .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        // tag::shortestPathProxy[]
        Path<Person, Person> path = Person.findShortestPath(Person.proxy("Fred"), Person.proxy("Joe"), 15)
        for(Path.Segment<Person, Person> segment in path) {
            println segment.start().name
            println segment.end().name
        }
        // end::shortestPathProxy[]

        then:
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
    void "test simple shortest path"() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
            .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        Path<Person, Person> path = Person.findPath("MATCH (fred:Person { name: 'Fred' }),(joe:Person { name: 'Joe' }), p = shortestPath((fred)-[*..15]-(joe)) RETURN p")
        for(Path.Segment<Person, Person> segment in path) {
            println segment.start().name
            println segment.end().name
        }

        then:
        path.start().name == "Fred"
        path.end().name == "Joe"
        path.length() == 2
        path.collect({ grails.neo4j.Path.Segment p -> p.start().name })  == ["Fred", "Barney"]
        path.collect({ grails.neo4j.Path.Segment p -> p.end().name })  == ["Barney", "Joe"]
        path.first().relationship().from.name == "Fred"
        path.first().relationship().to.name == "Barney"
        path.first().relationship().type == 'FRIENDS'
    }

    @Rollback
    void "test simple shortest pathTo"() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
                .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        Path<Person, Person> path = Person.findPathTo(Person, "MATCH (fred:Person { name: 'Fred' }),(joe:Person { name: 'Joe' }), p = shortestPath((fred)-[*..15]-(joe)) RETURN p")

        for(Path.Segment<Person, Person> segment in path) {
            println segment.start().name
            println segment.end().name
        }
        then:
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
    void "test shortest path with arguments"() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
                .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        Path<Person, Person> path = Person.findPath('MATCH (from:Person),(to:Person), p = shortestPath((from)-[*..15]-(to)) WHERE from.name = \$from AND to.name = \$to RETURN p', [from:"Fred", to:"Joe"])
        for(Path.Segment<Person, Person> segment in path) {
            println segment.start().name
            println segment.end().name
        }

        then:
        path.start().name == "Fred"
        path.end().name == "Joe"
        path.length() == 2
        path.collect({ grails.neo4j.Path.Segment p -> p.start().name })  == ["Fred", "Barney"]
        path.collect({ grails.neo4j.Path.Segment p -> p.end().name })  == ["Barney", "Joe"]
        path.first().relationship().from.name == "Fred"
        path.first().relationship().to.name == "Barney"
        path.first().relationship().type == 'FRIENDS'
    }

    @Rollback
    void "test shortest path with gstring arguments"() {
        given:
        def barney = new Person(name: "Barney")
        def joe = new Person(name: "Joe")
        barney.addToFriends(joe)

        def fred = new Person(name: "Fred")
                .addToFriends(barney)

        fred.save(flush:true)
        Person.withSession { it.clear() }

        when:
        // tag::pathCypher[]
        String from = "Fred"
        String to = "Joe"
        Path<Person, Person> path = Person.findPath("MATCH (from:Person),(to:Person), p = shortestPath((from)-[*..15]-(to)) WHERE from.name = $from AND to.name = $to RETURN p")
        for(Path.Segment<Person, Person> segment in path) {
            println segment.start().name
            println segment.end().name
        }
        // end::pathCypher[]

        then:
        path.start().name == "Fred"
        path.end().name == "Joe"
        path.length() == 2
        path.collect({ grails.neo4j.Path.Segment p -> p.start().name })  == ["Fred", "Barney"]
        path.collect({ grails.neo4j.Path.Segment p -> p.end().name })  == ["Barney", "Joe"]
        path.first().relationship().from.name == "Fred"
        path.first().relationship().to.name == "Barney"
        path.first().relationship().type == 'FRIENDS'
    }
}
