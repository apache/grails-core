package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Simples implements Serializable {
    Long id
    String name
}