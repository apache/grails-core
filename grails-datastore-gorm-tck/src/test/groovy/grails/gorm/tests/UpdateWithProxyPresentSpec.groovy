package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.async.AsyncEntity
import grails.gorm.dirty.checking.DirtyCheck
import grails.persistence.Entity
import groovy.transform.EqualsAndHashCode
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

/**
 * @author graemerocher
 */
class UpdateWithProxyPresentSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Pet, Person, PetType, Parent, Child]
    }

    void "Test update entity with association proxies"() {
        given:
            def person = new Person(firstName:"Bob", lastName:"Builder")
            def petType = new PetType(name:"snake")
            def pet = new Pet(name:"Fred", type:petType, owner:person)
            person.addToPets(pet)
            person.save(flush:true)
            session.clear()

        when:
            person = Person.get(person.id)
            person.firstName = "changed"
            person.save(flush:true)
            session.clear()
            person = Person.get(person.id)
            def personPet = person.pets.iterator().next()

        then:
            person.firstName == "changed"
            personPet.name == "Fred"
            personPet.id == pet.id
            personPet.owner.id == person.id
            personPet.type.name == 'snake'
            personPet.type.id == petType.id
    }

    void "Test update unidirectional oneToMany with proxy"() {
        given:
        def parent = new Parent(name: "Bob").save(flush: true)
        def child = new Child(name: "Bill").save(flush: true)
        session.clear()

        when:
        parent = Parent.get(parent.id)
        child = Child.load(child.id) // make sure we've got a proxy.
        then:
        session.mappingContext.proxyFactory.isProxy(child)==true
        
        when:
        parent.addToChildren(child)
        parent.save(flush: true)
        session.clear()
        parent = Parent.get(parent.id)

        then:
        parent.name == 'Bob'
        parent.children.size() == 1

        when:
        child = parent.children.first()

        then:
        child.name == "Bill"
    }
}

@Entity
class Pet implements Serializable {
    Long id
    Long version
    String name
    Date birthDate = new Date()
    PetType type = new PetType(name:"Unknown")
    Person owner
    Integer age
    Face face

    static mapping = {
        name index:true
    }

    static constraints = {
        owner nullable:true
        age nullable: true
        face nullable:true
    }
}

@DirtyCheck
@Entity
@ApplyDetachedCriteriaTransform
//@groovy.transform.EqualsAndHashCode - breaks gorm-neo4j: TODO: http://jira.grails.org/browse/GPNEO4J-10
@EqualsAndHashCode(includes = ['firstName', 'lastName', 'age'])
class Person implements Serializable, Comparable<Person>, AsyncEntity<Person> {
    static simpsons = where {
         lastName == "Simpson"
    }

    Long id
    Long version
    String firstName
    String lastName
    Integer age = 0
    Set<Pet> pets = [] as Set
    static hasMany = [pets:Pet]
    Face face
    boolean myBooleanProperty

    static DetachedCriteria<Person> withAge(int a) {
        Person.where {
            age > a
        }
    }

    static DetachedCriteria<Person> peopleWithOlderPets = where {
        pets {
            age > 9
        }
    }
    static DetachedCriteria<Person> peopleWithOlderPets2 = where {
        pets.age > 9
    }

    static Person getByFirstNameAndLastNameAndAge(String firstName, String lastName, int age) {
        find( new Person(firstName: firstName, lastName: lastName, age: age) )
    }

    static mapping = {
        firstName index:true
        lastName index:true
        age index:true
    }

    static constraints = {
        face nullable:true
    }

    @Override
    int compareTo(Person t) {
        age <=> t.age
    }
}

@Entity
class PetType implements Serializable {
    private static final long serialVersionUID = 1
    Long id
    Long version
    String name

    static belongsTo = Pet
}

@Entity
class Parent implements Serializable {
    private static final long serialVersionUID = 1
    Long id
    String name
    Set<Child> children = []
    static hasMany = [children: Child]
}

@Entity
class Child implements Serializable {
    private static final long serialVersionUID = 1
    Long id
    Long version
    String name
}


