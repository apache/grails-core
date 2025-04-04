package grails.gorm.tck

import grails.gorm.annotation.Entity

@Entity
class Card implements Serializable {

    Long id
    String cardNumber
    static hasOne = [cardProfile: CardProfile]
}