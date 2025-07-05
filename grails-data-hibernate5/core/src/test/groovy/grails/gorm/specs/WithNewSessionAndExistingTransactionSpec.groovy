/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.specs

import org.apache.grails.data.testing.tck.domains.Book
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Issue

import javax.sql.DataSource

/**
 * Created by graemerocher on 26/08/2016.
 */
class WithNewSessionAndExistingTransactionSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([Book])
    }

    void "Test withNewSession when an existing transaction is present"() {
        when: "An existing transaction not to pick up the current session"
        manager.sessionFactory.currentSession
        SessionHolder previousSessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)
        Book.withNewSession { Session session ->
            // access the current session
            assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(manager.sessionFactory))
            session.sessionFactory.currentSession
        }
        // reproduce session closed problem
        int result = Book.count()
        SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)
        DataSource dataSource = ((HibernateDatastore) manager.session.datastore).connectionSources.defaultConnectionSource.dataSource
        org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource = dataSource.targetDataSource.targetDataSource

        then: "The result is correct"
        dataSource != null
        tomcatDataSource != null
        tomcatDataSource.pool.active == 1
        sessionHolder.is(previousSessionHolder)
        TransactionSynchronizationManager.isSynchronizationActive()
        sessionHolder.session.isOpen()
        sessionHolder.isSynchronizedWithTransaction()
        manager.sessionFactory.currentSession.isOpen()
        result == 0
        Book.count() == 0
        manager.sessionFactory.currentSession == manager.hibernateSession
        manager.hibernateSession.isOpen()
    }

    @Issue('https://github.com/grails/grails-core/issues/10426')
    void "Test with withNewSession with nested transaction"() {
        when: "An existing transaction not to pick up the current session"
        manager.sessionFactory.currentSession
        SessionHolder previousSessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)
        Book.withNewSession { Session session ->
            assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(manager.sessionFactory))
            // access the current session
            session.sessionFactory.currentSession
            // reproduce "Pre-bound JDBC Connection found!" problem
            Book.withNewTransaction {
                assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(manager.sessionFactory))
                new Book(title: "The Stand", author: 'Stephen King').save()
            }
        }

        Book.count()
        SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)

        DataSource dataSource = ((HibernateDatastore) manager.session.datastore).connectionSources.defaultConnectionSource.dataSource
        org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource = dataSource.targetDataSource.targetDataSource

        then: "The result is correct"
        dataSource != null
        tomcatDataSource != null
        tomcatDataSource.pool.active == 1
        sessionHolder.is(previousSessionHolder)
        TransactionSynchronizationManager.isSynchronizationActive()
        sessionHolder.session.isOpen()
        sessionHolder.isSynchronizedWithTransaction()
        manager.sessionFactory.currentSession.isOpen()
        manager.sessionFactory.currentSession == manager.hibernateSession
        manager.hibernateSession.isOpen()
    }

    @Issue('https://github.com/grails/grails-core/issues/10448')
    void "Test with withNewSession with existing transaction"() {

        when: "the connection pool is obtained"
        DataSource dataSource = ((HibernateDatastore) manager.session.datastore).connectionSources.defaultConnectionSource.dataSource
        org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource = dataSource.targetDataSource.targetDataSource

        then: "the active count is correct"
        dataSource != null
        tomcatDataSource != null
        tomcatDataSource.pool.active == 0

        when: "An existing transaction not to pick up the current session"
        manager.sessionFactory.currentSession
        SessionHolder previousSessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)
        Book.withNewTransaction { TransactionStatus status ->
            // reproduce "java.lang.IllegalStateException: No value for key" problem
            Book.withNewSession { Session session ->
                // access the current session
                assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(manager.sessionFactory))
                session.sessionFactory.currentSession

                new Book(title: "The Stand", author: 'Stephen King').save()
            }
        }

        SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(manager.sessionFactory)


        then: "After withNewSession is completed all connections are closed"
        tomcatDataSource.pool.active == 0

        when: "A count is executed that uses the current connection"
        Book.count()

        then: "The result is correct"
        tomcatDataSource.pool.active == 1
        sessionHolder.is(previousSessionHolder)
        TransactionSynchronizationManager.isSynchronizationActive()
        sessionHolder.session.isOpen()
        sessionHolder.isSynchronizedWithTransaction()
        manager.sessionFactory.currentSession.isOpen()
        manager.sessionFactory.currentSession == manager.hibernateSession
        manager.hibernateSession.isOpen()
    }
}
