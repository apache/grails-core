package functionaltests


import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.PendingFeature

@Integration(applicationClass = Application)
class HomeSpec extends ContainerGebSpec {

    @PendingFeature(reason = 'Pending fix for https://github.com/grails/grails-core/issues/13846')
    void "Test the home page renders correctly"() {
        when:"The home page is visited"
            go '/'
            if(title != "Welcome to Grails") {
              println driver.pageSource
            }
        then:"The title is correct"
            title == "Welcome to Grails"
            $('li.controller', text: 'demo.AlphaController')
            $('li.controller', text: 'functionaltests.BookController')
            $('li.controller', text: 'functionaltests.ErrorsController')
            $('li.controller', text: 'functionaltests.ForwardingController')
            $('li.controller', text: 'functionaltests.InspectConfigController')
            $('li.controller', text: 'functionaltests.MiscController')
            $('li.controller', text: 'functionaltests.UploadController')
            $('li.controller', text: 'grails.functionaltests.InGrailsPackageController')
            
            // SimpleController should not become a controller as it is not in a grails-app/controllers dir
            !$('li.controller', text: 'functionaltests.SimpleController')

    }
}
