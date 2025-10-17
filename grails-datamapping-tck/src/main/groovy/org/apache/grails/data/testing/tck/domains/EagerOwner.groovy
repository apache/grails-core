package org.apache.grails.data.testing.tck.domains

import grails.persistence.Entity

@Entity
class EagerOwner implements Serializable {
    Set<Pet> pets = [] as Set
    Integer column1
    Integer column2
    static hasMany = [pets: Pet]
    static mapping = {
        pets lazy : false
    }
}