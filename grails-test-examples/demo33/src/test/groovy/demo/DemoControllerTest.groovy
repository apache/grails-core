package demo

import grails.testing.web.controllers.ControllerUnitTest
import org.junit.Test
import spock.lang.Specification

class DemoControllerTest extends Specification implements ControllerUnitTest<DemoController> {

    void "test action which renders text"() {
        when:
        controller.hello()

        then:
        status == 200
        response.text == 'Hello, World!'
    }


    void 'test invalid request method'() {
        when:
        request.method = 'POST'
        controller.clearDatabase()

        then:
        status == 405
    }

    void 'test valid request method'() {
        when:
        request.method = 'DELETE'
        controller.clearDatabase()

        then:
        status == 200
        response.text == 'Success'
    }
}
