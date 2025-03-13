package grails.gorm.tck

import grails.persistence.Entity

@Entity
class PetType implements Serializable {
    private static final long serialVersionUID = 1
    Long id
    Long version
    String name

    static belongsTo = Pet
}
