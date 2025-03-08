package org.grails.datastore.gorm

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import groovy.sql.Sql
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.orm.hibernate.GrailsHibernateTransactionManager
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.h2.Driver
import org.hibernate.SessionFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationContext
import org.springframework.orm.hibernate5.SessionFactoryUtils
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager

class Setup {
    static GrailsApplication grailsApplication
    static HibernateDatastore hibernateDatastore
    static hibernateSession
    static GrailsHibernateTransactionManager transactionManager
    static SessionFactory sessionFactory
    static TransactionStatus transactionStatus
    static HibernateMappingContextConfiguration hibernateConfig
    static ApplicationContext applicationContext

    @CompileStatic
    static destroy() {
        if (transactionStatus != null) {
            def tx = transactionStatus
            transactionStatus = null
            transactionManager.rollback(tx)
        }
        if (hibernateSession != null) {
            SessionFactoryUtils.closeSession( (org.hibernate.Session)hibernateSession )
        }

        if(hibernateConfig != null) {
            hibernateConfig = null
        }
        hibernateDatastore.destroy()
        grailsApplication = null
        hibernateDatastore = null
        hibernateSession = null
        transactionManager = null
        sessionFactory = null
        if(applicationContext instanceof DisposableBean) {
            applicationContext.destroy()
        }
        applicationContext = null
        shutdownInMemDb()
    }

    static shutdownInMemDb() {
        Sql sql = null
        try {
            sql = Sql.newInstance('jdbc:h2:mem:grailsDb', 'sa', '', Driver.name)
            sql.executeUpdate('SHUTDOWN')
        } catch (e) {
            // already closed, ignore
        } finally {
            try { sql?.close() } catch (ignored) {}
        }
    }

    static Session setup(List<Class> classes, ConfigObject grailsConfig = new ConfigObject(), boolean isTransactional = true) {
        System.setProperty("hibernate5.gorm.suite", "true")
        grailsApplication = new DefaultGrailsApplication(classes as Class[], new GroovyClassLoader(Setup.getClassLoader()))
        if(grailsConfig) {
            grailsApplication.config.putAll(grailsConfig)
        }

        grailsConfig.dataSource.dbCreate = "create-drop"
        hibernateDatastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(grailsConfig), classes as Class[])
        transactionManager = hibernateDatastore.getTransactionManager()
        sessionFactory = hibernateDatastore.sessionFactory
        if (transactionStatus == null && isTransactional) {
            transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition())
        }
        else if(isTransactional){
            throw new RuntimeException("new transaction started during active transaction")
        }
        if(!isTransactional) {
            hibernateSession = sessionFactory.openSession()
            TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(hibernateSession))
        }
        else {
            hibernateSession = sessionFactory.currentSession
        }

        return hibernateDatastore.connect()
    }
}
