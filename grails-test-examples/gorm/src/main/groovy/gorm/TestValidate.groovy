package gorm

import grails.validation.Validateable

class TestValidate implements Validateable {
    String name

    static constraints = {
        name shared: 'nonEmpty'
    }
}
