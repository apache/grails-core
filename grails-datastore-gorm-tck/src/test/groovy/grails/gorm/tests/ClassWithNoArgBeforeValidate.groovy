package grails.gorm.tests

import grails.persistence.Entity;

@Entity
class ClassWithNoArgBeforeValidate implements Serializable {
    Long id
    Long version
    def noArgCounter = 0
    String name

    def beforeValidate() {
        ++noArgCounter
    }

    static constraints = {
        name blank: false
    }
}