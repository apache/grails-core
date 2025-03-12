package grails.gorm.tck


import grails.gorm.tests.GormDatastoreSpec

class WhereLazySpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Product]
    }

    void createProducts() {
        new Product(name: 'tshirt', color: 'red').save(flush: true)
        new Product(name: 'tshirt', color: 'orange').save(flush: true)
        new Product(name: 'tshirt', color: 'yellow').save(flush: true)
        new Product(name: 'tshirt', color: 'orange').save(flush: true)
        new Product(name: 'tshirt', color: 'blue').save(flush: true)
    }

    void "test deleteAll with whereLazy"() {
        setup:
        createProducts()

        when:
        Product.removeAllByColor("orange")

        then:
        Product.count() == 3

        cleanup:
        Product.deleteAll()

    }

    void "test updateAll with whereLazy"() {
        setup:
        createProducts()

        when:
        Product.updateAll("orange")

        then:
        Product.countByName('tshirt') == 3
        Product.countByName('t-shirt orange') == 2

        cleanup:
        Product.deleteAll()
    }

}
