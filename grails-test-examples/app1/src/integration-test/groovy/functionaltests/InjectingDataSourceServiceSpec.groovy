package functionaltests

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration(applicationClass = Application)
class InjectingDataSourceServiceSpec extends Specification {

    InjectingDataSourceService injectingDataSourceService

    def "you can inject DataSource with def dataSource"() {
        expect:
        injectingDataSourceService != null

        and:
        injectingDataSourceService.dataSourceInjectionWorked()
    }
}
