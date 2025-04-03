package org.grails.plugins.web.interceptors

import grails.artefact.Interceptor
import grails.util.Environment
import grails.web.mapping.UrlMappingInfo
import spock.lang.Issue
import spock.lang.Specification

class UrlMappingMatcherSpec extends Specification {

    @Issue('https://github.com/apache/grails-core/issues/9179')
    void 'test a matcher with a uri does not match all requests'() {
        given:
        def matcher = new UrlMappingMatcher(Mock(Interceptor))
        def mappingInfo = Mock(UrlMappingInfo)

        when:
        matcher.matches(uri: '/test/**')

        then:
        !matcher.doesMatch('/demo/index', mappingInfo)
    }

    @Issue("https://github.com/apache/grails-core/issues/9208")
    void "test caching of results in production"() {
        given:
        System.setProperty(Environment.KEY, "prod")
        String controller = "foo"
        String url = "/foo/test"
        def info = Mock(UrlMappingInfo)
        info.getControllerName() >> controller

        when:
        def matcher = new UrlMappingMatcher(Mock(Interceptor))
        matcher.matches(controller: controller)


        then:
        matcher.doesMatch(url, info)


        when:
        matcher = new UrlMappingMatcher(Mock(Interceptor))
        matcher.matches(controller: 'bar')

        then:
        !matcher.doesMatch(url, info)

        cleanup:
        System.setProperty(Environment.KEY, "test")
    }
}
