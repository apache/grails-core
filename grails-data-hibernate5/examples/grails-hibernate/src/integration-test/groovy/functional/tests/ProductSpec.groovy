package functional.tests

import another.Item
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification

/**
 * Created by graemerocher on 02/01/2017.
 */
@Integration(applicationClass = Application)
class ProductSpec extends Specification {

    @Rollback
    void "test that JPA entities can be treated as GORM entities"() {
        when:"A basic entity is persisted and validated"
        Product product = new Product(price: "6000.01", name: "iMac")
        product.save(flush:true, validate:false)

        def query = Product.where {
            name == 'Mac Pro'
        }
        then:"The object was saved"
        !product.errors.hasErrors()
        Product.count() == 2
        query.count() == 0
    }

    @Rollback
    void "test entity in different package to application"() {
        expect:
        Item.count() == 0
    }

    @Rollback
    void "test that JPA entities can use jakarta.validation"() {
        when:"A basic entity is persisted and validated"
        Product c = new Product(price: "Bad", name: "iMac")
        c.save(flush:true)

        def query = Product.where {
            name == 'iMac'
        }
        then:"The object was saved"
        c.errors.hasErrors()
        Product.count() == 1
        query.count() == 0
    }
}
