package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import org.bson.types.ObjectId

class OneToOneNoReferenceSpec extends GormDatastoreSpec{

    void "Test that associations can be saved with no dbrefs"() {
        when:"A domain class is saved that has references disabled"
            def other = new OtherNoRef().save()
            def noref = new NoRef(other: other)
            noref.save flush:true

        then:"The association is saved without a dbref"
            println NoRef.collection.find().first()
            NoRef.collection.find().first().other == other.id
    }

    void "Test that querying an association works"() {
        when:"A domain class is saved that has references disabled"
            def other = new OtherNoRef().save()
            def noref = new NoRef(other: other)
            noref.save flush:true
            session.clear()

            other = OtherNoRef.get(other.id)
            noref = NoRef.findByOther(other)

        then:"The association can be queried"
            other != null
            noref != null
    }

    @Override
    List getDomainClasses() {
        [OtherNoRef,NoRef]
    }
}

@Entity
class NoRef {

    ObjectId id

    OtherNoRef other

    static mapping = {
       other reference:false
    }
}

@Entity
class OtherNoRef {

    ObjectId id
}
