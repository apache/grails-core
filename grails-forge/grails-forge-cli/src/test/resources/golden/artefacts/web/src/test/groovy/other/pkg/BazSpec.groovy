package other.pkg

import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class BazSpec extends Specification implements GrailsUnitTest {

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        expect: "fix me"
        true == false
    }
}
