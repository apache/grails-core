package grails.gorm.tck

import grails.gorm.tests.GormDatastoreSpec
import spock.lang.IgnoreIf

class NullValueEqualSpec extends GormDatastoreSpec {

  void "test null value in equal"() {
    when:
    new TestEntity(name:"Fred", age: null).save(failOnError: true)
    new TestEntity(name:"Bob", age: 11).save(failOnError: true)
    new TestEntity(name:"Jack", age: null).save(flush:true, failOnError: true)

    then:
    TestEntity.countByAge(11) == 1
    TestEntity.findAllByAge(null).size() == 2
    TestEntity.countByAge(null) == 2
  }

  @IgnoreIf({ System.getProperty('hibernate5.gorm.suite') })
  void "test null value in not equal"() {
    when:
    new TestEntity(name:"Fred", age: null).save(failOnError: true)
    new TestEntity(name:"Bob", age: 11).save(failOnError: true)
    new TestEntity(name:"Jack", age: null).save(flush:true, failOnError: true)

    then:
    TestEntity.countByAgeNotEqual(11) == 2
    TestEntity.countByAgeNotEqual(null) == 1
  }
}
