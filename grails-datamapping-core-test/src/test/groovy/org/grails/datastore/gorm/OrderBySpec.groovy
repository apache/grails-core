package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.TestEntity
import grails.gorm.tck.ChildEntity

/**
 * @author Daniel Wiell
 */
class OrderBySpec extends GormDatastoreSpec {
    def setup() {
        def age = 40
        ["Bob", "Fred", "Barney", "Frank", "Joe", "Ernie"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }
    }

    def 'Test order by property name with dynamic finder returning first result'() {
        when:
        def result = TestEntity.findByAgeGreaterThanEquals(40, [sort: "age", order: 'desc'])

        then:
        45 == result.age
    }

    def 'Test order by property name with dynamic finder using max'() {
        when:
        def results = TestEntity.findAllByAgeGreaterThanEquals(40, [sort: "age", order: 'desc', max:  1])

        then:
        45 == results[0].age
    }

    def 'Test order by with list() method using max'() {
        when:
        def results = TestEntity.list(sort: "age", order:  'desc', max: 1)

        then:
        45 == results[0].age
    }

    def 'Test order by with criteria using maxResults'() {
        when:
        def results = TestEntity.withCriteria {
            order 'age', 'desc'
            maxResults 1
        }

        then:
        45 == results[0].age
    }
}
