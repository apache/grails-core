package grails.gorm.tests

import grails.validation.ValidationException
import spock.lang.IgnoreRest

/**
 * @author graemerocher
 */
class CrudOperationsSpec extends GormDatastoreSpec {

    void "Test get using a string-based key"() {
        given:

            def t = new TestEntity(name:"Bob", child:new ChildEntity(name:"Child"))
            t.save(flush:true)

        when:
            t = TestEntity.get("${t.id}")

        then:
            t != null
    }

    void "Test get returns null of non-existent entity"() {
        given:
            def t
        when:
            t = TestEntity.get(1)
        then:
            t == null
    }

    @IgnoreRest
    void "Test basic CRUD operations"() {
        given:

            def t = new TestEntity(name:"Bob", child:new ChildEntity(name:"Child"))
            t.save(flush:true)

        when:
            def results = TestEntity.list()
            t = TestEntity.get(t.id)

        then:
            t != null
            t.id != null
            "Bob" == t.name
            1 == results.size()
            "Bob" == results[0].name
    }

    void "Test save method that takes a map"() {

        given:
            def t = new TestEntity(name:"Bob", child:new ChildEntity(name:"Child"))
            t.save(param:"one", flush: true)
        when:
            t = TestEntity.get(t.id)
        then:
            t.id != null
    }

    void "Test failOnError"() {
        given:
            def t = new TestEntity(child: new ChildEntity(name:"Child"))

        when:
            t.save(failOnError: true)

        then:
            thrown ValidationException
            t.id == null
    }
}
