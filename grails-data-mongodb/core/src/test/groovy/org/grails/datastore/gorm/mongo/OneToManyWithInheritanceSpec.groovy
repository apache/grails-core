package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import grails.persistence.Entity

class OneToManyWithInheritanceSpec extends GormDatastoreSpec {

    void "Test that a one-to-many with inheritances behaves correctly"() {
        given:"A one-to-many association inherited from a parent"
            Animal animal = new Animal().save()
            Donkey donkey = new Donkey(name: "Eeyore").save()
            new Carrot(leaves: 1, animal: animal).save()
            new Carrot(leaves: 2, animal: animal).save()
            new Carrot(leaves: 3, animal: donkey).save()
            new Carrot(leaves: 4, animal: donkey).save(flush:true)
            session.clear()

        when:"The association is loaded"
            animal = Animal.get(animal.id)
            donkey = Donkey.get(donkey.id)

        then:"The association is correctly loaded"
            animal.carrots.size() == 2
            donkey.carrots.size() == 2
    }

    @Override
    List getDomainClasses() {
       [Animal,Donkey, Carrot]
    }
}

@Entity
class Donkey extends Animal implements MongoEntity<Donkey> {
    String name
}

@Entity
class Animal implements MongoEntity<Animal> {
    String id
    Set carrots = []
    static hasMany = [carrots:Carrot]
}

@Entity
class Carrot implements MongoEntity<Carrot> {
    Long id
    Integer leaves
    Animal animal
    static belongsTo = [animal:Animal]
}
