package grails.test.app

import static grails.gorm.hibernate.mapping.MappingBuilder.orm

abstract class Payment {

    BigDecimal amount

    static constraints = {
    }

    static mapping = orm {
        tablePerHierarchy(false)
    }

    static graphql = true
}
