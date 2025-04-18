package grails.gorm.tests.cypher.domain

import grails.gorm.annotation.Entity

@Entity
// tag::class[]
class Pet {
    String name
    static belongsTo = [owner:Owner]
}
// end::class[]