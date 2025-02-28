package functionaltests.cors

import functionaltests.Application
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.web.mapping.cors.GrailsCorsConfiguration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

@Integration(applicationClass = Application)
@Rollback
class GrailsCorsConfigurationSpec extends Specification {

    @Autowired
    GrailsCorsConfiguration grailsCorsConfiguration

    void "test configuration is as expected"() {

        expect:
        grailsCorsConfiguration.mappings.size() == 1
        grailsCorsConfiguration.mappings.get('/api/**') != null
        grailsCorsConfiguration.corsConfigurations.size() == 1
    }

}
