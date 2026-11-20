@artifact.package@
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class @artifact.name@ControllerSpec extends Specification implements ControllerUnitTest<@artifact.name@Controller> {

     void "test index action"() {
        when:
        controller.index()

        then:
        status == 200

     }
}
