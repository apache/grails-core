package grails.gorm.tck

import grails.gorm.DetachedCriteria
import grails.gorm.async.AsyncEntity
import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.tck.Child
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.Parent
import grails.gorm.tck.Person
import grails.gorm.tck.Pet
import grails.gorm.tck.PetType
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
