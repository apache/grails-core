package grails.mongodb.cascade

import grails.persistence.Entity


@Entity
class Product {

    String name
    ProductLine productLine

    static mapping = {
        productLine(cascade: "none")
    }
}
