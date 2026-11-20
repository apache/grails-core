package other.pkg

import grails.testing.web.interceptor.InterceptorUnitTest
import spock.lang.Specification

class BazInterceptorSpec extends Specification implements InterceptorUnitTest<BazInterceptor> {

    void "test interceptor matching"() {
        when:
        withRequest(controller: "baz")

        then:
        interceptor.doesMatch()
    }
}
