package grails.plugin.json.view

import grails.plugin.json.view.test.JsonViewTest
import spock.lang.Specification

class JsonViewTestHttpSpec extends Specification implements JsonViewTest {

    void "Test that it is possible to specify HTTP headers"() {

        when:"A template is rendered with HTTP headers"
        def template ='''
json {
    userAgent request.getHeader('User-Agent')
    lang params.lang
}
'''
        def result = render(template) {
            header "User-Agent", "FooBar"
            params lang: 'de'
        }

        then:"The result is correct"
        result.json.userAgent == 'FooBar'
        result.json.lang == 'de'
    }

    void "Test that it is possible to specify HTTP attributes"() {
        when:"A template is rendered with HTTP attributes"
        def template ='''
    json {
        foo request.getAttribute('foo')
    }
    '''
        def result = render(template) {
            attribute "foo", [a: 1, b: 2]
        }

        then:"The result is correct"
        result.json.foo.a == 1
        result.json.foo.b == 2
    }
}
