package example.grails

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class FooBarSpec extends Specification implements DomainUnitTest<FooBar> {

     void "test domain constraints"() {
        when:
        FooBar domain = new FooBar()
        //TODO: Set domain props here

        then:
        domain.validate()
     }
}
