package ${packageName}

import geb.spock.GebSpec
import grails.testing.mixin.integration.Integration

/**
 * See https://grails.apache.org/docs/latest/guide/testing.html#functionalTesting and https://groovy.apache.org/geb/manual/current/
 * for more instructions on how to write functional tests with Grails and Geb.
 */
@Integration
class ${className}Spec extends GebSpec {

    void 'should display the correct title on the home page'() {
        when: 'visiting the home page'
            go('/')

        then: 'the page title is correct'
            title == 'Welcome to Grails'
    }
}
