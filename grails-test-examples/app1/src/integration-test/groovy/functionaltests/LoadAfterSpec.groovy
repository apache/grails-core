package functionaltests


import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration
class LoadAfterSpec extends ContainerGebSpec {

    void "Test override one plugin view from another plugin"() {
        when:
        go '/login/auth'

        then:
        title == "My Plugin Login Auth"
    }
}
