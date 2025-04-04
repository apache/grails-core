package grails.gorm.tck

import grails.gorm.annotation.Entity

@Entity
class CardProfile implements Serializable {

    Long id
    String fullName
    Card card

    static constraints = {
        card nullable: true
    }
}