package demo

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
class SecuredControllerSpec extends ContainerGebSpec {

    def "test RoleHierarchyEntry lifecycle"() {
        when:
        to SecuredPage

        then:
        at LoginPage

        when:
        login('sherlock', 'elementary')

        then:
        $().text().contains 'Sorry, you\'re not authorized to view this page.'

        when:
        go 'secured/grantRoleHierarchyEntry'

        then:
        browser.driver.pageSource.contains 'OK'

        when:
        to SecuredPage

        then:
        browser.driver.pageSource.contains 'you have ROLE_ADMIN'
    }
}
