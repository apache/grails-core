package example.grails

import grails.testing.web.interceptor.InterceptorUnitTest
import spock.lang.Specification

class FooBarInterceptorSpec extends Specification implements InterceptorUnitTest<FooBarInterceptor> {

    void "test interceptor matching"() {
        when:
        withRequest(controller: "fooBar")

        then:
        interceptor.doesMatch()
    }
}
