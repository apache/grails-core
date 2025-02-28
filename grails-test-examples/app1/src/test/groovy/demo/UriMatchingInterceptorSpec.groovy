package demo

import grails.testing.web.interceptor.InterceptorUnitTest
import spock.lang.Specification

class UriMatchingInterceptorSpec extends Specification implements InterceptorUnitTest<UriMatchingInterceptor> {

    void "Test uri matching interceptor"() {
        when:
        withRequest(uri: '/interceptorDemo/one')

        then:
        interceptor.doesMatch()

        when:
        withRequest(uri: '/interceptorDemo/two')

        then:
        !interceptor.doesMatch()
    }
}
