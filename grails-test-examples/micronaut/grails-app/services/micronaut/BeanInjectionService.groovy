package micronaut

import bean.injection.NamedService
import bean.injection.Qualified
import org.springframework.beans.factory.annotation.Autowired

import jakarta.inject.Named

class BeanInjectionService {

    @Autowired
    List<NamedService> namedServices

    @Autowired
    @Named('regular')
    NamedService namedService2

    @Autowired
    @Named('special')
    NamedService namedService3

    @Autowired
    @Qualified
    NamedService namedService4

    @Autowired
    NamedService namedService
}
