package functional.tests

import org.grails.datastore.mapping.model.MappingContext

/**
 * Created by jameskleeh on 4/6/17.
 */
class ProxyController {

    MappingContext grailsDomainClassMappingContext

    def index() {
        Proxy proxy = Proxy.load(1)
        grailsDomainClassMappingContext.proxyHandler.initialize(proxy)
        assert grailsDomainClassMappingContext.proxyHandler.isProxy(proxy)
        assert grailsDomainClassMappingContext.proxyHandler.isInitialized(proxy)
        respond(proxies: [proxy])
    }
}
