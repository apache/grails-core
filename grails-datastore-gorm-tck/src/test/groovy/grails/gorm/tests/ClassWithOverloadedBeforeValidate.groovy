package grails.gorm.tests

import grails.persistence.Entity

@Entity
class ClassWithOverloadedBeforeValidate implements Serializable {
    Long id
    Long version
    def noArgCounter = 0
    def listArgCounter = 0
    def propertiesPassedToBeforeValidate
    String name
    def beforeValidate() {
        ++noArgCounter
    }
    def beforeValidate(List properties) {
        ++listArgCounter
        propertiesPassedToBeforeValidate = properties
    }

    static constraints = {
        name blank: false
    }
}