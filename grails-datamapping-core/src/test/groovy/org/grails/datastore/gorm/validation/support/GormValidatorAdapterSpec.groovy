package org.grails.datastore.gorm.validation.support

import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.validation.jakarta.GormValidatorAdapter
import spock.lang.Specification

import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.Digits

/**
 * Created by graemerocher on 30/12/2016.
 */
class GormValidatorAdapterSpec extends Specification {


    void "test propagate jakarta.valdiation errors to gorm object"() {

        given:
        def factory = Validation.byDefaultProvider().configure().buildValidatorFactory()

        Validator v = factory.getValidator()
        def adapter = new GormValidatorAdapter(v)


        when:
        def product = new Product(price: "foo")
        adapter.validate(product)

        then:
        adapter.forExecutables()
        product.errors.hasErrors()
        product.errors.getFieldError('price')
    }
}

class Product implements GormValidateable {
    @Digits(integer = 6, fraction = 2)
    String price
}
