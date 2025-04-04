package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId

/**
 * Created by graemerocher on 29/02/16.
 */
class ObjectIdPropertySpec extends GormDatastoreSpec{

    void "test save and retrieve object id"() {
        when:"an object is saved and retrieved"

        def id = new ObjectId()
        ObjectIdPerson  o = new ObjectIdPerson(name: "Fred", scopeId: id)
        o.save(flush:true)
        session.clear()
        o = ObjectIdPerson.get(o.id)

        then:"The id is correct"
        o.scopeId == id
        session.clear()

        when:"A query is used to retrieve the object"
        o = ObjectIdPerson.findByScopeId(id)

        then:"The result is correct"
        o != null
        o.scopeId == id
    }
    @Override
    List getDomainClasses() {
        [ObjectIdPerson]
    }
}

@Entity
class ObjectIdPerson {
    ObjectId id;
    String name;
    ObjectId scopeId
}