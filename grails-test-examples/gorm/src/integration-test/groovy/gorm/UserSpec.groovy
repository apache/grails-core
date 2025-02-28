package gorm

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.*

@Integration(applicationClass = Application)
@Rollback
class UserSpec extends Specification {

    @Autowired
    UserService userService

    void "Test where query over association id works"() {
        when:"An association is queries with a where query"
            City c = City.findByName("London")
            def results = userService.bycity(c.id)


        then:"THe results are correct"
            User.count() == 3
            results.size() == 2
    }
}
