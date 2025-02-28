package demo

import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

class DemoUrlMappingsSpec extends Specification implements UrlMappingsUnitTest<DemoUrlMappings> {

    Class[] getControllersToMock() {
        DemoController
    }

    void testUrlMapping() {
        when:
        assertUrlMapping '/sayHello', controller: 'demo', action: 'hello'

        then:
        noExceptionThrown()
    }
}
