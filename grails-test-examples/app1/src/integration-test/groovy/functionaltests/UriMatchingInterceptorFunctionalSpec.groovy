package functionaltests


import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
class UriMatchingInterceptorFunctionalSpec extends ContainerGebSpec {

    void "Test that an interceptor that redirects works"() {
        when:
        go '/interceptorDemo/one'

        then:
        $().text() == 'interceptor did match for the one action'

        when:
        go '/interceptorDemo/two'

        then:
        $().text() == 'interceptor did not match for the two action'
    }
}
