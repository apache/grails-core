package demo

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
class DependencyInjectionSpec extends Specification {

    HelperService helperService

    def setup() {
        assert helperService != null
    }

    void 'some test method'() {
        expect:
        helperService != null
    }
}
