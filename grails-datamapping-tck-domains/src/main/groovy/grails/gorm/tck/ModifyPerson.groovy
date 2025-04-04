package grails.gorm.tck

import grails.persistence.Entity

@Entity
class ModifyPerson implements Serializable {
    Long id
    Long version

    String name

    static mapping = {
        name index:true
    }

    def beforeInsert() {
        name = "Fred"
    }
}
