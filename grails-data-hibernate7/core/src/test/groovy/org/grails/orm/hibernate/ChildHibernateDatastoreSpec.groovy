package org.grails.orm.hibernate

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.connections.HibernateConnectionSource
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.springframework.context.ApplicationEventPublisher
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.springframework.core.env.PropertyResolver
import spock.lang.Specification
import grails.gorm.annotation.Entity

class ChildHibernateDatastoreSpec extends Specification {

    void "test child datastore initialization and delegation"() {
        given: "A parent datastore and necessary mocks"
        def config = Collections.singletonMap(Settings.SETTING_DB_CREATE, "create-drop")
        HibernateDatastore parent = new HibernateDatastore(config, ChildTestBook)
        
        def sessionFactory = Mock(SessionFactory)
        def connectionSource = Mock(HibernateConnectionSource)
        def connectionSources = Mock(ConnectionSources)
        def mappingContext = parent.getMappingContext()
        def eventPublisher = Mock(ConfigurableApplicationEventPublisher)
        def settings = new HibernateConnectionSourceSettings()
        def hibernateSettings = settings.getHibernate()
        def interceptor = Mock(org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor)
        hibernateSettings.setEventTriggeringInterceptor(interceptor)
        def propResolver = Mock(PropertyResolver)

        connectionSources.getDefaultConnectionSource() >> connectionSource
        connectionSources.getBaseConfiguration() >> propResolver
        connectionSources.getFactory() >> null
        connectionSource.getSource() >> sessionFactory
        connectionSource.getSettings() >> settings
        connectionSource.getName() >> "secondary"

        when: "A child datastore is created"
        def child = new HibernateDatastore.ChildHibernateDatastore(parent, connectionSources, mappingContext, eventPublisher)

        then: "It has its own session factory but shares the mapping context"
        child.getSessionFactory() == sessionFactory
        child.mappingContext == mappingContext

        when: "Asking for the default connection"
        def defaultDs = child.getDatastoreForConnection(ConnectionSource.DEFAULT)

        then: "It delegates to the parent"
        defaultDs == parent

        when: "Asking for a sibling connection"
        // Simulate a sibling by adding it to parent's map
        parent.datastoresByConnectionSource.put("other", Mock(HibernateDatastore))
        def result = child.getDatastoreForConnection("other")

        then: "It delegates sibling lookup to the parent"
        result != null
        result == parent.datastoresByConnectionSource.get("other")

        when: "Executing withNewSession on the child"
        def session = Mock(Session)
        sessionFactory.openSession() >> session
        def resultValue = null
        child.withNewSession { s ->
            resultValue = "success"
        }

        then: "It uses its own session factory"
        resultValue == "success"
        
        cleanup:
        parent.close()
    }
}

@Entity
class ChildTestBook {
    Long id
    String title
}
