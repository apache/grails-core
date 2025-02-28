package functionaltests.sitemesh

import functionaltests.Application
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue
import spock.lang.PendingFeature

@Integration(applicationClass = Application)
class SitemeshLayoutFunctionalSpec extends ContainerGebSpec {

    @Issue('GRAILS-12045')
    @PendingFeature(reason = 'title is empty')
    void 'test layout by convention'() {
        when:
        go '/layoutByConvention'

        then:
        title == 'Convention Layout'
    }

    @Issue('GRAILS-12045')
    @PendingFeature(reason='title is empty')
    void 'test layout specified in controller property'() {
        when:
        go '/layoutSpecifiedByProperty'

        then:
        title == 'Foo Layout'

    }

    @Issue('GRAILS-12045')
    @PendingFeature(reason='text does not contain the value')
    void 'test layout specified in controller property applied to a GSP that does not contain a root html tag'() {
        when:
        go '/layoutSpecifiedByProperty/snippetView'

        then:
        title  == 'Foo Layout'
        $().text().contains 'this is some content'
    }
}
