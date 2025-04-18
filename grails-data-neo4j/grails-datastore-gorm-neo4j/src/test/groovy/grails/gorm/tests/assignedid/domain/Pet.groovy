package grails.gorm.tests.assignedid.domain

import grails.gorm.annotation.Entity

import static grails.neo4j.mapping.MappingBuilder.node

@Entity
// tag::class[]
class Pet {
    String name
    static belongsTo = [owner:Owner]
    static mapping = node {
        id generator:'assigned', name:'name'
    }
}
// end::class[]