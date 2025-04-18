package example

import grails.gorm.annotation.Entity
import grails.neo4j.Node
import grails.neo4j.mapping.MappingBuilder
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(includes = 'name')
@Entity
class Person implements Node<Person> {
    String name
    static hasMany = [friends: Person]

    static mapping = MappingBuilder.node {
        id(generator:'assigned', name:'name')
    }
}
