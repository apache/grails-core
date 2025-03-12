package grails.gorm.tests

import grails.persistence.Entity

/**
 * Tests for criteria queries that compare two properties
 */
class PropertyComparisonQuerySpec extends GormDatastoreSpec {

    void "Test geProperty query"() {
        given:"Some dead and alive dogs"
            new Dog(name:"Barney", age:7, deathAge:14).save()
            new Dog(name:"Fred", age:13, deathAge:13).save()
            new Dog(name:"Jack", age:14, deathAge:13).save()
            new Dog(name:"Joe", age:4, deathAge:14).save(flush:true)

        when:"We query for dogs that are alive"
            def results = Dog.withCriteria {
                geProperty 'age', 'deathAge'
                order 'name'
            }

        then:"3 dogs are found"
            Dog.count() == 4
            results.size() == 2
            results[0].name == "Fred"
            results[1].name == "Jack"
    }
    void "Test leProperty query"() {
        given:"Some dead and alive dogs"
            new Dog(name:"Barney", age:7, deathAge:14).save()
            new Dog(name:"Fred", age:13, deathAge:13).save()
            new Dog(name:"Jack", age:14, deathAge:13).save()
            new Dog(name:"Joe", age:4, deathAge:14).save(flush:true)

        when:"We query for dogs that are alive"
            def results = Dog.withCriteria {
                leProperty 'age', 'deathAge'
                order 'name'
            }

        then:"3 dogs are found"
            Dog.count() == 4
            results.size() == 3
            results[0].name == "Barney"
            results[1].name == "Fred"
            results[2].name == "Joe"
    }

    void "Test ltProperty query"() {
        given:"Some dead and alive dogs"
            new Dog(name:"Barney", age:7, deathAge:14).save()
            new Dog(name:"Fred", age:13, deathAge:13).save()
            new Dog(name:"Joe", age:4, deathAge:14).save(flush:true)

        when:"We query for dogs that are alive"
            def results = Dog.withCriteria {
                ltProperty 'age', 'deathAge'
                order 'name'
            }

        then:"2 dogs are found"
            Dog.count() == 3
            results.size() == 2
            results[0].name == "Barney"
            results[1].name == "Joe"
    }

    void "Test gtProperty query"() {
        given:"Some dead and alive dogs"
            new Dog(name:"Barney", age:7, deathAge:14).save()
            new Dog(name:"Fred", age:13, deathAge:13).save()
            new Dog(name:"Joe", age:4, deathAge:14).save(flush:true)

        when:"We query for dogs that are alive"
            def results = Dog.withCriteria {
                gtProperty 'deathAge', 'age'
                order 'name'
            }

        then:"2 dogs are found"
            Dog.count() == 3
            results.size() == 2
            results[0].name == "Barney"
            results[1].name == "Joe"
    }

    void "Test neProperty query"() {
        given:"Some dead and alive dogs"
            new Dog(name:"Barney", age:7, deathAge:14).save()
            new Dog(name:"Fred", age:13, deathAge:13).save()
            new Dog(name:"Joe", age:4, deathAge:14).save(flush:true)

        when:"We query for dogs that are alive"
            def results = Dog.withCriteria {
                neProperty 'age', 'deathAge'
                order 'name'
            }

        then:"2 dogs are found"
            Dog.count() == 3
            results.size() == 2
            results[0].name == "Barney"
            results[1].name == "Joe"
    }

    void "Test eqProperty query"() {
        given:"Some dead and alive dogs"
            new Dog(name:"Barney", age:7, deathAge:14).save()
            new Dog(name:"Fred", age:13, deathAge:13).save()
            new Dog(name:"Joe", age:4, deathAge:14).save(flush:true)

        when:"We query for dogs that died"
            def results = Dog.withCriteria {
                eqProperty 'age', 'deathAge'
            }

        then:"1 dog is found"
            Dog.count() == 3
            results.size() == 1
            results[0].name == "Fred"
    }

    @Override
    List getDomainClasses() {
        [Dog]
    }
}

@Entity
class Dog implements Serializable{
    Long id
    int age
    int deathAge
    String name

    static mapping = {
        age index:true
        name index:true
    }
}