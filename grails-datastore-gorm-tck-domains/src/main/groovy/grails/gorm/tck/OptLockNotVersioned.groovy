package grails.gorm.tck

import grails.persistence.Entity

@Entity
class OptLockNotVersioned implements Serializable {
    Long id
    Long version

    String name

    static mapping = {
        version false
    }
}
