@artifact.package@
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class @artifact.name@ServiceSpec extends Specification implements ServiceUnitTest<@artifact.name@Service> {

     void "test something"() {
        expect:
        service.doSomething()
     }
}
