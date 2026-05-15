package ${packageName}

import geb.spock.GebSpec
import grails.testing.mixin.integration.Integration

/**
 * See https://groovy.apache.org/geb/manual/current/ for more instructions
 */
@Integration
class ${className}Spec extends GebSpec {

    void 'home page loads'() {
        when: 'The home page is visited'
            go('/')

        then: 'The title is correct'
            waitFor { title == 'Welcome to Grails' }
    }
}
