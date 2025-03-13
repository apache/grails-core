package grails.gorm.tck

import grails.persistence.Entity

@Entity
class EnumThing {
    Long id
    Long version
    String name
    TestEnum en

    static mapping = {
        name index: true
        en index: true
    }
}
