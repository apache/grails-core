package example

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

@Integration
@Rollback
class TestServiceSpec extends Specification {

    TestService testService
    TestBean testBean

    @Autowired
    List<BookService> bookServiceList

    void "test data-service is loaded correctly"() {
        when:
        testService.testDataService()

        then:
        noExceptionThrown()
    }

    void "test autowire by type"() {

        expect:
        testBean.bookRepo != null
    }

    void "test autowire by name works"() {

        expect:
        testBean.bookService != null
    }

    void "test that there is only one bookService"() {
        expect:
        bookServiceList.size() == 1
    }
}
