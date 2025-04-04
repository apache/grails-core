package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Location implements Serializable {
//    Long id
    Long version
    String name
    String code = "DEFAULT"

    def namedAndCode() {
        "$name - $code"
    }

    static mapping = {
        name index:true
        code index:true
    }
}
