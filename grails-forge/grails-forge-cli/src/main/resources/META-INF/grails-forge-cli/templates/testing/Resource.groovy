@artifact.package@
import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class @artifact.name@Spec extends Specification implements DomainUnitTest<@artifact.name@> {

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        expect: "fix me"
        true == false
    }
}
