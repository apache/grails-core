package example

import grails.gorm.transactions.Rollback
import grails.test.hibernate.HibernateSpec
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.testing.GrailsUnitTest

/**
 * Created by graemerocher on 06/04/2017.
 */
class SchemaPerTenantSpec extends HibernateSpec implements GrailsUnitTest {

    BookService bookDataService = hibernateDatastore.getService(BookService)

    @Override
    Map getConfiguration() {
        Collections.unmodifiableMap(
                (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver(),
                (Settings.SETTING_DB_CREATE): "create-drop"
        )
    }

    def setup() {
        //To register MimeTypes
        if (grailsApplication.mainContext.parent) {
            grailsApplication.mainContext.getBean("mimeTypesHolder")
        }
        hibernateDatastore.addTenantForSchema("moreBooks")
        hibernateDatastore.addTenantForSchema("evenMoreBooks")
    }
    def cleanup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }

    @Rollback("moreBooks")
    void "Test should rollback changes in a previous test"() {
        when:"When there is no tenant"
        Book.count()

        then:"You still get an exception"
        thrown(TenantNotFoundException)

        when:"You can save a book"

        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")
        bookDataService.saveBook("The Stand")

        then:"And the changes will be rolled back for the next test"
        bookDataService.countBooks() == 1
    }

    void 'Test database per tenant'() {
        when:"When there is no tenant"
        Book.count()

        then:"You still get an exception"
        thrown(TenantNotFoundException)

        when:"But look you can add a new Schema at runtime!"

        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")

        AnotherBookService bookService = new AnotherBookService()

        then:
        bookService.countBooks() == 0
        bookDataService.countBooks()== 0

        when:"And the new @CurrentTenant transformation deals with the details for you!"
        bookService.saveBook("The Stand")
        bookService.saveBook("The Shining")
        bookService.saveBook("It")

        then:
        bookService.countBooks() == 3
        bookDataService.countBooks()== 3

        when:"Swapping to another schema and we get the right results!"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "evenMoreBooks")
        bookService.saveBook("Along Came a Spider")
        bookDataService.saveBook("Whatever")
        then:
        bookService.countBooks() == 2
        bookDataService.countBooks()== 2
    }
}
