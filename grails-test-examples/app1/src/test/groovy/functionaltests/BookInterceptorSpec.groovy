package functionaltests

import grails.testing.web.interceptor.InterceptorUnitTest
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.web.ControllerUnitTestMixin} for usage instructions
 */
class BookInterceptorSpec extends Specification implements InterceptorUnitTest<BookInterceptor> {

    void "Test book interceptor matching"() {
        when:"A request matches the interceptor"
            withRequest(controller:"book")

        then:"The interceptor does match"
            interceptor.doesMatch()
    }
}
