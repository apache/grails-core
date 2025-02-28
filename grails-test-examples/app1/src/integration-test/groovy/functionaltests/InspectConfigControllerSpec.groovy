package functionaltests

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue

@Integration(applicationClass = Application)
class InspectConfigControllerSpec extends ContainerGebSpec {

    @Issue('GRAILS-11951')
    void "test config properties from plugins"() {
        when:
        go '/inspectConfig/showPropertyValues'

        then:
        $('div', 0).text() == 'Prop One Defined By LoadFirst Plugin'
        $('div', 1).text() == 'Prop Two Defined By LoadSecond Plugin'
        $('div', 2).text() == 'Prop Three Defined By app1 Application'
        $('div', 3).text() == 'Prop One Defined By LoadFirst Plugin'
        $('div', 4).text() == 'Prop Two Defined By LoadSecond Plugin'
        $('div', 5).text() == 'Prop Three Defined By app1 Application'
        $('div', 6).text() == 'Prop One Defined By LoadFirst Plugin'
        $('div', 7).text() == 'Prop Two Defined By LoadSecond Plugin'
        $('div', 8).text() == 'Prop Three Defined By app1 Application'
    }
}
