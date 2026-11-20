package other.pkg

import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class BazControllerSpec extends Specification implements ControllerUnitTest<BazController> {

     void "test index action"() {
        when:
        controller.index()

        then:
        status == 200

     }
}
