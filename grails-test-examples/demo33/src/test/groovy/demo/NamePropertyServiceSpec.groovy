package demo

import grails.testing.services.ServiceUnitTest
import spock.lang.Issue
import spock.lang.Specification

class NamePropertyServiceSpec extends Specification implements ServiceUnitTest<NamePropertyService> {

    @Issue('grails/grails-core#10363')
    void "test referencing a service with a 'name' property"() {
        when:
        service

        then:
        notThrown ClassCastException
    }
}
