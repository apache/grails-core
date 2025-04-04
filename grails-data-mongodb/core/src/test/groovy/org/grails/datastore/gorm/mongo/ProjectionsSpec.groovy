package org.grails.datastore.gorm.mongo

import grails.gorm.DetachedCriteria
import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * Created by graemerocher on 15/04/14.
 */
class ProjectionsSpec extends GormDatastoreSpec {

    void "Test distinct projection with detached criteria"() {
        given: "Some test data"
        new Dog(name: "Fred", age: 6).save()
        new Dog(name: "Ginger", age: 2).save()
        new Dog(name: "Rastas", age: 4).save()
        new Dog(name: "Albert", age: 11).save()
        new Dog(name: "Joe", age: 2).save(flush: true)

        when: "A age projection is used"
        def ages = new DetachedCriteria<Dog>(Dog).distinct('age').list().sort()

        then: "The result is correct"
        ages == [2, 4, 6, 11]

        when: "A age projection is used"
        ages = Dog.where {}.distinct('age').list().sort()

        then: "The result is correct"
        ages == [2, 4, 6, 11]
    }

    void "Test sum projection"() {
        given: "Some test data"
        new Dog(name: "Fred", age: 6).save()
        new Dog(name: "Ginger", age: 2).save()
        new Dog(name: "Rastas", age: 4).save()
        new Dog(name: "Albert", age: 11).save()
        new Dog(name: "Joe", age: 2).save(flush: true)

        when: "A sum projection is used"
        def avg = Dog.createCriteria().list {
            projections {
                avg 'age'
                max 'age'
                min 'age'
                sum 'age'
                count()
            }
        }

        then: "The result is correct"
        Dog.count() == 5
        avg == [5, 11, 2, 25, 5]
    }

    @Issue('GPMONGODB-294')
    void "Test multiple projections"() {
        given: "Some test data"
        new Dog(name: "Fred", age: 6).save()
        new Dog(name: "Joe", age: 2).save(flush: true)

        when: "A sum projection is used"
        List results = Dog.createCriteria().list {
            projections {
                property 'name'
                property 'age'
            }
            order 'name'
        }

        then: "The result is correct"
        results.size() == 2
        [["Joe", 2], ["Fred", 6]].containsAll(results)
    }

    @Override
    List getDomainClasses() {
        [Dog]
    }
}

@Entity
class Dog {
    ObjectId id
    String name
    int age
}
