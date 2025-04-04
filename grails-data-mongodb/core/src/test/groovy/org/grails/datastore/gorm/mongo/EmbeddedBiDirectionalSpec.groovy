package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec

/**
 * Created by Jim on 8/18/2016.
 */
class EmbeddedBiDirectionalSpec extends GormDatastoreSpec {

    void "test nested backreferences"() {
        when:"A domain is created with nested embedded collections"
        def owner = new EBDDogOwner(name: "Joe")
        EBDDog dog = new EBDDog(name: "Rex")
        dog.addToToys(manufacturer: 'Mattel')
        owner.addToDogs(dog)
        owner.save(flush: true)
        session.clear()

        owner = EBDDogOwner.findByName("Joe")

        then:"All entities are saved with back references"
        owner != null
        owner.dogs.size() == 1
        owner.dogs[0].owner != null
        owner.dogs[0].toys.size() == 1
        owner.dogs[0].toys[0].dog != null
    }

    @Override
    List getDomainClasses() {
        [EBDDogOwner, EBDDog, EBDToy]
    }
}

@Entity
class EBDDogOwner {
    String name
    static hasMany = ['dogs': EBDDog]
    static embedded = ['dogs']
}

@Entity
class EBDDog {
    String name
    static belongsTo = ['owner': EBDDogOwner]
    static hasMany = ['toys': EBDToy]
    static embedded = ['toys']
}

@Entity
class EBDToy {
    String manufacturer
    static belongsTo = ['dog': EBDDog]
}