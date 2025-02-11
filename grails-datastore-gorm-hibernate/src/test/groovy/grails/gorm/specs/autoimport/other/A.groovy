package grails.gorm.specs.autoimport.other

import grails.persistence.Entity

@Entity
class A {

    static mapping = {
        autoImport false
    }
}
