package profiles

import spock.lang.Specification

/**
 * Sample test to verify the project structure works
 */
class SampleProfileTest extends Specification {
    
    def "should demonstrate that the project structure works"() {
        given:
        def sampleValue = "grails"
        
        when:
        def result = sampleValue.toUpperCase()
        
        then:
        result == "GRAILS"
    }
}