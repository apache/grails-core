package demo

import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Issue
import spock.lang.Specification

class NamePropertyControllerSpec extends Specification implements ControllerUnitTest<NamePropertyController> {

    @Issue('grails/grails-core#10363')
    void "test referencing a controller with a 'name' property"() {
        when:
        controller

        then:
        notThrown ClassCastException
    }
}
