package grails.gorm.tests

/**
 * Abstract base test for order by queries. Subclasses should do the necessary setup to configure GORM
 */
class OrderBySpec extends GormDatastoreSpec {

    void "Test order with criteria"() {
        given:
            def age = 40

            ["Bob", "Fred", "Barney", "Frank", "Joe", "Ernie"].each {
                new TestEntity(name:it, age: age++, child:new ChildEntity(name:"$it Child")).save()
            }

        when:
            def results = TestEntity.createCriteria().list {
                order "age"
            }
        then:
            40 == results[0].age
            41 == results[1].age
            42 == results[2].age

        when:
            results = TestEntity.createCriteria().list {
                order "age", "desc"
            }

        then:
            45 == results[0].age
            44 == results[1].age
            43 == results[2].age
    }
    void "Test order by with list() method"() {
        given:
            def age = 40

            ["Bob", "Fred", "Barney", "Frank", "Joe", "Ernie"].each {
                new TestEntity(name:it, age: age++, child:new ChildEntity(name:"$it Child")).save()
            }

        when:
            def results = TestEntity.list(sort:"age")

        then:
            40 == results[0].age
            41 == results[1].age
            42 == results[2].age

        when:
            results = TestEntity.list(sort:"age", order:"desc")

        then:
            45 == results[0].age
            44 == results[1].age
            43 == results[2].age
    }

    void "Test order by property name with dynamic finder"() {
        given:
            def age = 40

            ["Bob", "Fred", "Barney", "Frank", "Joe", "Ernie"].each {
                new TestEntity(name:it, age: age++, child:new ChildEntity(name:"$it Child")).save()
            }

        when:
            def results = TestEntity.findAllByAgeGreaterThanEquals(40, [sort:"age"])

        then:
            40 == results[0].age
            41 == results[1].age
            42 == results[2].age

        when:
            results = TestEntity.findAllByAgeGreaterThanEquals(40, [sort:"age", order:"desc"])

        then:
            45 == results[0].age
            44 == results[1].age
            43 == results[2].age
    }
}
