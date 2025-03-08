package grails.gorm.tests.autoimport.other

import grails.persistence.Entity

@Entity
class A {

    static mapping = {
        autoImport false
    }
}
