package grails.gorm.tck

import grails.persistence.Entity
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable

@Entity
class UniqueGroup implements Serializable, DirtyCheckable {
    Long id
    Long version
    String name
    String desc
    static constraints = {
        name unique:true, index:true
        desc nullable: true
    }
}