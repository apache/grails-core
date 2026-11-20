package example.grails

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class FooBarTagLibSpec extends Specification implements TagLibUnitTest<FooBarTagLib> {

     void "test simple tag as method"() {
       expect:
       tagLib.simple()
     }
}
