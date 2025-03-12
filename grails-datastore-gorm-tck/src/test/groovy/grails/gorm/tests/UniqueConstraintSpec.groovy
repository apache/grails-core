package grails.gorm.tests

import grails.persistence.Entity
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable

/**
 * Tests the unique constraint
 */

class UniqueConstraintSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [UniqueGroup, GroupWithin]
    }
}

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