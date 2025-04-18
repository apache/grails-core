package grails.gorm.tests.assignedid.domain

import grails.gorm.annotation.Entity
import static grails.neo4j.mapping.MappingBuilder.*

@Entity
// tag::class[]
class Owner {
    String name
    static hasMany = [pets:Pet]
    static mapping = node {
        id generator:'assigned', name:'name'
    }
}
// end::class[]