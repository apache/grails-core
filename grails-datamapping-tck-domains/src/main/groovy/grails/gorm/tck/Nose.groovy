package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Nose implements Serializable {
    Long id
    Long version
    boolean hasFreckles
    Face face
    static belongsTo = [face: Face]

    static mapping = {
        face index:true
    }
}
