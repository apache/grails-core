package org.grails.orm.hibernate

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.SingletonConnectionSources
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSource
import org.grails.orm.hibernate.connections.HibernateConnectionSource
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings
import spock.lang.AutoCleanup

class ChildHibernateDatastoreUnitSpec extends HibernateGormDatastoreSpec {

    void "test child datastore with real objects"() {
        given: "A primary datastore (parent)"
        HibernateDatastore parent = getDatastore()
        
        and: "A secondary connection source"
        def secondaryUrl = "jdbc:h2:mem:secondaryDB;LOCK_TIMEOUT=10000"
        def dataSource = new DriverManagerDataSource(secondaryUrl, "sa", "")
        def settings = new HibernateConnectionSourceSettings()
        
        def factory = parent.connectionSources.getFactory()
        def dataSourceConnectionSource = new DataSourceConnectionSource("secondary", dataSource, settings.getDataSource())
        
        def secondaryConnectionSource = factory.create("secondary", dataSourceConnectionSource, settings)
        
        when: "A child datastore is created"
        def child = new HibernateDatastore.ChildHibernateDatastore(
                parent, 
                new SingletonConnectionSources(secondaryConnectionSource, parent.connectionSources.getBaseConfiguration()),
                parent.mappingContext,
                parent.eventPublisher
        )

        then: "It has its own session factory"
        child.getSessionFactory() != parent.getSessionFactory()
        
        when: "Executing a session on the child"
        String url = null
        child.withNewSession { Session s ->
            url = s.doReturningWork { it.getMetaData().getURL() }
        }

        then: "It uses the secondary database URL"
        url.startsWith("jdbc:h2:mem:secondaryDB")

        when: "Asking for the default connection from the child"
        def resolved = child.getDatastoreForConnection(ConnectionSource.DEFAULT)

        then: "It returns the parent"
        resolved == parent

        cleanup:
        secondaryConnectionSource?.close()
    }
}
