package demo

import grails.testing.web.interceptor.InterceptorUnitTest
import spock.lang.Specification

class TestInterceptorSpec extends Specification implements InterceptorUnitTest<TestInterceptor> {

    // tag::with_request[]
    void "Test test interceptor matching"() {
        when:
        withRequest(controller: "test")

        then:
        interceptor.doesMatch()

        when:
        withRequest(controller: "person")

        then:
        !interceptor.doesMatch()
    }
    // end::with_request[]

    // tag::with_interceptors[]
    void "Test controller execution with interceptors"() {
        given:
        def controller = (TestController)mockController(TestController)

        when:
        withInterceptors([controller: "test"]) {
            controller.renderAttribute()
        }

        then:
        response.text == "Foo is Bar"
    }
    // end::with_interceptors[]
}
