package grails.gorm.tck

import grails.gorm.annotation.Entity

@Entity
class ContactDetails implements Serializable {

    String phoneNumber

    static constraints = {
        phoneNumber nullable: false, unique: true
    }
}