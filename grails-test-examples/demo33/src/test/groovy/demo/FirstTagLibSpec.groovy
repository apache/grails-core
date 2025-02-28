// tag::basic_declaration[]
package demo

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class FirstTagLibSpec extends Specification implements TagLibUnitTest<FirstTagLib> {

    void setupSpec() {
        mockTagLib SecondTagLib
    }

    void "test invoking a tag which invokes a tag in another taglib"() {
        expect:
        tagLib.sayHello() == 'BEFORE Hello From SecondTagLib AFTER'
    }
}
// end::basic_declaration[]
