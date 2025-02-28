package app2

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.PendingFeature

/**
 */
@Integration(applicationClass = Application)
@Rollback
class ErrorsControllerSpec extends ContainerGebSpec {

    @PendingFeature(reason = 'pagesource does not contain the value')
    void "Test a global 500 mapping that maps to another controller"() {
        when:"An action that throws a error that is handled by a 500 mapping in UrlMappings.groovy"
            go '/test/throwGeneralError'

        then:"The correct action is executed"
            driver.pageSource.contains 'error occured'
    }

}
