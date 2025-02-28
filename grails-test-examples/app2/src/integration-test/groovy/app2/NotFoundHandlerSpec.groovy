package app2

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
@Rollback
class NotFoundHandlerSpec extends ContainerGebSpec {

    void "Test that a 404 handler renders the view correctly when a forward is executed"() {
        when:"An action is visited where an interceptor uses response.sendError(404)"
            go '/foo/index'

        then:"The 404 handler is rendered"
        	title == "Page Not Found"

        when:"No response.sendError(404) method is called"
            go '/foo/index?user=admin'

        then:"The 404 handler is not executed"
            title == "Foo List"            
    }
}
