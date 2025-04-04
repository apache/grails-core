package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Dog implements Serializable{
    Long id
    int age
    int deathAge
    String name

    static mapping = {
        age index:true
        name index:true
    }
}