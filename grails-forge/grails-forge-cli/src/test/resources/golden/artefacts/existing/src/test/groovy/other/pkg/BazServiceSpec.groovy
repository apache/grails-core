package other.pkg

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class BazServiceSpec extends Specification implements ServiceUnitTest<BazService> {

     void "test something"() {
        expect:
        service.doSomething()
     }
}
