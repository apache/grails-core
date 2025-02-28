package demo

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class DefineBeansSpec extends Specification implements ServiceUnitTest<ReportingService> {

    void "test dependency injection with defineBeans"() {
        given:
        defineBeans {
            someHelper RushHelper
        }

        expect:
        service.retrieveSomeNumber() == 2112
    }

    // tag::test_declaration[]
    void "test the bean is available to the context"() {
        given:
        defineBeans {
            someInteger(Integer, 2)
        }

        expect:
        applicationContext.getBean('someInteger') == 2
    }
    // end::test_declaration[]
}
