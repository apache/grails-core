package issue11005

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
class DummyDependencyIntegrationSpec extends Specification {

    DummyDependency dummyDependency

    void 'test dependency defined in resources.groovy is injected'() {
        expect:
        dummyDependency != null
    }
}
