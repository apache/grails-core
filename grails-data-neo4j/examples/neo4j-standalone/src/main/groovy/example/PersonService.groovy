package example

import grails.gorm.services.Service
import grails.neo4j.Path
import grails.neo4j.services.Cypher

@Service(Person)
interface PersonService {

    Person findPerson(String name)

    Path<Person, Person> findPath(Person from, Person to)

    @Cypher("""MATCH ${Person from},${Person to}, p = shortestPath(($from)-[*..15]-($to)) 
               WHERE $from.name = $start AND $to.name = $end 
               RETURN p""")
    Path<Person, Person> findPath(String start, String end)

    @Cypher("""MATCH ${Person p} 
               WHERE $p.name = $name  
               SET p.age = $age""")
    void updatePerson(String name, int age)
}
