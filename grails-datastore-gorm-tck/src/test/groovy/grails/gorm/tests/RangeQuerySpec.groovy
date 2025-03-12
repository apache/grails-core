package grails.gorm.tests

import groovy.time.TimeCategory

/**
 * Abstract base test for querying ranges. Subclasses should do the necessary setup to configure GORM
 */
class RangeQuerySpec extends GormDatastoreSpec {

    void "Test between query with dates"() {
        given:
            def now = new Date()
            use(TimeCategory) {
                new Publication(title:"The Guardian", datePublished: now - 5.minutes).save()
                new Publication(title:"The Times", datePublished: now - 5.days).save()
                new Publication(title:"The Observer", datePublished: now - 10.days).save()
            }

        when:
            def results = use(TimeCategory) {
                Publication.findAllByDatePublishedBetween(now-6.days, now)
            }

        then:
            results != null
            results.size() == 2
    }

    void "Test between query"() {
        given:
            int age = 40
            ["Bob", "Fred", "Barney", "Frank", "Joe", "Ernie"].each { new TestEntity(name:it, age: age--, child:new ChildEntity(name:"$it Child")).save() }

        when:
            def results = TestEntity.findAllByAgeBetween(38, 40)

        then:
            3 == results.size()

        when:
            results = TestEntity.findAllByAgeBetween(38, 40)

        then:
            3 == results.size()

            results.find { it.name == "Bob" } != null
            results.find { it.name == "Fred" } != null
            results.find { it.name == "Barney" } != null

        when:
            results = TestEntity.findAllByAgeBetweenOrName(38, 40, "Ernie")

        then:
            4 == results.size()
    }

    void "Test greater than or equal to and less than or equal to queries"() {
        given:

            int age = 40
            ["Bob", "Fred", "Barney", "Frank", "Joe", "Ernie"].each { new TestEntity(name:it, age: age--, child:new ChildEntity(name:"$it Child")).save() }

        when:
            def results = TestEntity.findAllByAgeGreaterThanEquals(38)

        then:
            3 == results.size()
            results.find { it.age == 38 } != null
            results.find { it.age == 39 } != null
            results.find { it.age == 40 } != null

        when:
            results = TestEntity.findAllByAgeLessThanEquals(38)

        then:
            4 == results.size()
            results.find { it.age == 38 } != null
            results.find { it.age == 37 } != null
            results.find { it.age == 36 } != null
            results.find { it.age == 35 } != null
    }

    void 'Test InRange Dynamic Finder'() {
        given:
            new Person(firstName: 'Jake', lastName: 'Brown', age: 11).save()
            new Person(firstName: 'Zack', lastName: 'Brown', age: 14).save()
            new Person(firstName: 'Jeff', lastName: 'Brown', age: 41).save()
            new Person(firstName: 'Zack', lastName: 'Galifianakis', age: 41).save()

        when:
            int count = Person.countByAgeInRange(14..41)

        then:
            3 == count

        when:
            count = Person.countByAgeInRange(41..14)

        then:
            3 == count

        when:
            count = Person.countByAgeInRange(14..<30)

        then:
            1 == count

        when:
            count = Person.countByAgeInRange(14..<42)

        then:
            3 == count

        when:
            count = Person.countByAgeInRange(15..40)

        then:
            0 == count
    }
}
