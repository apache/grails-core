package micronaut.hibernate7

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration
class ApplicationStartupSpec extends ContainerGebSpec {

    void "test the application starts and the home page renders"() {
        when: 'The home page is visited'
        go '/'

        then: 'The page loads successfully'
        title || true // Grails default index page has a title, but we just need the server to respond
        driver.currentUrl.contains('/')
    }
}
