package grails.gorm.tests

class SaveAllSpec extends GormDatastoreSpec {

    def "Test that many objects can be saved at once using multiple arguments"() {
        given:
            def bob = new Person(firstName:"Bob", lastName:"Builder")
            def fred = new Person(firstName:"Fred", lastName:"Flintstone")
            def joe = new Person(firstName:"Joe", lastName:"Doe")

            Person.saveAll(bob, fred, joe)

        when:
            def total = Person.count()
            def results = Person.list()
        then:
            total == 3
            results.every { it.id != null } == true
    }

    def "Test that many objects can be saved at once using a list"() {
        given:
            def bob = new Person(firstName:"Bob", lastName:"Builder")
            def fred = new Person(firstName:"Fred", lastName:"Flintstone")
            def joe = new Person(firstName:"Joe", lastName:"Doe")

            Person.saveAll([bob, fred, joe])

        when:
            def total = Person.count()
            def results = Person.list()
        then:
            total == 3
            results.every { it.id != null } == true
    }

    def "Test that many objects can be saved at once using an iterable"() {
        given:
            def bob = new Person(firstName:"Bob", lastName:"Builder")
            def fred = new Person(firstName:"Fred", lastName:"Flintstone")
            def joe = new Person(firstName:"Joe", lastName:"Doe")

            Vector<Person> personVector = new Vector<Person>()
            personVector.add(bob)
            personVector.add(fred)
            personVector.add(joe)

            Person.saveAll(personVector)

        when:
            def total = Person.count()
            def results = Person.list()
        then:
            total == 3
            results.every { it.id != null } == true
    }
}
