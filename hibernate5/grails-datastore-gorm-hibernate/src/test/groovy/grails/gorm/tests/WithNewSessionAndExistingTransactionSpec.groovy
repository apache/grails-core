package grails.gorm.tests

import org.grails.datastore.gorm.Setup
import org.grails.orm.hibernate.GormSpec
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.orm.hibernate5.SpringSessionSynchronization
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Issue

import javax.sql.DataSource

/**
 * Created by graemerocher on 26/08/2016.
 */
class WithNewSessionAndExistingTransactionSpec extends GormSpec {

    @Override
    List getDomainClasses() {
        [Book]
    }

    void "Test withNewSession when an existing transaction is present"() {
        when:"An existing transaction not to pick up the current session"
        sessionFactory.currentSession
        SessionHolder previousSessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)
        Book.withNewSession { Session session ->
            // access the current session
            assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(sessionFactory))
            session.sessionFactory.currentSession
        }
        // reproduce session closed problem
        int result =  Book.count()
        SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)
        DataSource dataSource = ((HibernateDatastore)session.datastore).connectionSources.defaultConnectionSource.dataSource
        org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource = dataSource.targetDataSource.targetDataSource

        then:"The result is correct"
        dataSource != null
        tomcatDataSource != null
        tomcatDataSource.pool.active == 1
        sessionHolder.is(previousSessionHolder)
        TransactionSynchronizationManager.isSynchronizationActive()
        sessionHolder.session.isOpen()
        sessionHolder.isSynchronizedWithTransaction()
        sessionFactory.currentSession.isOpen()
        result == 0
        Book.count() == 0
        sessionFactory.currentSession == Setup.hibernateSession
        Setup.hibernateSession.isOpen()
    }

    @Issue('https://github.com/grails/grails-core/issues/10426')
    void "Test with withNewSession with nested transaction"() {
        when:"An existing transaction not to pick up the current session"
        sessionFactory.currentSession
        SessionHolder previousSessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)
        Book.withNewSession { Session session ->
            assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(sessionFactory))
            // access the current session
            session.sessionFactory.currentSession
            // reproduce "Pre-bound JDBC Connection found!" problem
            Book.withNewTransaction {
                assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(sessionFactory))
                new Book(title: "The Stand", author: 'Stephen King').save()
            }
        }

        Book.count()
        SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)

        DataSource dataSource = ((HibernateDatastore)session.datastore).connectionSources.defaultConnectionSource.dataSource
        org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource = dataSource.targetDataSource.targetDataSource

        then:"The result is correct"
        dataSource != null
        tomcatDataSource != null
        tomcatDataSource.pool.active == 1
        sessionHolder.is(previousSessionHolder)
        TransactionSynchronizationManager.isSynchronizationActive()
        sessionHolder.session.isOpen()
        sessionHolder.isSynchronizedWithTransaction()
        sessionFactory.currentSession.isOpen()
        sessionFactory.currentSession == Setup.hibernateSession
        Setup.hibernateSession.isOpen()
    }

    @Issue('https://github.com/grails/grails-core/issues/10448')
    void "Test with withNewSession with existing transaction"() {

        when:"the connection pool is obtained"
        DataSource dataSource = ((HibernateDatastore)session.datastore).connectionSources.defaultConnectionSource.dataSource
        org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource = dataSource.targetDataSource.targetDataSource

        then:"the active count is correct"
        dataSource != null
        tomcatDataSource != null
        tomcatDataSource.pool.active == 0

        when:"An existing transaction not to pick up the current session"
        sessionFactory.currentSession
        SessionHolder previousSessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)
        Book.withNewTransaction { TransactionStatus status ->
            // reproduce "java.lang.IllegalStateException: No value for key" problem
            Book.withNewSession { Session session ->
                // access the current session
                assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(sessionFactory))
                session.sessionFactory.currentSession

                new Book(title: "The Stand", author: 'Stephen King').save()
            }
        }

        SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)


        then:"After withNewSession is completed all connections are closed"
        tomcatDataSource.pool.active == 0

        when:"A count is executed that uses the current connection"
        Book.count()

        then:"The result is correct"
        tomcatDataSource.pool.active == 1
        sessionHolder.is(previousSessionHolder)
        TransactionSynchronizationManager.isSynchronizationActive()
        sessionHolder.session.isOpen()
        sessionHolder.isSynchronizedWithTransaction()
        sessionFactory.currentSession.isOpen()
        sessionFactory.currentSession == Setup.hibernateSession
        Setup.hibernateSession.isOpen()
    }
}
