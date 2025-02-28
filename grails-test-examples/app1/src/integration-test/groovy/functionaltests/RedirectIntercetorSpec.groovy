package functionaltests

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * @author graemerocher
 */
@Integration(applicationClass = Application)
class RedirectInterceptorSpec extends ContainerGebSpec {

    void "Test that an interceptor that redirects works"() {
        when:
        go '/misc/interceptedByInterceptor'

        then:
        driver.pageSource.contains 'Test' // the redirect worked
    }
}
