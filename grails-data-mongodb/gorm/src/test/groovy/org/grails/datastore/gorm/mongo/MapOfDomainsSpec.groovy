package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.Plant
import grails.persistence.Entity
import groovy.transform.EqualsAndHashCode
import org.bson.types.ObjectId

/**
 * Created by graemerocher on 22/04/14.
 */
class MapOfDomainsSpec extends GormDatastoreSpec{

    void "Test that a map of embedded objects can be persisted"() {
        when:"A domain class with a map of embedded objects is persisted"
            def phones = new Smartphones()

            def data = [apple: new Smartphone(name: "iPhone"), samsung: new Smartphone(name: "Galaxy")]
            phones.phonesByManufacturer = data
            phones.save(flush:true)
            session.clear()
            phones = Smartphones.get(phones.id)

        then:"The results are correct"
            phones.phonesByManufacturer == data

    }

    @Override
    List getDomainClasses() {
        [Smartphones]
    }
}


@Entity
class Smartphones {
    ObjectId id
    Map<String, Smartphone> phonesByManufacturer

    static embedded = ['phonesByManufacturer']
}
@EqualsAndHashCode
class Smartphone {
    String name
}
