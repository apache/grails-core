package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Face implements Serializable {
    Long id
    Long version
    String name
    Nose nose
    Person person
    static hasOne = [nose: Nose]
    static belongsTo = [person:Person]

    static constraints = {
        person nullable:true
    }
}
