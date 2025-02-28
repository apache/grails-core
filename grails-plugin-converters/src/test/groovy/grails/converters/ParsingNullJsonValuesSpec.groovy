package grails.converters

import spock.lang.Issue
import spock.lang.Specification

class ParsingNullJsonValuesSpec extends Specification {

    @Issue('grails/grails-core#9129')
    def "test parsing null value"() {
        expect:
        JSON.parse("{'myList':null}").myList == null
        JSON.parse("{'myList':null}").get('myList') == null
    }

    @Issue('grails/grails-core#9129')
    def "test the remove method returns null, not JSONObject.Null"() {
        when:
        def obj = JSON.parse("{'myList':null}")

        then:
        obj.remove('myList') == null
    }
}
