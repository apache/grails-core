package org.grails.datastore.gorm.mongo


import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec

import jakarta.validation.constraints.Digits

/**
 * Created by graemerocher on 30/12/2016.
 */
class JakartaValidationSpec extends GormDatastoreSpec {

    void "test jakarta.validator validation"() {
        when:"An invalid entity is created"
        JakartaProduct p = new JakartaProduct(name:"MacBook", price: "bad")
        p.save()

        then:"The are errors"
        p.hasErrors()
        p.errors.getFieldError('price')
    }

    @Override
    List getDomainClasses() {
        [JakartaProduct]
    }
}

@Entity
class JakartaProduct {
    @Digits(integer = 6, fraction = 2)
    String price
    String name
}