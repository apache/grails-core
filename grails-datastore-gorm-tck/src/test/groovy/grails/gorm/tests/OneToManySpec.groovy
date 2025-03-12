package grails.gorm.tests

/**
 * @author graemerocher
 */
class OneToManySpec extends GormDatastoreSpec {

    void "test save and return unidirectional one to many"() {
        given:
            Person p = new Person(firstName: "Fred", lastName: "Flinstone")
            Country c = new Country(name:"Dinoville")
                            .addToResidents(p)
                            .save(flush:true)

            session.clear()

        when:
            c = Country.findByName("Dinoville")

        then:
            c != null
            c.residents != null
            c.residents.size() == 1
            c.residents.every { it instanceof Person } == true

        when:
            c.addToResidents(new Person(firstName:"Barney", lastName:"Rubble"))
            c.save(flush:true)
            session.clear()
            c = Country.findByName("Dinoville")

        then:
            c != null
            c.residents != null
            c.residents.size() == 2
            c.residents.every { it instanceof Person } == true
    }

    void "test save and return bidirectional one to many"() {
        given:
            Person p = new Person(firstName: "Fred", lastName: "Flinstone")
            p.addToPets(new Pet(name: "Dino", type: new PetType(name: "Dinosaur")))
            p.save(flush:true)

            new Person(firstName: "Barney", lastName: "Rubble")
                .addToPets(new Pet(name: "T Rex", type: new PetType(name: "Dinosaur")))
                .addToPets(new Pet(name: "Stego", type: new PetType(name: "Dinosaur")))
                .save(flush:true)

            session.clear()

        when:
            p = Person.findByFirstName("Fred")

        then:
            p != null
            p.pets != null
            p.pets.size() == 1
            def pet = p.pets.iterator().next()
            pet instanceof Pet
            pet.name == 'Dino'
            pet.type != null
            pet.type.name == 'Dinosaur'

        when:
            p.addToPets(new Pet(name: "Rex", type: new PetType(name: "Dinosaur")))
            p.save(flush:true)
            session.clear()
            p = Person.findByFirstName("Fred")

        then:
            p != null
            p.pets != null
            p.pets.size() == 2
            p.pets.every { it instanceof Pet } == true
    }

    void "test update inverse side of bidirectional one to many collection"() {
        given:
            Person p = new Person(firstName: "Fred", lastName: "Flinstone").save()
            new Pet(name: "Dino", type: new PetType(name: "Dinosaur"), owner:p).save()
            Person p2 = new Person(firstName: "Barney", lastName: "Rubble").save()
            new Pet(name: "T Rex", type: new PetType(name: "Dinosaur"), owner:p2).save()
            new Pet(name: "Stego", type: new PetType(name: "Dinosaur"), owner:p2).save(flush:true)

            session.clear()

        when:
            p = Person.findByFirstName("Fred")

        then:
            p != null
            p.pets != null
            p.pets.size() == 1
            def pet = p.pets.iterator().next()
            pet instanceof Pet
            pet.name == 'Dino'
            pet.type != null
            pet.type.name == 'Dinosaur'
    }

    void "test update inverse side of bidirectional one to many happens before flushing the session"() {

        if (session.datastore.getClass().name.contains('Hibernate')) {
            return
        }

        given:
            Person person = new Person(firstName: "Fred", lastName: "Flinstone").save()
            Pet dino = new Pet(name: "Dino", type: new PetType(name: "Dinosaur"), owner:person).save()
            Pet trex = new Pet(name: "Trex", type: new PetType(name: "Dinosaur"), owner:person).save()

        expect:
            dino.owner == person
            trex.owner == person
            person.pets.size() == 2

        when:
            session.flush()
            session.clear()
            person = Person.findByLastName('Flinstone')

        then:
            person
            person.pets.size() == 2
    }

    void "Test persist of association with proxy"() {
        given: "A domain model with a many-to-one"
        def person = new Person(firstName: "Fred", lastName: "Flintstone")
        person.save(flush:true)
        session.clear()
        def pet = new Pet(name: "Dino", owner: Person.load(person.id))
        pet.save(flush: true)
        session.clear()

        when: "The association is queried"
        pet = Pet.findByName("Dino")

        then: "The domain model is valid"
        pet != null
        pet.name == "Dino"
        pet.owner != null
        pet.owner.firstName == "Fred"
    }
}
