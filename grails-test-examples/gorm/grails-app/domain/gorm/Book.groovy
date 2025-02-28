package gorm

import static grails.gorm.hibernate.mapping.MappingBuilder.*

class Book {

    def testService

    String title

    static mapping = orm {
        autowire true
    }
}
