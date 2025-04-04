package grails.gorm.tck

import grails.gorm.annotation.Entity

@Entity
class TestBook implements Serializable {

    Long id
    String title
    TestAuthor author
}