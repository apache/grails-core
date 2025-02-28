package demo

import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

import jakarta.servlet.http.HttpServletResponse

class SubControllerSpec extends Specification implements ControllerUnitTest<SubController> {
    void 'test calling super method'() {
        when:
        controller.method1()

        then:
        status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 1'
    }
}
