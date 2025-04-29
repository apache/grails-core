package ${packageName}

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

import geb.spock.*

/**
 * See http://www.gebish.org/manual/current/ for more instructions
 */
@Integration
@Rollback
class ${className}Spec extends GebSpec {

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        when:"The home page is visited"
            go '/'

        then:"The title is correct"
        	title == "Welcome to Grails"
    }
}
