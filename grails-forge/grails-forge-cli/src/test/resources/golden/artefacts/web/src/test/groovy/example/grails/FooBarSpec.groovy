package example.grails

import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class FooBarSpec extends Specification implements GrailsUnitTest {

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        expect: "fix me"
        true == false
    }
}
