// tag::basic_declaration[]
package demo

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class ReportingServiceSpec extends Specification implements ServiceUnitTest<ReportingService> {

    Closure doWithSpring() {{ ->
        someHelper RushHelper
    }}

    void "test dependency injection"() {
        expect:
        service.retrieveSomeNumber() == 2112
    }
}

class RushHelper implements MathHelper {

    @Override
    int getSomeNumber() {
        2112
    }
}
// end::basic_declaration[]
