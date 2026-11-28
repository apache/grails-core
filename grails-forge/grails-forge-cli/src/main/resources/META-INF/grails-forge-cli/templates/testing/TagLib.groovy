@artifact.package@
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class @artifact.name@TagLibSpec extends Specification implements TagLibUnitTest<@artifact.name@TagLib> {

     void "test simple tag as method"() {
       expect:
       tagLib.simple()
     }
}
