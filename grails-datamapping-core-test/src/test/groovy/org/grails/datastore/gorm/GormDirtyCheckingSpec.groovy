package org.grails.datastore.gorm

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import spock.lang.Issue

class GormDirtyCheckingSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Student, BooleanTest]
    }

    void "test a new instance is dirty by default"() {

        when:
        Student student = new Student(name: "JD")

        then:
        student.isDirty()
    }

    @Issue('https://github.com/grails/grails-core/issues/12453')
    void "test Boolean property getters"() {

        when:
        BooleanTest student = new BooleanTest(property1: true, property2: true)

        then: "Same behaviour of getters for boolean and Boolean"
        student.isProperty1()
        student.getProperty1()
        student.isProperty2()
        student.getProperty2()
    }

}

@Entity
class Student {
    String name
}

@Entity
class BooleanTest {
    Boolean property1
    boolean property2
}

