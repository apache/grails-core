package grails.gorm.tck

import grails.persistence.Entity

@Entity
class OptLockVersioned implements Serializable {
    Long id
    Long version

    String name
}