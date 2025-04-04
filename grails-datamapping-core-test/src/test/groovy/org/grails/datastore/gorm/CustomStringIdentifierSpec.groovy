package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class CustomStringIdentifierSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Product, Description]
    }

    void "test basic crud operations with string id"() {
        when: "A product is saved with an assigned id"
            createProducts()
            def p = Product.get("MacBook")

        then:"The product is not null"
            p != null

        when:"A product is retrieved by id"
            session.clear()
            p = Product.get("MacBook")

        then:"The product is not null"
            p != null
    }

    void "Test dynamic finders with string id"() {
        when: "A product with a string id is query via a dynamic finder"
            createProducts()
            def p = Product.findByName("MacBook")

        then:"The product is not null"
            p != null

    }

    void "Test integer based id"() {
       when:"An object has an id that is an integer"
            def d = new Description(name:"Blah").save(flush:true)

        then:"The object is successfully saved"
            d != null

        when:"The object is queried"
            session.clear()
            d = Description.get(1)

        then:"The object is returned"
            d != null

    }

    protected def createProducts() {
        new Product(name: "MacBook").save()
        new Product(name: "iPhone").save()
        new Product(name: "iMac").save(flush: true)

    }
}

@Entity
class Description {
    Integer id
    String name
}

@Entity
class Product {
    String name
    Date dateCreated

    static mapping = {
        id generator:'assigned', name:"name"
    }
}
