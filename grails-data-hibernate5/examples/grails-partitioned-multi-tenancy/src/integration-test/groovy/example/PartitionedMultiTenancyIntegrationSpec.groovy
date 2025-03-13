package example

import datasources.Application
import grails.core.GrailsApplication
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.util.GrailsWebMockUtil
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.web.SessionTenantResolver
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

@Integration(applicationClass = Application)
@Slf4j
@Rollback
class PartitionedMultiTenancyIntegrationSpec extends Specification {
    BookService bookService
    AnotherBookService anotherBookService
    GrailsWebRequest webRequest
    GrailsApplication grailsApplication

    def setup() {
        //To register MimeTypes
        if (grailsApplication.mainContext.parent) {
            grailsApplication.mainContext.getBean("mimeTypesHolder")
        }
        webRequest = GrailsWebMockUtil.bindMockWebRequest()
    }

    def cleanup() {
        RequestContextHolder.setRequestAttributes(null)
    }

    void "test saveBook with data service"() {
        given:
        webRequest.session.setAttribute(SessionTenantResolver.ATTRIBUTE, "moreBooks")

        when:
        Book book = bookService.saveBook("Book-Test-${System.currentTimeMillis()}")
        println book
        log.info("${book}")


        then:
        bookService.countBooks() == 1
        book?.id
    }

    void "test saveBook with normal service"() {
        given:
        webRequest.session.setAttribute(SessionTenantResolver.ATTRIBUTE, "moreBooks")

        when:
        Book book = anotherBookService.saveBook("Book-Test-${System.currentTimeMillis()}")
        println book
        log.info("${book}")


        then:
        anotherBookService.countBooks() == 1
        book?.id
    }

    void 'Test database per tenant'() {
        when:"When there is no tenant"
        Book.count()

        then:"You still get an exception"
        thrown(TenantNotFoundException)

        when:"But look you can add a new Schema at runtime!"
        webRequest.session.setAttribute(SessionTenantResolver.ATTRIBUTE, "moreBooks")


        then:
        anotherBookService.countBooks() == 0
        bookService.countBooks()== 0

        when:"And the new @CurrentTenant transformation deals with the details for you!"
        anotherBookService.saveBook("The Stand")
        anotherBookService.saveBook("The Shining")
        anotherBookService.saveBook("It")

        then:
        anotherBookService.countBooks() == 3
        bookService.countBooks()== 3

        when:"Swapping to another schema and we get the right results!"
        webRequest.session.setAttribute(SessionTenantResolver.ATTRIBUTE, "evenMoreBooks")

        anotherBookService.saveBook("Along Came a Spider")
        bookService.saveBook("Whatever")
        then:
        anotherBookService.countBooks() == 2
        bookService.countBooks()== 2
    }
}
