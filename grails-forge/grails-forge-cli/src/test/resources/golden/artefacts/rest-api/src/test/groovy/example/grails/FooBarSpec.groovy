package example.grails

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class FooBarSpec extends Specification implements DomainUnitTest<FooBar> {

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        expect: "fix me"
        true == false
    }
}
