package micronaut

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

@Integration
class BeanInjectionServiceSpec extends Specification {

    @Autowired
    BeanInjectionService beanInjectionService

    @Autowired
    TestService service

    @Autowired
    List<BeanInjectionService> bInjectService

    void "test injecting Micronaut beans in a Grails service"() {
        expect:
        beanInjectionService.namedServices.size() == 4
        beanInjectionService.namedService2.name == "regular"
        beanInjectionService.namedService3.name == "special"
        beanInjectionService.namedService4.name == "qualified"
        beanInjectionService.namedService.name == "primary"
    }

    void "test autowire Grails service by type"() {
        expect:
        service != null
    }

    void "test there are two beans for a Grails service when we inject Micronaut bean in it"() {
        expect:
        bInjectService != null
        bInjectService.size() == 2
    }
}
