package other.pkg

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class BazTagLibSpec extends Specification implements TagLibUnitTest<BazTagLib> {

     void "test simple tag as method"() {
       expect:
       tagLib.simple()
     }
}
