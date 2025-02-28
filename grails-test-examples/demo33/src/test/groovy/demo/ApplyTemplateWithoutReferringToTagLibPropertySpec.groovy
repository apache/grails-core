package demo

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class ApplyTemplateWithoutReferringToTagLibPropertySpec extends Specification implements TagLibUnitTest<SampleTagLib> {

    void "test tags with applyTemplate without referring to tagLib anywhere in this spec"() {
        expect:
        applyTemplate('<demo:helloWorld/>') == 'Hello, World!'
        applyTemplate('<demo:sayHello name="Adrian"/>') == 'Hello, Adrian!'
    }
}
