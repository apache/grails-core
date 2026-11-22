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
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.FlushMode
import org.hibernate.HibernateException
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MultiDataSourceSessionSpec extends Specification {

    @Shared Map config = [
            'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'create-drop',
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto': 'create-drop',
            'dataSources.secondary':[url:"jdbc:h2:mem:secondaryDb;LOCK_TIMEOUT=10000"],
    ]

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), OsivBook, OsivAuthor)

    void cleanup() {
        [datastore.sessionFactory, datastore.getDatastoreForConnection('secondary').sessionFactory].each { SessionFactory sessionFactory ->
            if (TransactionSynchronizationManager.hasResource(sessionFactory)) {
                Session session = ((SessionHolder) TransactionSynchronizationManager.unbindResource(sessionFactory)).session
                if (session.open) {
                    session.close()
                }
            }
        }
    }

    def "withSession on default datasource works with OSIV"() {
        given: "OSIV interceptor configured with the datastore"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)

        when: "OSIV preHandle is called"
        interceptor.preHandle(webRequest)

        then: "a session is bound for the default SessionFactory"
        TransactionSynchronizationManager.hasResource(datastore.sessionFactory)

        when: "withSession is called on default datasource"
        boolean sessionObtained = false
        OsivAuthor.withSession { Session s ->
            sessionObtained = s != null
        }

        then: "session is available"
        sessionObtained

        cleanup:
        interceptor.afterCompletion(webRequest, null)
    }

    def "withSession on secondary datasource works with OSIV"() {
        given: "OSIV interceptor configured with the datastore"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)

        when: "OSIV preHandle is called"
        interceptor.preHandle(webRequest)

        then: "a session is bound for both the default and secondary SessionFactory"
        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')
        TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        TransactionSynchronizationManager.hasResource(secondaryDatastore.sessionFactory)

        when: "withSession is called on secondary datasource"
        boolean sessionObtained = false
        OsivBook.secondary.withSession { Session s ->
            sessionObtained = s != null
        }

        then: "session is available without 'No Session found for current thread' error"
        sessionObtained

        cleanup:
        interceptor.afterCompletion(webRequest, null)
    }

    def "afterCompletion cleans up sessions for all datasources"() {
        given: "OSIV interceptor with preHandle already called"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)
        interceptor.preHandle(webRequest)

        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')

        when: "afterCompletion is called"
        interceptor.afterCompletion(webRequest, null)

        then: "sessions are unbound for all datasources"
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        !TransactionSynchronizationManager.hasResource(secondaryDatastore.sessionFactory)
    }

    def "OSIV skips secondary datasource if session already bound"() {
        given: "a pre-bound session for the secondary datasource"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = webRequest()

        def secondaryDatastore = datastore.getDatastoreForConnection('secondary')
        SessionFactory secondarySf = secondaryDatastore.sessionFactory
        Session preBoundSession = secondarySf.openSession()
        SessionHolder preBoundHolder = new SessionHolder(preBoundSession)
        TransactionSynchronizationManager.bindResource(secondarySf, preBoundHolder)

        when: "OSIV preHandle is called"
        interceptor.preHandle(webRequest)

        then: "the pre-bound session is preserved (not replaced)"
        def currentHolder = TransactionSynchronizationManager.getResource(secondarySf)
        currentHolder.is(preBoundHolder)

        when: "the request completes"
        interceptor.afterCompletion(webRequest, null)

        then: "the pre-bound session is left bound and open"
        TransactionSynchronizationManager.getResource(secondarySf).is(preBoundHolder)
        preBoundSession.open

        cleanup:
        if (TransactionSynchronizationManager.hasResource(secondarySf)) {
            TransactionSynchronizationManager.unbindResource(secondarySf)
        }
        preBoundSession.close()
    }

    def "a request whose session for a later datasource fails to open releases the sessions it opened before"() {
        given: "the secondary datasource opens sessions, and the tertiary one, after it, cannot"
        HibernateDatastore broken = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config + [
                'hibernate.generate_statistics': 'true',
                'dataSource.url': 'jdbc:h2:mem:brokenDefaultDb;LOCK_TIMEOUT=10000',
                'dataSources.secondary': [url: 'jdbc:h2:mem:healthySecondaryDb;LOCK_TIMEOUT=10000'],
                'dataSources.tertiary': [url: 'jdbc:h2:mem:brokenTertiaryDb;LOCK_TIMEOUT=10000'],
        ]), BrokenOsivBook, BrokenOsivAuthor)
        SessionFactory defaultSf = broken.sessionFactory
        SessionFactory secondarySf = broken.getDatastoreForConnection('secondary').sessionFactory
        broken.getDatastoreForConnection('tertiary').sessionFactory.close()
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(broken)

        when:
        interceptor.preHandle(webRequest())

        then: "the failure is thrown, and the sessions opened before it are unbound and closed"
        thrown(IllegalStateException)
        secondarySf.statistics.sessionOpenCount == 1
        secondarySf.statistics.sessionCloseCount == 1
        !TransactionSynchronizationManager.hasResource(secondarySf)
        defaultSf.statistics.sessionCloseCount == defaultSf.statistics.sessionOpenCount
        !TransactionSynchronizationManager.hasResource(defaultSf)

        cleanup:
        [defaultSf, secondarySf].each { SessionFactory sf ->
            if (sf != null && TransactionSynchronizationManager.hasResource(sf)) {
                TransactionSynchronizationManager.unbindResource(sf)
            }
        }
        try {
            broken?.close()
        }
        catch (Exception ignored) {
            // its tertiary session factory is already closed
        }
    }

    def "a request whose session for a later datasource fails to be set up closes it, and the sessions opened before"() {
        given: "after the secondary datasource, one whose new session refuses its flush mode"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        SessionFactory secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        Session refusingSession = Mock(Session) {
            isOpen() >> true
            setHibernateFlushMode(_) >> { throw new IllegalStateException('flush mode refused') }
        }
        SessionFactory refusingSf = Stub(SessionFactory) {
            openSession() >> refusingSession
        }
        HibernateDatastore refusingDatastore = Stub(HibernateDatastore) {
            getSessionFactory() >> refusingSf
            getDefaultFlushModeName() >> FlushMode.AUTO.name()
        }
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, [
                secondary: datastore.getDatastoreForConnection('secondary'),
                refusing: refusingDatastore,
        ]))

        when:
        interceptor.preHandle(webRequest())

        then: "the failure is thrown, the session that refused is closed, and the earlier sessions are released"
        thrown(IllegalStateException)
        1 * refusingSession.close()
        !TransactionSynchronizationManager.hasResource(refusingSf)
        !TransactionSynchronizationManager.hasResource(secondarySf)
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
    }

    def "a request whose default session refuses the flush mode the parent class sets closes it"() {
        given: "a default session factory whose new session refuses the MANUAL flush mode"
        Session refusingSession = Mock(Session) {
            isOpen() >> true
            setHibernateFlushMode(FlushMode.MANUAL) >> { throw new HibernateException('flush mode refused') }
        }
        SessionFactory refusingSf = Stub(SessionFactory) {
            openSession() >> refusingSession
        }
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(refusingSf, [:]))

        when:
        interceptor.preHandle(webRequest())

        then: "the failure is thrown as the parent class throws it, and the session is closed and not bound"
        DataAccessResourceFailureException failure = thrown()
        failure.cause instanceof HibernateException
        1 * refusingSession.close()
        !TransactionSynchronizationManager.hasResource(refusingSf)
    }

    def "a request whose default session fails to be set up closes it"() {
        given: "an interceptor whose flush mode the new default session refuses"
        Session refused = null
        def interceptor = new GrailsOpenSessionInViewInterceptor() {
            @Override
            protected void applyFlushMode(Session session) {
                refused = session
                throw new IllegalStateException('flush mode refused')
            }
        }
        interceptor.setHibernateDatastore(datastore)

        when:
        interceptor.preHandle(webRequest())

        then: "the failure is thrown, and the default session is closed and not bound"
        thrown(IllegalStateException)
        refused != null
        !refused.open
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
    }

    def "an include uses the sessions of the request for every datasource and leaves them open"() {
        given: "the request has opened its sessions"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = webRequest()
        SessionFactory secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        interceptor.preHandle(webRequest)
        Session defaultSession = boundSession(datastore.sessionFactory)
        Session secondarySession = boundSession(secondarySf)

        when: "an include within it starts and completes"
        interceptor.preHandle(webRequest)
        interceptor.postHandle(webRequest, null)
        interceptor.afterCompletion(webRequest, null)

        then: "the sessions of the request are still bound and open"
        boundSession(datastore.sessionFactory).is(defaultSession)
        boundSession(secondarySf).is(secondarySession)
        secondarySession.open

        when: "the request completes"
        interceptor.afterCompletion(webRequest, null)

        then: "they are unbound and closed"
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        !TransactionSynchronizationManager.hasResource(secondarySf)
        !secondarySession.open
    }

    def "a request that goes asynchronous without an asynchronous context closes the sessions it opened at once and leaves the thread without them"() {
        given: "the request has opened its sessions"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = webRequest()
        SessionFactory secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        interceptor.preHandle(webRequest)
        Session defaultSession = boundSession(datastore.sessionFactory)
        Session secondarySession = boundSession(secondarySf)

        when: "the request goes asynchronous"
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "no session is bound to the thread any more"
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        !TransactionSynchronizationManager.hasResource(secondarySf)

        and: "the session of the other datasource is closed"
        !secondarySession.open

        cleanup:
        defaultSession?.close()
    }

    def "a request that goes asynchronous flushes another datasource's session under AUTO when updates outside a transaction are allowed"() {
        given: "a datastore that flushes in AUTO mode and allows updates outside a transaction, with a book on its secondary datasource"
        HibernateDatastore autoFlush = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config + [
                'hibernate.flush.mode': 'AUTO',
                'hibernate.allow_update_outside_transaction': 'true',
                'dataSource.url': 'jdbc:h2:mem:outsideTxDefaultDb;LOCK_TIMEOUT=10000',
                'dataSources.secondary': [url: 'jdbc:h2:mem:outsideTxSecondaryDb;LOCK_TIMEOUT=10000'],
        ]), OutsideTransactionBook)
        SessionFactory secondarySf = autoFlush.getDatastoreForConnection('secondary').sessionFactory
        Serializable bookId = OutsideTransactionBook.withNewTransaction { new OutsideTransactionBook(title: 'Before').save(flush: true).id }

        and: "a request that changes it without a flush"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(autoFlush)
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        Session secondarySession = boundSession(secondarySf)
        secondarySession.get(OutsideTransactionBook, bookId).title = 'After'

        when: "the request goes asynchronous"
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the change is written, as postHandle would write it, and the session is closed at once, without an asynchronous context"
        titleOf(secondarySf, OutsideTransactionBook, bookId) == 'After'
        !secondarySession.open
        !TransactionSynchronizationManager.hasResource(secondarySf)

        cleanup:
        autoFlush?.close()
    }

    def "a request that goes asynchronous does not flush another datasource's session under AUTO when Hibernate would refuse it"() {
        given: "a datastore that flushes in AUTO mode with the default setting, which refuses a flush outside a transaction"
        HibernateDatastore autoFlush = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config + [
                'hibernate.flush.mode': 'AUTO',
                'dataSource.url': 'jdbc:h2:mem:insideTxDefaultDb;LOCK_TIMEOUT=10000',
                'dataSources.secondary': [url: 'jdbc:h2:mem:insideTxSecondaryDb;LOCK_TIMEOUT=10000'],
        ]), InsideTransactionBook)
        SessionFactory secondarySf = autoFlush.getDatastoreForConnection('secondary').sessionFactory
        Serializable bookId = InsideTransactionBook.withNewTransaction { new InsideTransactionBook(title: 'Before').save(flush: true).id }

        and: "a request that changes it without a flush"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(autoFlush)
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        Session secondarySession = boundSession(secondarySf)
        secondarySession.get(InsideTransactionBook, bookId).title = 'After'

        when: "the request goes asynchronous"
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "nothing is flushed or thrown, and the session is closed at once, without an asynchronous context"
        notThrown(Exception)
        titleOf(secondarySf, InsideTransactionBook, bookId) == 'Before'
        !secondarySession.open
        !TransactionSynchronizationManager.hasResource(secondarySf)

        cleanup:
        autoFlush?.close()
    }

    def "a request that goes asynchronous releases every session it opened when a flush there throws an Error"() {
        given: "a datastore that flushes in AUTO mode and allows updates outside a transaction, with a book on each of two other datasources"
        HibernateDatastore autoFlush = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config + [
                'hibernate.flush.mode': 'AUTO',
                'hibernate.allow_update_outside_transaction': 'true',
                'dataSource.url': 'jdbc:h2:mem:refusedDefaultDb;LOCK_TIMEOUT=10000',
                'dataSources.secondary': [url: 'jdbc:h2:mem:refusedSecondaryDb;LOCK_TIMEOUT=10000'],
                'dataSources.third': [url: 'jdbc:h2:mem:refusedThirdDb;LOCK_TIMEOUT=10000'],
        ]), RefusedSecondaryBook, RefusedThirdBook)
        SessionFactory secondarySf = autoFlush.getDatastoreForConnection('secondary').sessionFactory
        SessionFactory thirdSf = autoFlush.getDatastoreForConnection('third').sessionFactory
        Serializable secondaryId = RefusedSecondaryBook.withNewTransaction { new RefusedSecondaryBook(title: 'Before').save(flush: true).id }
        Serializable thirdId = RefusedThirdBook.withNewTransaction { new RefusedThirdBook(title: 'Before').save(flush: true).id }

        and: "a request, made asynchronous by Grails, that changes both so that each one's beforeUpdate fails its assert"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(autoFlush)
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        Session defaultSession = boundSession(autoFlush.sessionFactory)
        Session secondarySession = boundSession(secondarySf)
        Session thirdSession = boundSession(thirdSf)
        secondarySession.get(RefusedSecondaryBook, secondaryId).title = 'Refused'
        thirdSession.get(RefusedThirdBook, thirdId).title = 'Refused'
        webRequest.setAttribute('org.grails.ASYNC_STARTED', true, WebRequest.SCOPE_REQUEST)

        when: "the request goes asynchronous"
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "every session the request opened is unbound and, without an asynchronous context, closed at once"
        AssertionError failure = thrown()
        [autoFlush.sessionFactory, secondarySf, thirdSf].every { !TransactionSynchronizationManager.hasResource(it) }
        !defaultSession.open
        !secondarySession.open
        !thirdSession.open

        and: "the first failure is thrown, with the second attached"
        failure.suppressed.length == 1
        failure.suppressed[0] instanceof AssertionError

        cleanup:
        autoFlush?.close()
    }

    def "a request that Grails makes asynchronous without an asynchronous context closes its default session at once too"() {
        given: "the request has opened its sessions"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        Session defaultSession = boundSession(datastore.sessionFactory)

        when: "the action returns a promise or calls startAsync(), which Spring's asynchronous interceptors never see"
        webRequest.setAttribute('org.grails.ASYNC_STARTED', true, WebRequest.SCOPE_REQUEST)
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the default session is unbound and closed"
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        !defaultSession.open

        cleanup:
        if (defaultSession?.open) {
            defaultSession.close()
        }
    }

    def "a request that Grails makes asynchronous closes its sessions when the asynchronous request completes"() {
        given: 'a servlet request that has opened its sessions'
        var webRequest = new ServletWebRequest(new MockHttpServletRequest(asyncSupported: true), new MockHttpServletResponse())
        final interceptor = new GrailsOpenSessionInViewInterceptor(hibernateDatastore: datastore).tap {
            preHandle(webRequest)
        }
        final secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        final defaultSession = boundSession(datastore.sessionFactory)
        final secondarySession = boundSession(secondarySf)

        when: 'the action returns a promise, so that the request goes asynchronous'
        final asyncContext = webRequest.request.startAsync()
        webRequest.setAttribute('org.grails.ASYNC_STARTED', true, WebRequest.SCOPE_REQUEST)
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: 'the sessions are unbound, and open for the work the request continues with'
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        !TransactionSynchronizationManager.hasResource(secondarySf)
        defaultSession.open
        secondarySession.open

        when: 'the asynchronous request completes'
        asyncContext.complete()

        then: 'they are closed'
        !defaultSession.open
        !secondarySession.open

        cleanup:
        [defaultSession, secondarySession].each { session ->
            if (session?.open) {
                session.close()
            }
        }
    }

    def "a request that Spring makes asynchronous closes its sessions when the asynchronous request completes without a dispatch"() {
        given: 'a servlet request that has opened its sessions'
        var webRequest = new ServletWebRequest(new MockHttpServletRequest(asyncSupported: true), new MockHttpServletResponse())
        final interceptor = new GrailsOpenSessionInViewInterceptor(hibernateDatastore: datastore).tap {
            preHandle(webRequest)
        }
        final secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        final defaultSession = boundSession(datastore.sessionFactory)
        final secondarySession = boundSession(secondarySf)

        when: 'the request goes asynchronous without Grails marking it, as for a Callable, a DeferredResult or a task the action does not return'
        final asyncContext = webRequest.request.startAsync()
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: 'the sessions are unbound, and open for the work the request continues with'
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        !TransactionSynchronizationManager.hasResource(secondarySf)
        defaultSession.open
        secondarySession.open

        when: 'the asynchronous request completes without the dispatch in which Spring closes the default session'
        asyncContext.complete()

        then: 'both sessions are closed'
        !secondarySession.open
        !defaultSession.open

        cleanup:
        [defaultSession, secondarySession].each { session ->
            if (session?.open) {
                session.close()
            }
        }
    }

    def "a request that Spring makes asynchronous without an asynchronous context leaves its default session open for Spring to close"() {
        given: "the request has opened its sessions"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        Session defaultSession = boundSession(datastore.sessionFactory)

        when: "the action returns a Callable or DeferredResult, whose processing rebinds the session and closes it"
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the default session is unbound, and left open"
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        defaultSession.open

        cleanup:
        defaultSession?.close()
    }

    def "a session of another datasource bound before the request stays bound and open when the request goes asynchronous"() {
        given: "a pre-bound session for the secondary datasource"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = webRequest()
        SessionFactory secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        Session preBoundSession = secondarySf.openSession()
        SessionHolder preBoundHolder = new SessionHolder(preBoundSession)
        TransactionSynchronizationManager.bindResource(secondarySf, preBoundHolder)
        interceptor.preHandle(webRequest)
        Session defaultSession = boundSession(datastore.sessionFactory)

        when: "the request goes asynchronous"
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the pre-bound session is left bound and open"
        TransactionSynchronizationManager.getResource(secondarySf).is(preBoundHolder)
        preBoundSession.open

        cleanup:
        if (TransactionSynchronizationManager.hasResource(secondarySf)) {
            TransactionSynchronizationManager.unbindResource(secondarySf)
        }
        preBoundSession.close()
        defaultSession?.close()
    }

    def "a request that goes asynchronous without an asynchronous context closes the session of another datasource at once, without a flush"() {
        given: "a book of the other datasource"
        OsivBook.withTransaction { new OsivBook(title: 'Before').save() }

        and: "a request that changes it without a flush"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = webRequest()
        SessionFactory secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        interceptor.preHandle(webRequest)
        Session defaultSession = boundSession(datastore.sessionFactory)
        Session secondarySession = boundSession(secondarySf)
        OsivBook book = OsivBook.findByTitle('Before')
        book.title = 'Changed before going asynchronous'

        expect: "the default flush mode, under which the change is not written when an action returns either"
        secondarySession.hibernateFlushMode == FlushMode.COMMIT

        when: "the request goes asynchronous"
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the change is not written, and the session is closed"
        OsivBook.withNewSession { OsivBook.findByTitle('Before') } != null
        OsivBook.withNewSession { OsivBook.findByTitle('Changed before going asynchronous') } == null
        !TransactionSynchronizationManager.hasResource(secondarySf)
        !secondarySession.open

        cleanup:
        defaultSession?.close()
        OsivBook.withTransaction { OsivBook.list()*.delete(flush: true) }
    }

    def "CRUD operations work on secondary datasource with OSIV"() {
        given: "OSIV interceptor configured and preHandle called"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = Mock(WebRequest)
        interceptor.preHandle(webRequest)

        when: "data is saved to secondary datasource within a transaction"
        OsivBook book = OsivBook.withTransaction {
            new OsivBook(title: "Test Book").save(flush: true)
            OsivBook.first()
        }

        then: "the book is saved successfully"
        book != null
        book.title == "Test Book"

        when: "data is read from secondary datasource using withSession"
        int count = 0
        OsivBook.secondary.withSession { Session s ->
            count = OsivBook.count()
        }

        then: "the count is correct"
        count == 1

        cleanup:
        OsivBook.withTransaction {
            OsivBook.list()*.delete(flush: true)
        }
        interceptor.afterCompletion(webRequest, null)
    }

    private static String titleOf(SessionFactory sessionFactory, Class type, Serializable id) {
        Session session = sessionFactory.openSession()
        try {
            session.get(type, id).title
        }
        finally {
            session.close()
        }
    }

    private WebRequest webRequest() {
        Map<String, Object> attributes = [:]
        Stub(WebRequest) {
            getAttribute(_ as String, WebRequest.SCOPE_REQUEST) >> { String name, int scope -> attributes[name] }
            setAttribute(_ as String, _, WebRequest.SCOPE_REQUEST) >> { String name, Object value, int scope -> attributes[name] = value }
            removeAttribute(_ as String, WebRequest.SCOPE_REQUEST) >> { String name, int scope -> attributes.remove(name) }
        }
    }

    /**
     * A datastore whose default connection uses the given session factory, and whose other connections are the given
     * datastores, in the given order.
     */
    private HibernateDatastore datastoreWith(SessionFactory defaultSessionFactory, Map<String, HibernateDatastore> others) {
        List<ConnectionSource> sources = ([ConnectionSource.DEFAULT] + others.keySet()).collect { String name ->
            Stub(ConnectionSource) {
                getName() >> name
            }
        }
        ConnectionSources connectionSources = Stub(ConnectionSources) {
            getAllConnectionSources() >> sources
        }
        Stub(HibernateDatastore) {
            getSessionFactory() >> defaultSessionFactory
            getDefaultFlushModeName() >> FlushMode.COMMIT.name()
            getConnectionSources() >> connectionSources
            getDatastoreForConnection(_ as String) >> { String name -> others[name] }
        }
    }

    private static Session boundSession(SessionFactory sessionFactory) {
        ((SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory))?.session
    }
}

@Entity
class OsivBook {
    String title
    Date dateCreated
    Date lastUpdated

    static mapping = {
        datasource 'secondary'
    }
}

@Entity
class OsivAuthor {
    String name
}

@Entity
class OutsideTransactionBook {
    String title

    static mapping = {
        datasource 'secondary'
    }
}

@Entity
class InsideTransactionBook {
    String title

    static mapping = {
        datasource 'secondary'
    }
}

@Entity
class RefusedSecondaryBook {
    String title

    def beforeUpdate() {
        assert title != 'Refused'
    }

    static mapping = {
        datasource 'secondary'
    }
}

@Entity
class RefusedThirdBook {
    String title

    def beforeUpdate() {
        assert title != 'Refused'
    }

    static mapping = {
        datasource 'third'
    }
}

@Entity
class BrokenOsivBook {
    String title

    static mapping = {
        datasource 'secondary'
    }
}

@Entity
class BrokenOsivAuthor {
    String name
}
