// tag::basic_declaration[]
package demo

import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class DemoControllerSpec extends Specification implements ControllerUnitTest<DemoController> {

    // ...

// end::basic_declaration[]

// tag::test_render[]
    void "test action which renders text"() {
        when:
        controller.hello()               // <1>

        then:
        status == 200                    // <2>
        response.text == 'Hello, World!' // <3>
    }

// end::test_render[]
    void 'test invalid request method'() {
        when:
        request.method = 'POST'
        controller.clearDatabase()

        then:
        status == 405
    }

    void 'test valid request method'() {
        when:
        request.method = 'DELETE'
        controller.clearDatabase()

        then:
        status == 200
        response.text == 'Success'
    }

    void 'test private methods can be executed'() {
        expect:
        controller.privateMethod() == 'From Private'
    }

    void 'test protected methods can be executed'() {
        expect:
        controller.protectedMethod() == 'From Protected'
    }
// tag::basic_declaration[]
}
// end::basic_declaration[]

