package grails.gorm.tck

import grails.persistence.Entity

@Entity
class SimpleWidget implements Serializable {
    Long id
    Long version
    String name
    String spanishName
}