package grails.mongodb.cascade

import grails.gorm.tests.GormDatastoreSpec

class MongoCascadeSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Product, ProductLine]
    }

    void "test association is not cascaded on update or insert"() {
        given:
        ProductLine x = new ProductLine(name: "x")
        x.save()
        session.flush()
        session.clear()

        Product product = new Product(name: "my product", productLine: ProductLine.load(x.id))
        product.save()
        session.flush()
        session.clear()

        when:
        product = Product.get(product.id)

        then:
        product.productLine.name == "x"

        when: //no cascading on update
        product.productLine.name = "xy"
        product.save()
        session.flush()
        session.clear()
        x = ProductLine.get(x.id)

        then:
        x.name == "x"

        when:
        x.name = "xy"
        product = new Product(name: "other product", productLine: x)
        product.save()
        session.flush()
        session.clear()

        then:
        ProductLine.get(x.id).name == "x"
    }
}
