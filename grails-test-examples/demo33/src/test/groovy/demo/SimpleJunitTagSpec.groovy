package demo

import grails.testing.web.taglib.TagLibUnitTest
import org.junit.Test
import spock.lang.Specification

class SimpleJunitTagSpec extends Specification implements TagLibUnitTest<SampleTagLib> {

    void testSomethingSimple() {
        expect:
        applyTemplate('<demo:helloWorld/>') == 'Hello, World!'
    }
}
