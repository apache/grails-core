package grails.gorm.tests.txs

import grails.gorm.tests.services.Attribute
import grails.gorm.tests.services.Product
import grails.gorm.transactions.Transactional
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.annotation.Isolation
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 16/06/2017.
 */
class CustomIsolationLevelSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore hibernateDatastore = new HibernateDatastore(Product, Attribute)


    @Issue('https://github.com/grails/grails-data-mapping/issues/952')
    void "test custom isolation level"() {
        expect:
        new ProductService().listProducts().size() == 0
    }


}

class ProductService {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    List<Product> listProducts() {
        Product.list()
    }
}
