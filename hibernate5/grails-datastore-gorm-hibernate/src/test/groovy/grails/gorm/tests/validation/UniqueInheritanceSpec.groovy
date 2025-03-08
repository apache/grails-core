package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Rollback
class UniqueInheritanceSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Item, ConcreteProduct, Product, Book)

    void "unique constraint works directly"() {
        setup:
        Product i = new ConcreteProduct(name: '123')
        i.save(flush: true)

        expect:
        !i.hasErrors()

        when:
        i.save()

        then:
        !i.hasErrors()
    }

    void "unique constraint works on cascade"() {
        setup:
        Item i = new Item(product: new ConcreteProduct(name: '123'))
        i.save(flush: true)

        expect:
        !i.hasErrors()

        when:
        i.save()

        then:
        !i.hasErrors() // item.product.name is not unique
    }

    @Issue('https://github.com/grails/gorm-hibernate5/issues/32')
    void "test save multiple book instances with unique constraint applied"() {
        when:
        def book1=new Book(title: "one")
        book1.save()
        def book2=new Book(title: "one")
        book2.save()

        then:
        book2.hasErrors()
    }
}

@Entity
class Book {
    String title
    static constraints = {
        title (nullable: false,size: 0..200, unique: true, blank:false)
    }
}

@Entity
class Item {
    Product product
}

@Entity
class ConcreteProduct extends Product {

}

@Entity
abstract class Product {
    String name

    static constraints = {
        name unique: true
    }
}
