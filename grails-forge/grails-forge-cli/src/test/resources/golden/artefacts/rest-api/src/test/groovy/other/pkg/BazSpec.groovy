package other.pkg

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class BazSpec extends Specification implements DomainUnitTest<Baz> {

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        expect: "fix me"
        true == false
    }
}
