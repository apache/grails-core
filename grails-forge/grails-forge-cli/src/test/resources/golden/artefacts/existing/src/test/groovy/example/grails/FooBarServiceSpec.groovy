package example.grails

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class FooBarServiceSpec extends Specification implements ServiceUnitTest<FooBarService> {

     void "test something"() {
        expect:
        service.doSomething()
     }
}
