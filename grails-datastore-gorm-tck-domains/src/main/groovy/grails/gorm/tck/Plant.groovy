package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Plant implements Serializable {
    Long id
    Long version
    boolean goesInPatch
    String name

    static mapping = {
        name index:true
        goesInPatch index:true
    }
}
