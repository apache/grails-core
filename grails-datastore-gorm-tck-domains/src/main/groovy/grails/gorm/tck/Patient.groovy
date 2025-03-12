package grails.gorm.tck

import grails.gorm.annotation.Entity

@Entity
class Patient implements Serializable {

    ContactDetails contactDetails

    static constraints = {
        contactDetails nullable: false
    }

    static mapping = {
        contactDetails lazy: true
    }
}