package grails.gorm.services.multitenancy.database


import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 05/04/2017.
 */

class DatabasePerTenantSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
            DatastoreUtils.createPropertyResolver([(Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DATABASE,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver(),
             (Settings.SETTING_DB_CREATE)            : "create-drop"]),
            [ConnectionSource.DEFAULT, "foo", "bar"],
            Book
    )
    @Shared IBookService bookDataService = datastore.getService(IBookService)

    //Ignore test on Github Workflow because of inconsistent behavior
    @IgnoreIf({ System.getenv("GITHUB_WORKFLOW")})
    void 'Test database per tenant'() {
        when:"When there is no tenant"
        Book.count()

        then:"You still get an exception"
        thrown(TenantNotFoundException)

        when:"But look you can add a new Schema at runtime!"

        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "foo")

        BookService bookService = new BookService()

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
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "bar")
        bookService.saveBook("Along Came a Spider")
        bookDataService.saveBook("Whatever")
        then:
        bookService.countBooks() == 2
        bookDataService.countBooks()== 2
    }
}





