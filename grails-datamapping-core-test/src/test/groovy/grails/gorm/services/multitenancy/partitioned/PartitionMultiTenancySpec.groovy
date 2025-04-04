package grails.gorm.services.multitenancy.partitioned

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.services.Service
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PartitionMultiTenancySpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
            [(Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver(),
             (Settings.SETTING_DB_CREATE): "create-drop"],
            getClass().getPackage()
    )
    @Shared IBookService bookDataService = datastore.getService(IBookService)

    void 'Test partitioned multi-tenancy with GORM services'() {
        setup:
        BookService bookService = new BookService()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")

        when: "When there is no tenant"
        Book.count()

        then:"You still get an exception"
        thrown(TenantNotFoundException)

        when:"But look you can change tenant"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "12")

        then:
        bookService.countBooks() == 0
        bookDataService.countBooks() == 0

        when:"And the new @CurrentTenant transformation deals with the details for you!"
        bookService.saveBook("The Stand")
        bookService.saveBook("The Shining")

        then:
        bookService.countBooks() == 2
        bookDataService.countBooks()== 2
        bookService.findBooks("The Shining")[0].title == "The Shining"

        when:"Swapping to another schema and we get the right results!"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "13")

        then:
        bookService.countBooks() == 0
        bookDataService.countBooks() == 0

        when: "calling a method save using Tenants.withoutId"
        Book book1 = new Book(title: "The Secret", tenantId: 55)
        Book book2 = new Book(title: "The Secret - 2", tenantId: 55)

        bookDataService.saveBook(book1)
        bookDataService.saveBook(book2)

        and: "Swapping to another schema and we get the right results!"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "55")

        then: "two books are created with tenantId 55"
        bookService.countBooks() == 2
        bookDataService.countBooks() == 2


        when: "book is saved without tenantId"
        Book book3 = bookDataService.saveBook(
                new Book(title: "The Road Trip")
        )

        then: "new book is saved without tenantId"
        book3.id && !book3.tenantId


    }
}

@Entity
class Book implements MultiTenant<Book> {
    Long tenantId
    String title
}

@CurrentTenant
@Transactional
class BookService {

    @WithoutTenant
    void saveBook(Book book) {
        book.save()
    }

    void saveBook(String title) {
        new Book(title: title).save()
    }

    @ReadOnly
    int countBooks() {
        Book.count()
    }

    @ReadOnly
    List<Book> findBooks(String title) {
        (List<Book>) Book.withCriteria {
            eq('title', title)
        }
    }

}

@CurrentTenant
@Service(Book)
interface IBookService {

    Book saveBook(String title)

    @WithoutTenant
    Book saveBook(Book book)

    Integer countBooks()
}