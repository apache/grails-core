package org.grails.orm.hibernate.proxy

import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import spock.lang.Specification

class SimpleHibernateProxyHandlerSpec extends Specification {

    void "test isInitialized respects PersistentCollections"() {
        given:
        def ph = new HibernateProxyHandler()

        when:
        def initialized = Mock(PersistentCollection) {
            1 * wasInitialized() >> true
        }
        def notInitialized = Mock(PersistentCollection) {
            1 * wasInitialized() >> false
        }

        then:
        ph.isInitialized(initialized)
        !ph.isInitialized(notInitialized)
    }

    void "test isInitialized respects HibernateProxy"() {
        given:
        def ph = new HibernateProxyHandler()

        when:
        def initialized = Mock(HibernateProxy) {
            1 * getHibernateLazyInitializer() >> Mock(LazyInitializer) {
                1 * isUninitialized() >> false
            }
        }
        def notInitialized = Mock(HibernateProxy) {
            1 * getHibernateLazyInitializer() >> Mock(LazyInitializer) {
                1 * isUninitialized() >> true
            }
        }

        then:
        ph.isInitialized(initialized)
        !ph.isInitialized(notInitialized)
    }
}
