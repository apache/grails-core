package example.grails

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
@Rollback
class FooBarFlowSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        expect: "fix me"
        true == false
    }
}
