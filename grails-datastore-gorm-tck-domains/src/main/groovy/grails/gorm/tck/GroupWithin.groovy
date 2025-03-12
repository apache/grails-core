package grails.gorm.tck

import grails.persistence.Entity

@Entity
class GroupWithin implements Serializable {
    Long id
    Long version
    String name
    String org
    static constraints = {
        name unique:"org", index:true
        org index:true
    }
}