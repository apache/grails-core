package gorm

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.*

@Integration(applicationClass = Application)
@Rollback
class BookSpec extends Specification {


    @Autowired
    TestService testService

    void "Test dynamic finders work"() {
        expect:"Dynamic finders to work"
            testService.testDynamicFinders() // test when called from service
            Book.count() == 1
            Book.findByTitle("The Stand")
            Book.where {
                title == "The Stand"
            }.count() == 1            
            Book.countByTitle("The Stand") == 1
            Book.countByTitle("Something Else") == 0
            Book.findAllByTitle("The Stand").size() == 1

    }

}
