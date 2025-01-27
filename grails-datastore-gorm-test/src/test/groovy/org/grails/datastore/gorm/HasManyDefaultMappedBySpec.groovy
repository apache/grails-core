package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import org.grails.datastore.mapping.model.types.Association

class HasManyDefaultMappedBySpec extends GormDatastoreSpec {

    void "Test that has-many with multiple potential matches for the other side matches correctly"() {

        when:"A has many with multiple potential matching sides is retrieved"
            def entity = session.datastore.mappingContext.getPersistentEntity(MyDomain.name)
            Association p = entity.getPropertyByName("childs")

        then:"The other side is correctly mapped"
            p != null
            p.inverseSide != null
            p.inverseSide.name == 'parent'
    }

    @Override
    List getDomainClasses() {
        [MyDomain, ChildDomain]
    }
}

@Entity
class MyDomain {
    Long id
    Set childs
    static hasMany = [childs:ChildDomain]
}

@Entity
class ChildDomain {
    Long id
    static belongsTo = [parent:MyDomain]

    def getSomething() {}
    def myService

    MyDomain parent
}
