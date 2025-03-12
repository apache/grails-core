package grails.gorm.tests

import grails.persistence.Entity

/**
 * @author graemerocher
 */
@Entity
class ChildEntity implements Serializable {
    Long id
    Long version
    String name

    static mapping = {
        name index:true
    }

    static belongsTo = [TestEntity]
}