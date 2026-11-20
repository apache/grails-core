package other.pkg

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class BazSpec extends Specification implements DomainUnitTest<Baz> {

     void "test domain constraints"() {
        when:
        Baz domain = new Baz()
        //TODO: Set domain props here

        then:
        domain.validate()
     }
}
