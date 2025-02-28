package functionaltests

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue

@Integration(applicationClass = Application)
class MiscFunctionalSpec extends ContainerGebSpec {

    @Issue('9133')
    void "Test that bean override configuration works"() {
        when:
            go '/misc/beanPropertyOverrideTest'

        then:
            driver.pageSource.contains 'Brian'

    }

    @Issue('GRAILS-12028')
    void "Test that when an action returns null the view is rendered by convention"() {
        when:
            go '/misc/actionWhichReturnsNull'

        then:
        	title == 'Action Which Returns Null GSP'
    }

    void "Test that placeholder configuration works for the config object"() {
    	when:
    		go '/misc/placeHolderConfig'

    	then:
    		driver.pageSource.contains '[test test test]'
    }
}
