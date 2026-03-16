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

