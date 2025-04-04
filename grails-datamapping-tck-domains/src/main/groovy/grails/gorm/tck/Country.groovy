package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Country extends Location {
    Integer population = 0

    static hasMany = [residents:Person]
    Set residents
}
