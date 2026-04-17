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
package org.grails.plugin.hibernate.support

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.WebRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class GrailsOpenSessionInViewInterceptorSpec extends Specification {

    @Shared Map config = [
            'dataSource.url':"jdbc:h2:mem:osivSpecDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto': 'create-drop',
            'dataSources.secondary':[url:"jdbc:h2:mem:osivSecondaryDb;LOCK_TIMEOUT=10000"],
    ]

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), OsivSpecBook, OsivSpecAuthor)

    def "test hibernateFlushMode is correctly applied to default session"() {
        given: "An OSIV interceptor"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)

        when: "preHandle is called"
        interceptor.preHandle(webRequest)

        then: "the session is bound with the correct flush mode"
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore.sessionFactory)
        sessionHolder != null
        sessionHolder.session.hibernateFlushMode == FlushMode.COMMIT

        cleanup:
        interceptor.afterCompletion(webRequest, null)
    }

    def "test hibernateFlushMode is correctly applied to secondary session"() {
        given: "An OSIV interceptor"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)

        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')

        when: "preHandle is called"
        interceptor.preHandle(webRequest)

        then: "the secondary session is bound with the correct flush mode"
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(secondaryDatastore.sessionFactory)
        sessionHolder != null
        sessionHolder.session.hibernateFlushMode == FlushMode.COMMIT

        cleanup:
        interceptor.afterCompletion(webRequest, null)
    }

    def "test sessions are unbound and closed after completion"() {
        given: "An OSIV interceptor with bound sessions"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)
        interceptor.preHandle(webRequest)

        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')
        SessionFactory primarySf = datastore.sessionFactory
        SessionFactory secondarySf = secondaryDatastore.sessionFactory

        expect: "Sessions are bound"
        TransactionSynchronizationManager.hasResource(primarySf)
        TransactionSynchronizationManager.hasResource(secondarySf)

        when: "afterCompletion is called"
        interceptor.afterCompletion(webRequest, null)

        then: "Sessions are unbound"
        !TransactionSynchronizationManager.hasResource(primarySf)
        !TransactionSynchronizationManager.hasResource(secondarySf)
    }

    def "test postHandle flushes session if not manual"() {
        given: "An OSIV interceptor with a mocked session"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        def mockSessionFactory = Mock(SessionFactory)
        def mockSession = Mock(Session)
        interceptor.setSessionFactory(mockSessionFactory)

        mockSession.getHibernateFlushMode() >> FlushMode.AUTO
        SessionHolder sessionHolder = new SessionHolder(mockSession)
        TransactionSynchronizationManager.bindResource(mockSessionFactory, sessionHolder)

        WebRequest webRequest = Mock(WebRequest)

        when: "postHandle is called"
        interceptor.postHandle(webRequest, null)

        then: "session.flush() was called exactly once"
        1 * mockSession.flush()

        cleanup:
        TransactionSynchronizationManager.unbindResource(mockSessionFactory)
    }

    def "test postHandle flushes secondary sessions if not manual"() {
        given: "An OSIV interceptor with a mocked secondary session"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        
        def mockSessionFactory = Mock(SessionFactory)
        def mockSession = Mock(Session)
        interceptor.setSessionFactory(Mock(SessionFactory)) // default SF

        mockSession.getHibernateFlushMode() >> FlushMode.AUTO
        
        // We need to inject the secondary session factory into the interceptor's private list
        // or use the setHibernateDatastore with enough mocks.
        def mockDatastore = Mock(HibernateDatastore)
        mockDatastore.getSessionFactory() >> Mock(SessionFactory)
        mockDatastore.getDefaultFlushModeName() >> 'MANUAL'
        
        def connectionSources = Mock(org.grails.datastore.mapping.core.connections.ConnectionSources)
        def secondarySource = Mock(org.grails.datastore.mapping.core.connections.ConnectionSource)
        secondarySource.getName() >> 'secondary'
        connectionSources.getAllConnectionSources() >> [secondarySource]
        mockDatastore.getConnectionSources() >> connectionSources
        
        def secondaryDatastore = Mock(HibernateDatastore)
        secondaryDatastore.isOsivReadOnly() >> false
        secondaryDatastore.getDefaultFlushModeName() >> 'AUTO'
        secondaryDatastore.getSessionFactory() >> mockSessionFactory
        
        mockDatastore.getDatastoreForConnection('secondary') >> secondaryDatastore
        
        interceptor.setHibernateDatastore(mockDatastore)
        
        SessionHolder sessionHolder = new SessionHolder(mockSession)
        TransactionSynchronizationManager.bindResource(mockSessionFactory, sessionHolder)

        WebRequest webRequest = Mock(WebRequest)

        when: "postHandle is called"
        interceptor.postHandle(webRequest, null)

        then: "secondary session.flush() was called"
        1 * mockSession.flush()

        cleanup:
        TransactionSynchronizationManager.unbindResource(mockSessionFactory)
    }

    def "test postHandle handles exceptions during flush"() {
        given: "An OSIV interceptor with a session that fails to flush"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        def mockSessionFactory = Mock(SessionFactory)
        def mockSession = Mock(Session)
        interceptor.setSessionFactory(mockSessionFactory)

        mockSession.getHibernateFlushMode() >> FlushMode.AUTO
        mockSession.flush() >> { throw new RuntimeException("Flush failed") }
        
        SessionHolder sessionHolder = new SessionHolder(mockSession)
        TransactionSynchronizationManager.bindResource(mockSessionFactory, sessionHolder)

        WebRequest webRequest = Mock(WebRequest)

        when: "postHandle is called"
        interceptor.postHandle(webRequest, null)

        then: "The exception is thrown"
        thrown(RuntimeException)

        cleanup:
        TransactionSynchronizationManager.unbindResource(mockSessionFactory)
    }

    def "test setHibernateDatastore with read-only"() {
        given: "A datastore that is read-only"
        def mockDatastore = Mock(HibernateDatastore)
        mockDatastore.isOsivReadOnly() >> true
        mockDatastore.getDefaultFlushModeName() >> 'AUTO'
        mockDatastore.getSessionFactory() >> Mock(SessionFactory)
        
        def connectionSources = Mock(org.grails.datastore.mapping.core.connections.ConnectionSources)
        connectionSources.getAllConnectionSources() >> []
        mockDatastore.getConnectionSources() >> connectionSources

        def interceptor = new GrailsOpenSessionInViewInterceptor()

        when: "setHibernateDatastore is called"
        interceptor.setHibernateDatastore(mockDatastore)

        then: "hibernateFlushMode is MANUAL"
        interceptor.hibernateFlushMode == FlushMode.MANUAL
    }

    def "test preHandle skips if already bound"() {
        given: "A session already bound"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        
        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')
        SessionFactory secondarySf = secondaryDatastore.sessionFactory
        
        Session mockSession = Mock(Session)
        TransactionSynchronizationManager.bindResource(secondarySf, new SessionHolder(mockSession))
        
        WebRequest webRequest = Mock(WebRequest)

        when: "preHandle is called"
        interceptor.preHandle(webRequest)

        then: "No additional session was opened for secondarySf (mockSession is still there)"
        TransactionSynchronizationManager.getResource(secondarySf).session == mockSession

        cleanup:
        TransactionSynchronizationManager.unbindResource(secondarySf)
        interceptor.afterCompletion(webRequest, null)
    }

    def "test postHandle handles multiple exceptions during flush"() {
        given: "An OSIV interceptor with multiple failing secondary sessions"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        
        def mockDatastore = Mock(HibernateDatastore)
        mockDatastore.getSessionFactory() >> Mock(SessionFactory)
        mockDatastore.getDefaultFlushModeName() >> 'MANUAL'
        
        def connectionSources = Mock(org.grails.datastore.mapping.core.connections.ConnectionSources)
        def secondary1 = Mock(org.grails.datastore.mapping.core.connections.ConnectionSource)
        secondary1.getName() >> 'secondary1'
        def secondary2 = Mock(org.grails.datastore.mapping.core.connections.ConnectionSource)
        secondary2.getName() >> 'secondary2'
        connectionSources.getAllConnectionSources() >> [secondary1, secondary2]
        mockDatastore.getConnectionSources() >> connectionSources
        
        def mockSf1 = Mock(SessionFactory)
        def mockSession1 = Mock(Session)
        mockSession1.getHibernateFlushMode() >> FlushMode.AUTO
        mockSession1.flush() >> { throw new RuntimeException("Flush failed 1") }
        
        def mockSf2 = Mock(SessionFactory)
        def mockSession2 = Mock(Session)
        mockSession2.getHibernateFlushMode() >> FlushMode.AUTO
        mockSession2.flush() >> { throw new RuntimeException("Flush failed 2") }

        def ds1 = Mock(HibernateDatastore)
        ds1.isOsivReadOnly() >> false
        ds1.getDefaultFlushModeName() >> 'AUTO'
        ds1.getSessionFactory() >> mockSf1
        
        def ds2 = Mock(HibernateDatastore)
        ds2.isOsivReadOnly() >> false
        ds2.getDefaultFlushModeName() >> 'AUTO'
        ds2.getSessionFactory() >> mockSf2

        mockDatastore.getDatastoreForConnection('secondary1') >> ds1
        mockDatastore.getDatastoreForConnection('secondary2') >> ds2
        
        interceptor.setHibernateDatastore(mockDatastore)
        
        TransactionSynchronizationManager.bindResource(mockSf1, new SessionHolder(mockSession1))
        TransactionSynchronizationManager.bindResource(mockSf2, new SessionHolder(mockSession2))

        WebRequest webRequest = Mock(WebRequest)

        when: "postHandle is called"
        interceptor.postHandle(webRequest, null)

        then: "A RuntimeException is thrown with suppressed exception"
        RuntimeException e = thrown(RuntimeException)
        e.message == "Flush failed 1"
        e.suppressed.length == 1
        e.suppressed[0].message == "Flush failed 2"

        cleanup:
        TransactionSynchronizationManager.unbindResource(mockSf1)
        TransactionSynchronizationManager.unbindResource(mockSf2)
    }

    def "test afterCompletion handles session close exception"() {
        given: "An OSIV interceptor with a secondary session that fails to close"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        
        def mockSessionFactory = Mock(SessionFactory)
        def mockSession = Mock(Session)
        
        def primarySf = Mock(SessionFactory)
        def primarySession = Mock(Session)
        interceptor.setSessionFactory(primarySf)
        
        def mockDatastore = Mock(HibernateDatastore)
        mockDatastore.getSessionFactory() >> primarySf
        mockDatastore.getDefaultFlushModeName() >> 'MANUAL'
        
        def connectionSources = Mock(org.grails.datastore.mapping.core.connections.ConnectionSources)
        def secondarySource = Mock(org.grails.datastore.mapping.core.connections.ConnectionSource)
        secondarySource.getName() >> 'secondary'
        connectionSources.getAllConnectionSources() >> [secondarySource]
        mockDatastore.getConnectionSources() >> connectionSources
        
        def secondaryDatastore = Mock(HibernateDatastore)
        secondaryDatastore.isOsivReadOnly() >> false
        secondaryDatastore.getDefaultFlushModeName() >> 'AUTO'
        secondaryDatastore.getSessionFactory() >> mockSessionFactory
        
        mockDatastore.getDatastoreForConnection('secondary') >> secondaryDatastore
        
        interceptor.setHibernateDatastore(mockDatastore)
        
        TransactionSynchronizationManager.bindResource(mockSessionFactory, new SessionHolder(mockSession))
        TransactionSynchronizationManager.bindResource(primarySf, new SessionHolder(primarySession))

        WebRequest webRequest = Mock(WebRequest)

        when: "afterCompletion is called"
        interceptor.afterCompletion(webRequest, null)

        then: "session.close() was called (even if it throws)"
        1 * mockSession.isOpen() >> true
        1 * mockSession.close() >> { throw new RuntimeException("Close failed") }
        !TransactionSynchronizationManager.hasResource(mockSessionFactory)
        !TransactionSynchronizationManager.hasResource(primarySf)

        cleanup:
        if (TransactionSynchronizationManager.hasResource(mockSessionFactory)) {
            TransactionSynchronizationManager.unbindResource(mockSessionFactory)
        }
        if (TransactionSynchronizationManager.hasResource(primarySf)) {
            TransactionSynchronizationManager.unbindResource(primarySf)
        }
    }

    def "test postHandle with null sessionHolder"() {
        given: "An OSIV interceptor with NO session bound"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        def mockSessionFactory = Mock(SessionFactory)
        interceptor.setSessionFactory(mockSessionFactory)
        // Ensure no resource is bound
        assert TransactionSynchronizationManager.getResource(mockSessionFactory) == null

        WebRequest webRequest = Mock(WebRequest)

        when: "postHandle is called"
        interceptor.postHandle(webRequest, null)

        then: "No exception is thrown and it completes"
        noExceptionThrown()
    }

    def "test afterCompletion calls super even if additional sessions fail"() {
        given: "An OSIV interceptor where additional session unbind fails"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        
        def primarySf = Mock(SessionFactory)
        interceptor.setSessionFactory(primarySf)
        
        // Setup a secondary session that we can't unbind easily? 
        // Actually, let's just make one that throws on close.
        def secondarySf = Mock(SessionFactory)
        def secondarySession = Mock(Session)
        
        def mockDatastore = Mock(HibernateDatastore)
        mockDatastore.getSessionFactory() >> primarySf
        mockDatastore.getDefaultFlushModeName() >> 'MANUAL'
        
        def connectionSources = Mock(org.grails.datastore.mapping.core.connections.ConnectionSources)
        def secondarySource = Mock(org.grails.datastore.mapping.core.connections.ConnectionSource)
        secondarySource.getName() >> 'secondary'
        connectionSources.getAllConnectionSources() >> [secondarySource]
        mockDatastore.getConnectionSources() >> connectionSources
        
        def secondaryDatastore = Mock(HibernateDatastore)
        secondaryDatastore.isOsivReadOnly() >> true
        secondaryDatastore.getSessionFactory() >> secondarySf
        mockDatastore.getDatastoreForConnection('secondary') >> secondaryDatastore
        
        interceptor.setHibernateDatastore(mockDatastore)
        
        TransactionSynchronizationManager.bindResource(secondarySf, new SessionHolder(secondarySession))
        TransactionSynchronizationManager.bindResource(primarySf, new SessionHolder(Mock(Session)))

        WebRequest webRequest = Mock(WebRequest)

        when: "afterCompletion is called"
        interceptor.afterCompletion(webRequest, null)

        then: "additional session close is attempted"
        1 * secondarySession.isOpen() >> true
        1 * secondarySession.close() >> { throw new RuntimeException("fail") }
        
        and: "primary session is still handled via super"
        // If super.afterCompletion was called, it would have tried to get resource for primarySf
        !TransactionSynchronizationManager.hasResource(secondarySf)
        !TransactionSynchronizationManager.hasResource(primarySf)

        cleanup:
        if (TransactionSynchronizationManager.hasResource(secondarySf)) {
            TransactionSynchronizationManager.unbindResource(secondarySf)
        }
        if (TransactionSynchronizationManager.hasResource(primarySf)) {
            TransactionSynchronizationManager.unbindResource(primarySf)
        }
    }
}

@Entity
class OsivSpecBook {
    String title
    static mapping = {
        datasource 'secondary'
    }
}

@Entity
class OsivSpecAuthor {
    String name
}
