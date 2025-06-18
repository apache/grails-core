package org.apache.grails.data.testing.tck.domains

import grails.persistence.Entity

@Entity
class SimpleCountry {
    Integer id
    String name

    static hasMany = [residents: Person]
    Set residents
}