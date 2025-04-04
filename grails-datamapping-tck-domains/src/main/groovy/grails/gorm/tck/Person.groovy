package grails.gorm.tck

import grails.gorm.async.AsyncEntity
import grails.gorm.DetachedCriteria
import grails.gorm.dirty.checking.DirtyCheck
import grails.persistence.Entity
import groovy.transform.EqualsAndHashCode
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

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
    static hasMany = [pets: Pet]
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
        find(new Person(firstName: firstName, lastName: lastName, age: age))
    }

    static mapping = {
        firstName index: true
        lastName index: true
        age index: true
    }

    static constraints = {
        face nullable: true
    }

    @Override
    int compareTo(Person t) {
        age <=> t.age
    }
}
