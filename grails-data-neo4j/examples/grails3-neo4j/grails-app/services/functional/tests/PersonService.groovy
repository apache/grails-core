package functional.tests

import grails.gorm.services.Service
import grails.neo4j.Path
import grails.neo4j.services.Cypher

// tag::class[]
@Service(Person)
interface PersonService {
// end::class[]

    // tag::findPath[]
    Path<Person, Person> findPath(Person from, Person to)
    // end::findPath[]

    // tag::findPathCypher[]
    @Cypher("""MATCH ${Person from},${Person to}, p = shortestPath(($from)-[*..15]-($to)) 
               WHERE $from.name = $start AND $to.name = $end 
               RETURN p""")
    Path<Person, Person> findPath(String start, String end)
    // end::findPathCypher[]

    // tag::updatePerson[]
    @Cypher("""MATCH ${Person p} 
               WHERE $p.name = $name  
               SET p.age = $age""")
    void updatePerson(String name, int age)
    // end::updatePerson[]
}
