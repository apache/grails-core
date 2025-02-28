package demo

import grails.testing.web.controllers.ControllerUnitTest
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by jameskleeh on 7/3/17.
 */
class ManualControllerMockSpec extends Specification implements ControllerUnitTest {

    @Shared
    def controller

    void setup() {
        controller = mockController(DemoController)
    }

    void "test it worked"() {
        expect:
        controller instanceof DemoController
        webRequest.controllerName == "demo"
        webRequest.request.getAttribute(GrailsApplicationAttributes.CONTROLLER) == controller
    }
}
