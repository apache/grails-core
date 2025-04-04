package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import org.bson.Document
import org.bson.types.ObjectId
import grails.persistence.Entity

/**
 *
 */
class NullsAreNotStoredSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [NANSPerson]
    }
    void "Test that null values are not stored on domain creation"() {
        given:"A domain model with fields that are null"
            NANSPerson person = new NANSPerson()
            person.save(flush:true,validate:false)
            session.clear()

        when:"The instance is read from the database"
            Document personObj = NANSPerson.collection.find(new Document('_id', person.id)).first()

        then:"The null-valued fields are not stored"
            personObj != null
            !personObj.containsKey("name")
    }

    void "Test that null values are not stored on domain update"() {
        given:"A domain model with fields that are null"
            NANSPerson person = new NANSPerson(name: "John Smith")
            person.save(flush:true,validate:false)
            session.clear()

        when:"The instance is updated and read from the database"
            person = NANSPerson.get(person.id)
            person.name = null
            person.save(flush: true,validate:false)
            session.clear()
            Document personObj = NANSPerson.collection.find(new Document('_id', person.id)).first()

        then:"The null-valued fields are not stored"
            personObj != null
            !personObj.containsKey("name")
    }
}

@Entity
class NANSPerson implements MongoEntity<NANSPerson> {
    ObjectId id
    String name
}
