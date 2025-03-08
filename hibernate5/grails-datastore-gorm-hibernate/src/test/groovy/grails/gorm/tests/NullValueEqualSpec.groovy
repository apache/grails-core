package grails.gorm.tests

class NullValueEqualSpec extends GormDatastoreSpec {

    void "test null value in equal and not equal"() {
        when:
        new TestEntity(name:"Fred", age: null).save(failOnError: true)
        new TestEntity(name:"Bob", age: 11).save(failOnError: true)
        new TestEntity(name:"Jack", age: null).save(flush:true, failOnError: true)

        then:
        TestEntity.countByAge(11) == 1
        TestEntity.findAllByAge(null).size() == 2
        TestEntity.countByAge(null) == 2
//      TODO: these 2 cases do not work for Hibernate
//        TestEntity.countByAgeNotEqual(11) == 2
//        TestEntity.countByAgeNotEqual(null) == 1
    }
}
