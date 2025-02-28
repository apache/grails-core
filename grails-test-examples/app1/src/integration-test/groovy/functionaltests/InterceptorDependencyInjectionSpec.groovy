package functionaltests

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * @author graemerocher
 */
@Integration(applicationClass = Application)
class InterceptorDependencyInjectionSpec extends ContainerGebSpec {

    void "Test that interceptors are subjected to autowire by name"() {
        when:
        go '/misc/actionWhichReturnsMap'

        then:
        $('h2', text: 'The Magic Number Is 42')
    }
}
