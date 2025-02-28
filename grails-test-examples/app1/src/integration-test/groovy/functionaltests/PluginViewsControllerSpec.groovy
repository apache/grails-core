package functionaltests

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 */
@Integration(applicationClass = Application)
@Rollback
class PluginViewsControllerSpec extends ContainerGebSpec {

    void "Test that when plugin templates are rendered they render correctly"() {
        when:"A view that renders plugin templates is visisted"
            go '/pluginViews/testTemplateFromPlugin'

        then:"The title is correct"
        	$('div', 0).text() == "Template from plugin: Hello from plugin"
            $('div', 1).text() == "Template from plugin no plugin attribute: Hello from app"
    }

    void "Test that views from a plugin loaded after another plugin override when using loadAfter"() {
        when:"A view that renders plugin views"
            go '/pluginViews/testPluginViewOverrideInPlugin'

        then:"The title is correct"
            $('h1', 0).text() == "Second Plugin"
            
    }    
}
