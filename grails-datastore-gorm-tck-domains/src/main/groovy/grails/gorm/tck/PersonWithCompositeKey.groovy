package grails.gorm.tck

import grails.persistence.Entity

@Entity
class PersonWithCompositeKey implements Serializable {
    Long version
    String firstName
    String lastName
    Integer age
    static mapping = {
        id composite: ['lastName', 'firstName']
    }
}