package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Highway implements Serializable {
    Long id
    Long version
    Boolean bypassed
    String name

    static mapping = {
        bypassed index:true
        name index:true
    }
}
