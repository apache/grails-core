package functionaltests.chaining

import functionaltests.Application
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
class ChainingToNamespacedControllersFunctionalSpec extends ContainerGebSpec {

    void "Test chaining to a namespaced controller"() {
        when:"A controller action chains to a namespaced controller"
            go '/chainingHome/chainDemo?ns=alpha'

        then:"The chain works correctly"
        	$('body').text() == 'rendered from the Demo controller in the alpha namespace'

    when:"A controller action chains to a namespaced controller"
        go '/chainingHome/chainDemo?ns=beta'

    then:"The chain works correctly"
        $('body').text() == 'rendered from the Demo controller in the beta namespace'
    }
}
