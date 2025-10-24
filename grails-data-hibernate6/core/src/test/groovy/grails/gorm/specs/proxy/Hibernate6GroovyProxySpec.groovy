package grails.gorm.specs.proxy

import org.apache.grails.data.hibernate6.core.GrailsDataHibernate6TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.Location
import org.grails.datastore.gorm.proxy.GroovyProxyFactory

/**
 * @author graemerocher
 */
//TODO Are we still supporting Proxies?
class Hibernate6GroovyProxySpec extends GrailsDataTckSpec<GrailsDataHibernate6TckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Location])
    }
    void "Test creation and behavior of Groovy proxies"() {
        given:
        manager.session.mappingContext.proxyFactory = new GroovyProxyFactory()
        def id = new Location(name: "United Kingdom", code: "UK").save(flush: true)?.id
        manager.session.clear()

        when:
        def location = Location.proxy(id)

        then:

        location != null
        id == location.id
        false == location.isInitialized()
        false == location.initialized

        "UK" == location.code
        "United Kingdom - UK" == location.namedAndCode()
        true == location.isInitialized()
        true == location.initialized
        null != location.target
    }
}