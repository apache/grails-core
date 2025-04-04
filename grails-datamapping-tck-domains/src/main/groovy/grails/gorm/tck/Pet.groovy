package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Pet implements Serializable {
    Long id
    Long version
    String name
    Date birthDate = new Date()
    PetType type = new PetType(name:"Unknown")
    Person owner
    Integer age
    Face face

    static mapping = {
        name index:true
    }

    static constraints = {
        owner nullable:true
        age nullable: true
        face nullable:true
    }
}
