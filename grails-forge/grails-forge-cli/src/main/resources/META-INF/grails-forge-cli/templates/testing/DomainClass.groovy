@artifact.package@
import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class @artifact.name@Spec extends Specification implements DomainUnitTest<@artifact.name@> {

     void "test domain constraints"() {
        when:
        @artifact.name@ domain = new @artifact.name@()
        //TODO: Set domain props here

        then:
        domain.validate()
     }
}
