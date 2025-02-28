package ds2

import static grails.gorm.hibernate.mapping.MappingBuilder.*

class Book {

    String title

    static constraints = {
    }

    static mapping = orm {
        datasource 'secondary'
    }
}
