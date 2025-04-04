package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Practice implements Serializable {
//    Long id
    Long version
    String name
    static hasMany = [locations: Location]
}
