package example

import grails.testing.mixin.integration.Integration
import spock.lang.Issue
import spock.lang.Specification

@Integration
class ServiceInjectionSpec extends Specification {

    ClassUsingAService classUsingAService

    @Issue('https://github.com/grails/gorm-hibernate5/issues/202')
    void 'data-service is injected correctly'() {
        when:
        classUsingAService.doSomethingWithTheService()

        then:
        noExceptionThrown()
    }

}
