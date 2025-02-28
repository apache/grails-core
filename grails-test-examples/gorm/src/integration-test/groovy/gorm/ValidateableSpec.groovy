package gorm

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration(applicationClass = Application)
class ValidateableSpec extends Specification {

    void "test validateable objects have access to default constraints"() {
        expect:
        new TestValidate(name: 'foo').validate()
        !new TestValidate().validate()
    }

}
