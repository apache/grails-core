package grails.gorm.tests.cypher.domain

import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity

@Entity
// tag::class[]
class Owner implements Neo4jEntity<Owner> {
    String name
    static hasMany = [pets:Pet]
}
// end::class[]