package grails.gorm.specs

import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity
import spock.lang.IgnoreIf

class NullValueEqualSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([TestEntity])
    }

    void "test null value in equal"() {
        when:
        new TestEntity(name: "Fred", age: null).save(failOnError: true)
        new TestEntity(name: "Bob", age: 11).save(failOnError: true)
        new TestEntity(name: "Jack", age: null).save(flush: true, failOnError: true)

        then:
        TestEntity.countByAge(11) == 1
        TestEntity.findAllByAge(null).size() == 2
        TestEntity.countByAge(null) == 2
    }

    void "test null value in not equal"() {
        when:
        new TestEntity(name: "Fred", age: null).save(failOnError: true)
        new TestEntity(name: "Bob", age: 11).save(failOnError: true)
        new TestEntity(name: "Jack", age: null).save(flush: true, failOnError: true)
        def count = TestEntity.countByAgeNotEqual(11)

        then:
        TestEntity.list().size() == 3
        TestEntity.countByAgeNotEqual(null) == 1
        TestEntity.countByAgeNotEqual(11) == 2
    }
}
