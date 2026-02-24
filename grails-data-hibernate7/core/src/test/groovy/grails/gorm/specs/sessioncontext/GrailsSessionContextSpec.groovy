package grails.gorm.specs.sessioncontext

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.GrailsSessionContext
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager

class GrailsSessionContextSpec extends HibernateGormDatastoreSpec {

    void "test GrailsSessionContext can be created with a SessionFactory"() {
        given:
        HibernateDatastore hibernateDatastore = manager.hibernateDatastore
        SessionFactoryImplementor sessionFactory = hibernateDatastore.sessionFactory as SessionFactoryImplementor

        when:
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)

        then:
        sessionContext != null
    }

    void "test currentSession() returns session bound via TransactionSynchronizationManager"() {
        given:
        HibernateDatastore hibernateDatastore = manager.hibernateDatastore
        SessionFactoryImplementor sessionFactory = hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)
        Session session = sessionFactory.openSession()
        // unbind whatever the test framework bound, then bind our own session
        TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory)
        TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session))

        when:
        Session current = sessionContext.currentSession()

        then:
        current != null
        current == session

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory)
        if (session.isOpen()) session.close()
    }

    void "test currentSession() throws when no session is bound and allowCreate is false"() {
        given:
        HibernateDatastore hibernateDatastore = manager.hibernateDatastore
        SessionFactoryImplementor sessionFactory = hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)
        // unbind whatever the test framework bound
        def saved = TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory)

        when:
        sessionContext.currentSession()

        then:
        thrown(org.hibernate.HibernateException)

        cleanup:
        // restore the original binding so the framework is not broken for subsequent tests
        if (saved) TransactionSynchronizationManager.bindResource(sessionFactory, saved)
    }

    void "test currentSession() returns session when bound as plain Session resource"() {
        given:
        HibernateDatastore hibernateDatastore = manager.hibernateDatastore
        SessionFactoryImplementor sessionFactory = hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)
        Session session = sessionFactory.openSession()
        TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory)
        TransactionSynchronizationManager.bindResource(sessionFactory, session)

        when:
        Session current = sessionContext.currentSession()

        then:
        current == session

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory)
        if (session.isOpen()) session.close()
    }
}

