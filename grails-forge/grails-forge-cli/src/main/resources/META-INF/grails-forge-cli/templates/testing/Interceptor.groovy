@artifact.package@
import grails.testing.web.interceptor.InterceptorUnitTest
import spock.lang.Specification

class @artifact.name@InterceptorSpec extends Specification implements InterceptorUnitTest<@artifact.name@Interceptor> {

    void "test interceptor matching"() {
        when:
        withRequest(controller: "@artifact.propertyName@")

        then:
        interceptor.doesMatch()
    }
}
