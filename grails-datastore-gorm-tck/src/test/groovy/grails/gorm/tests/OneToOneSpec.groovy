package grails.gorm.tests

import grails.persistence.Entity
import org.grails.datastore.mapping.model.types.OneToOne

class OneToOneSpec extends GormDatastoreSpec {

    def "Test persist and retrieve unidirectional many-to-one"() {
        given:"A domain model with a many-to-one"
            def person = new Person(firstName:"Fred", lastName: "Flintstone")
            def pet = new Pet(name:"Dino", owner:person)
            person.save()
            pet.save(flush:true)
            session.clear()

        when:"The association is queried"
            pet = Pet.findByName("Dino")

        then:"The domain model is valid"
            pet != null
            pet.name == "Dino"
            pet.ownerId == person.id
            pet.owner.firstName == "Fred"
    }

    def "Test persist and retrieve one-to-one with inverse key"() {
        given:"A domain model with a one-to-one"
            def face = new Face(name:"Joe")
            def nose = new Nose(hasFreckles: true, face:face)
            face.nose = nose
            face.save(flush:true)
            session.clear()

        when:"The association is queried"
            face = Face.get(face.id)
            def association = Face.gormPersistentEntity.getPropertyByName('nose')
        then:"The domain model is valid"
            association instanceof OneToOne
            association.bidirectional
            association.associatedEntity.javaClass == Nose
            face != null
            face.noseId == nose.id
            face.nose != null
            face.nose.hasFreckles == true

        when:"The inverse association is queried"
            session.clear()
            nose = Nose.get(nose.id)

        then:"The domain model is valid"
            nose != null
            nose.hasFreckles == true
            nose.face != null
            nose.face.name == "Joe"
    }
}

@Entity
class Face implements Serializable {
    Long id
    Long version
    String name
    Nose nose
    Person person
    static hasOne = [nose: Nose]
    static belongsTo = [person:Person]

    static constraints = {
        person nullable:true
    }
}

@Entity
class Nose implements Serializable {
    Long id
    Long version
    boolean hasFreckles
    Face face
    static belongsTo = [face: Face]

    static mapping = {
        face index:true
    }
}
