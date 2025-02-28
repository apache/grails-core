package namespaces


import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.PendingFeature

@Integration
class PageControllerSpec extends ContainerGebSpec {

    void "test that the page renders correctly"() {
        when: "The admin page is visited"
        go '/myAppTest/admin/page/index'

        then: "The title is correct"
        title == 'Admin Page'

        when: "The page is visited without namespace"
        go '/myAppTest/page/index'

        then: "The title is correct"
        title == 'Frontend Page'
    }
}
