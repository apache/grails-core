package example

import org.hibernate.Hibernate

import grails.gorm.transactions.Rollback
import grails.test.hibernate.HibernateSpec

/**
 * Tests Proxy with hibernate-groovy-proxy
 */

class ProxySpec extends HibernateSpec {

    @Rollback
    void "Test Proxy"() {
        when:
        new Customer(1, "Bob").save(failOnError: true, flush: true)
        hibernateDatastore.currentSession.clear()

        def proxy
        Customer.withNewSession {
            proxy = Customer.load(1)
        }

        then:
        //without ByteBuddyGroovyInterceptor this would normally cause the proxy to init
        proxy
        proxy.metaClass
        proxy.getMetaClass()
        !Hibernate.isInitialized(proxy)
        //id calls
        proxy.id == 1
        proxy.getId() == 1
        proxy["id"] == 1
        !Hibernate.isInitialized(proxy)
        // gorms trait implements in the class so no way to tell
        // proxy.toString() == "Customer : 1 (proxy)"
        // !Hibernate.isInitialized(proxy)
    }

}
