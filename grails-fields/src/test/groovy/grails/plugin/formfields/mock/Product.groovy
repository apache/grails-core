package grails.plugin.formfields.mock

import grails.persistence.Entity

@Entity
class Product {

    String name
    Double netPrice
    Double taxRate
    Double tax

    static constraints = {
        name blank: false, unique: true
        netPrice min: 0.01d
        taxRate min: 0.0d
        tax nullable: true
    }

    static mapping = {
        tax formula: 'NET_PRICE * TAX_RATE'
    }

}
