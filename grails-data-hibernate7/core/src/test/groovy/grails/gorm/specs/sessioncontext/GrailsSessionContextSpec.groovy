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
package grails.gorm.specs.sessioncontext

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.GrailsSessionContext
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager

class GrailsSessionContextSpec extends HibernateGormDatastoreSpec {

    def setup() {
        TransactionSynchronizationManager.unbindResourceIfPossible(manager.hibernateDatastore.sessionFactory)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    def cleanup() {
        TransactionSynchronizationManager.unbindResourceIfPossible(manager.hibernateDatastore.sessionFactory)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

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
        SessionFactoryImplementor sessionFactory = manager.hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)
        Session session = sessionFactory.openSession()
        TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session))

        when:
        Session current = sessionContext.currentSession()

        then:
        current != null
        current == session

        cleanup:
        if (session.isOpen()) session.close()
    }

    void "test currentSession() throws when no session is bound and allowCreate is false"() {
        given:
        SessionFactoryImplementor sessionFactory = manager.hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)

        when:
        sessionContext.currentSession()

        then:
        thrown(org.hibernate.HibernateException)
    }

    void "test currentSession() returns session when bound as plain Session resource"() {
        given:
        SessionFactoryImplementor sessionFactory = manager.hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)
        Session session = sessionFactory.openSession()
        TransactionSynchronizationManager.bindResource(sessionFactory, session)

        when:
        Session current = sessionContext.currentSession()

        then:
        current == session

        cleanup:
        if (session.isOpen()) session.close()
    }

    void "test initJta handles missing JtaPlatform"() {
        given:
        SessionFactoryImplementor sessionFactory = Mock(SessionFactoryImplementor)
        org.hibernate.service.spi.ServiceRegistryImplementor registry = Mock(org.hibernate.service.spi.ServiceRegistryImplementor)
        sessionFactory.getServiceRegistry() >> registry
        registry.getService(org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform) >> null
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)

        when:
        sessionContext.initJta()

        then:
        noExceptionThrown()
        sessionContext.jtaSessionContext == null
    }

    void "test currentSession() switches to AUTO flush mode when sync is active"() {
        given:
        SessionFactoryImplementor sessionFactory = manager.hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)
        Session session = sessionFactory.openSession()
        session.setHibernateFlushMode(FlushMode.MANUAL)
        
        TransactionSynchronizationManager.initSynchronization()
        TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session))

        when:
        Session current = sessionContext.currentSession()

        then:
        current.getHibernateFlushMode() == FlushMode.AUTO
        
        cleanup:
        if (session.isOpen()) session.close()
    }

    void "test currentSession() creates a new session when allowCreate is true"() {
        given:
        SessionFactoryImplementor sessionFactory = manager.hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)
        sessionContext.allowCreate = true

        when:
        Session session = sessionContext.currentSession()

        then:
        session != null
        session.isOpen()

        cleanup:
        if (session?.isOpen()) session.close()
    }

    void "test currentSession() with active transaction and allowCreate"() {
        given:
        SessionFactoryImplementor sessionFactory = manager.hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)
        sessionContext.allowCreate = true
        
        TransactionSynchronizationManager.initSynchronization()

        when:
        Session session = sessionContext.currentSession()

        then:
        session != null
        TransactionSynchronizationManager.hasResource(sessionFactory)
        ((SessionHolder)TransactionSynchronizationManager.getResource(sessionFactory)).isSynchronizedWithTransaction()

        cleanup:
        if (session?.isOpen()) session.close()
    }

    void "test createSession() sets FlushMode MANUAL when transaction is read-only"() {
        given:
        SessionFactoryImplementor sessionFactory = manager.hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)
        sessionContext.allowCreate = true

        TransactionSynchronizationManager.initSynchronization()
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true)

        when:
        Session session = sessionContext.currentSession()

        then:
        session != null
        session.getHibernateFlushMode() == FlushMode.MANUAL

        cleanup:
        if (session?.isOpen()) session.close()
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false)
    }

    void "test currentSession() with already-synchronized SessionHolder skips re-registration"() {
        given:
        SessionFactoryImplementor sessionFactory = manager.hibernateDatastore.sessionFactory as SessionFactoryImplementor
        GrailsSessionContext sessionContext = new GrailsSessionContext(sessionFactory)
        Session session = sessionFactory.openSession()
        SessionHolder holder = new SessionHolder(session)
        holder.setSynchronizedWithTransaction(true)

        TransactionSynchronizationManager.initSynchronization()
        TransactionSynchronizationManager.bindResource(sessionFactory, holder)

        when:
        Session current = sessionContext.currentSession()

        then:
        current == session
        holder.isSynchronizedWithTransaction()

        cleanup:
        if (session.isOpen()) session.close()
    }
}
