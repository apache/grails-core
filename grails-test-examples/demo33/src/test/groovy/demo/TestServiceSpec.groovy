package demo

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class TestServiceSpec extends Specification implements ServiceUnitTest<TestService> {

    @Override
    boolean getLocalOverride() {
        return true
    }

    @Override
    Closure doWithConfig() {{ config->
        config.demo = ["foo": "test"]
    } }

    void "when local-override is set then the service picks the update config value"() {
        expect:
        service.foo == "test"
    }
}
