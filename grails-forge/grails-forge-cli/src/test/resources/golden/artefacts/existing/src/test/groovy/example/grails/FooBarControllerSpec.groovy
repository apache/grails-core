package example.grails

import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class FooBarControllerSpec extends Specification implements ControllerUnitTest<FooBarController> {

     void "test index action"() {
        when:
        controller.index()

        then:
        status == 200

     }
}
