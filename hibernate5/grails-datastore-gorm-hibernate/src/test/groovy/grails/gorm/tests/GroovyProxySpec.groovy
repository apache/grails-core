package grails.gorm.tests

import org.grails.datastore.gorm.proxy.GroovyProxyFactory

import spock.lang.Ignore

/**
 * @author graemerocher
 */
class GroovyProxySpec extends GormDatastoreSpec {

    // this test is ignored because Groovy proxies are not used with Hibernate
    @Ignore
    void "Test creation and behavior of Groovy proxies"() {

        given:
        session.mappingContext.proxyFactory = new GroovyProxyFactory()
        def id = new Location(name:"United Kingdom", code:"UK").save(flush:true)?.id
        session.clear()

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