package functionaltests

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 */
@Integration(applicationClass = Application)
@Rollback
class ForwardingSpec extends ContainerGebSpec {
    void "params extracted from the URL path by a custom UrlMapping are forwarded"() {
        when: 'a url mapping such as /forward/$param1 is matched'
        go '/forward/test'
        then: 'param1 is passed to the forwarded action'
        $().text() == 'Forward Destination. Params: test'
    }

    void "verifies params from the original url are passed to the forwarded action but not duplicated"() {
        when:
        go '/forwarding/two?param1=test'
        then:
        $().text() == 'Forward Destination. Params: test'
    }

    void "Test forward to same controller"() {
        when:"A forward is issued to an action in the same controller"
            go '/forwarding/one'

        then:"The forward works correctly"
        	$().text() == 'Forward Destination. Params:'
    }

    void "Test forward to named controller"() {
        when:"A forward is issued to an action in the same controller"
            go '/forwarding/two'

        then:"The forward works correctly"
            $().text() == 'Forward Destination. Params:'
    }

    void "Test forward with parameters"() {
        when:"A forward is issued to an action with parameters"
            go '/forwarding/three'

        then:"The forward works correctly"
            $().text() == 'Forward Destination. Params: test'
    }

    void 'Test forwarding to an action which returns a Map'() {
        when:
            go '/forwarding/forwardToList'

        then:
            $('li', text: 'Jeff')
            $('li', text: 'Zack')
            $('li', text: 'Jake')
            $('li', text: 'Betsy')
    }

    void "Test forward after populating flash"() {
        when: 'an acton populates flash and then forwards'
        go '/forwarding/putMessageInFlash'

        then: 'the flash data is available in the action that was forwarded to'
        $('div', id: 'message').text() == 'flash.message is [some message]'

        when: 'a subsequent request is initiated'
        go '/forwarding/displayFlash'

        then: 'the flash data is still available'
        $('div', id: 'message').text() == 'flash.message is [some message]'

        when: 'any further request is initiated'
        go '/forwarding/displayFlash'

        then: 'the flash message has been cleared'
        $('div', id: 'message').text() == 'flash.message is []'
    }
}
