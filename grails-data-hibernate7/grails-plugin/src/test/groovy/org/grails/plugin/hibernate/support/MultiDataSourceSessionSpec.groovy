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
import groovy.transform.AutoImplement
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.FlushMode
import org.hibernate.HibernateException
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.transaction.support.TransactionSynchronizationManager
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

    def "a request whose session for a later datasource fails to be set up with #failure.class.simpleName closes it, and the sessions opened before"() {
        given: "after the secondary datasource, one whose new session refuses its flush mode"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        SessionFactory secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        // Groovy classes, not Spock mocks or coerced maps, whose proxies would wrap a checked exception
        FakeSession refusingSession = new FakeSession(failure: failure)
        SessionFactory refusingSf = new FakeSessionFactory(session: refusingSession)
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, secondary: datastore.getDatastoreForConnection('secondary').sessionFactory, refusing: refusingSf))

        when:
        interceptor.preHandle(webRequest())

        then: "the failure is thrown, the session that refused is closed, and the earlier sessions are released"
        def thrownFailure = thrown(Throwable)
        thrownFailure.is(failure)
        refusingSession.closeCount == 1
        !TransactionSynchronizationManager.hasResource(refusingSf)
        !TransactionSynchronizationManager.hasResource(secondarySf)
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)

        where: "a checked exception too, which Groovy code can throw without declaring it"
        failure << [new IllegalStateException('flush mode refused'), new IOException('flush mode refused')]
    }

    def "a request whose session for a later datasource fails to open with a checked exception releases the sessions opened before"() {
        given: "after the secondary datasource, one whose session factory throws a checked exception as it opens a session"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        SessionFactory secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        IOException failure = new IOException('cannot open')
        // a Groovy class, not a Spock stub or a coerced map, whose proxy would wrap a checked exception
        SessionFactory failingSf = new FakeSessionFactory(failure: failure)
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, secondary: datastore.getDatastoreForConnection('secondary').sessionFactory, failing: failingSf))

        when:
        interceptor.preHandle(webRequest())

        then: "the failure is thrown, and the earlier sessions are released"
        def thrownFailure = thrown(IOException)
        thrownFailure.is(failure)
        !TransactionSynchronizationManager.hasResource(failingSf)
        !TransactionSynchronizationManager.hasResource(secondarySf)
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
    }

    def "a request whose default session fails to be set up with #failure.class.simpleName closes it"() {
        given: "an interceptor whose flush mode the new default session refuses"
        Session refused = null
        Throwable refusal = failure
        def interceptor = new GrailsOpenSessionInViewInterceptor() {
            @Override
            protected void applyFlushMode(Session session) {
                refused = session
                throw refusal
            }
        }
        interceptor.setHibernateDatastore(datastore)

        when:
        interceptor.preHandle(webRequest())

        then: "the failure is thrown, and the default session is closed and not bound"
        def thrownFailure = thrown(Throwable)
        thrownFailure.is(failure)
        refused != null
        !refused.open
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)

        where: "a checked exception too, which a Groovy override can throw without declaring it"
        failure << [new IllegalStateException('flush mode refused'), new IOException('flush mode refused')]
    }

    def "a request whose default session refuses its flush mode as it is opened closes it, failing with #expected.simpleName"() {
        given: "a default session factory whose new session refuses any flush mode"
        Session refusing = Mock(Session) {
            isOpen() >> true
            setHibernateFlushMode(_) >> { throw failure }
        }
        SessionFactory refusingSf = Stub(SessionFactory) {
            openSession() >> refusing
        }
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastore)
        interceptor.setSessionFactory(refusingSf)

        when:
        interceptor.preHandle(webRequest())

        then: "the failure is thrown, and the session is closed and not bound"
        def thrownFailure = thrown(expected)
        thrownFailure.is(failure) || thrownFailure.cause.is(failure)
        1 * refusing.close()
        !TransactionSynchronizationManager.hasResource(refusingSf)

        where:
        failure                                          || expected
        new IllegalStateException('flush mode refused')  || IllegalStateException
        new HibernateException('flush mode refused')     || DataAccessResourceFailureException
    }

    def "a request that fails after joining one factory's session and opening another's, that prints the same, releases only the one it opened"() {
        given: "a session bound before the request, a factory that prints as its factory does, and one after them that cannot open"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        FakeSession boundBefore = new FakeSession()
        SessionFactory boundSf = new FakeSessionFactory(printed: 'sessionFactory')
        FakeSession opened = new FakeSession()
        SessionFactory lookalikeSf = new FakeSessionFactory(session: opened, printed: 'sessionFactory')
        SessionFactory failingSf = new FakeSessionFactory(failure: new IllegalStateException('cannot open'))
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, secondary: datastore.getDatastoreForConnection('secondary').sessionFactory, bound: boundSf, lookalike: lookalikeSf, failing: failingSf))
        TransactionSynchronizationManager.bindResource(boundSf, new SessionHolder(boundBefore))

        when:
        interceptor.preHandle(webRequest())

        then: "the session bound before is left bound and open, and the one the request opened is closed and unbound"
        thrown(IllegalStateException)
        boundSession(boundSf).is(boundBefore)
        boundBefore.closeCount == 0
        opened.closeCount == 1
        !TransactionSynchronizationManager.hasResource(lookalikeSf)
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)

        cleanup:
        unbindIfBound(boundSf, lookalikeSf)
    }

    def "a request that joins one factory's session and opens another's, that prints the same, closes only the one it opened"() {
        given: "a session bound before the request, and a factory that prints as its factory does"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        WebRequest webRequest = webRequest()
        FakeSession boundBefore = new FakeSession()
        SessionFactory boundSf = new FakeSessionFactory(printed: 'sessionFactory')
        FakeSession opened = new FakeSession()
        SessionFactory lookalikeSf = new FakeSessionFactory(session: opened, printed: 'sessionFactory')
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, secondary: datastore.getDatastoreForConnection('secondary').sessionFactory, bound: boundSf, lookalike: lookalikeSf))
        TransactionSynchronizationManager.bindResource(boundSf, new SessionHolder(boundBefore))

        when:
        interceptor.preHandle(webRequest)

        then: "it uses the session bound before, and opens one for the other factory"
        boundSession(boundSf).is(boundBefore)
        boundSession(lookalikeSf).is(opened)

        when:
        interceptor.afterCompletion(webRequest, null)

        then: "the session bound before is left bound and open, and the one the request opened is closed and unbound"
        boundSession(boundSf).is(boundBefore)
        boundBefore.closeCount == 0
        opened.closeCount == 1
        !TransactionSynchronizationManager.hasResource(lookalikeSf)

        cleanup:
        unbindIfBound(boundSf, lookalikeSf)
    }

    def "two interceptors whose default factories print the same each close only the default session they opened"() {
        given: "one interceptor whose default session is bound before the request, and one whose default factory prints the same"
        FakeSession boundBefore = new FakeSession()
        SessionFactory boundSf = new FakeSessionFactory(printed: 'sessionFactory')
        FakeSession opened = new FakeSession()
        SessionFactory lookalikeSf = new FakeSessionFactory(session: opened, printed: 'sessionFactory')
        def joining = new GrailsOpenSessionInViewInterceptor(sessionFactory: boundSf)
        def opening = new GrailsOpenSessionInViewInterceptor(sessionFactory: lookalikeSf)
        WebRequest webRequest = webRequest()
        TransactionSynchronizationManager.bindResource(boundSf, new SessionHolder(boundBefore))

        when: "the request passes both, and completes in the reverse order"
        joining.preHandle(webRequest)
        opening.preHandle(webRequest)
        opening.afterCompletion(webRequest, null)
        joining.afterCompletion(webRequest, null)

        then: "the session bound before is left bound and open, and the one the request opened is closed and unbound"
        boundSession(boundSf).is(boundBefore)
        boundBefore.closeCount == 0
        opened.closeCount == 1
        !TransactionSynchronizationManager.hasResource(lookalikeSf)

        cleanup:
        unbindIfBound(boundSf, lookalikeSf)
    }

    def "a request that fails after opening a session that fails to close reports both failures"() {
        given: "a session bound before the request, then a factory whose session fails to close, then one that cannot open"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        FakeSession boundBefore = new FakeSession()
        SessionFactory boundSf = new FakeSessionFactory()
        IOException closeFailure = new IOException('cannot close')
        FakeSession unclosable = new FakeSession(closeFailure: closeFailure)
        SessionFactory unclosableSf = new FakeSessionFactory(session: unclosable)
        IllegalStateException failure = new IllegalStateException('cannot open')
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, secondary: datastore.getDatastoreForConnection('secondary').sessionFactory, bound: boundSf, unclosable: unclosableSf, failing: new FakeSessionFactory(failure: failure)))
        TransactionSynchronizationManager.bindResource(boundSf, new SessionHolder(boundBefore))

        when:
        interceptor.preHandle(webRequest())

        then: "the failure to open is thrown, with the failure to close attached"
        def thrownFailure = thrown(IllegalStateException)
        thrownFailure.is(failure)
        thrownFailure.suppressed.toList() == [closeFailure]

        and: "the session bound before is left as it was, and the others are released"
        boundSession(boundSf).is(boundBefore)
        boundBefore.closeCount == 0
        unclosable.closeCount == 1
        !TransactionSynchronizationManager.hasResource(unclosableSf)
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)

        cleanup:
        unbindIfBound(boundSf, unclosableSf)
    }

    def "a request that fails after opening a default session that fails to close reports both failures"() {
        given: "a default session that fails to close, and another datasource whose factory cannot open"
        IOException closeFailure = new IOException('cannot close')
        FakeSession unclosable = new FakeSession(closeFailure: closeFailure)
        SessionFactory defaultSf = new FakeSessionFactory(session: unclosable)
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        IllegalStateException failure = new IllegalStateException('cannot open')
        interceptor.setHibernateDatastore(datastoreWith(defaultSf, failing: new FakeSessionFactory(failure: failure)))

        when:
        interceptor.preHandle(webRequest())

        then: "the failure to open is thrown, with the failure to close the default session attached"
        def thrownFailure = thrown(IllegalStateException)
        thrownFailure.is(failure)
        thrownFailure.suppressed.toList() == [closeFailure]
        unclosable.closeCount == 1
        !TransactionSynchronizationManager.hasResource(defaultSf)

        cleanup:
        unbindIfBound(defaultSf)
    }

    def "a request that fails after joining a default session bound before it leaves that session bound and open"() {
        given: "a default session bound before the request, and another datasource whose factory cannot open"
        FakeSession boundBefore = new FakeSession()
        SessionFactory defaultSf = new FakeSessionFactory()
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(defaultSf, failing: new FakeSessionFactory(failure: new IllegalStateException('cannot open'))))
        TransactionSynchronizationManager.bindResource(defaultSf, new SessionHolder(boundBefore))

        when:
        interceptor.preHandle(webRequest())

        then: "the failure is thrown, and the default session is left to whoever bound it"
        thrown(IllegalStateException)
        boundSession(defaultSf).is(boundBefore)
        boundBefore.closeCount == 0

        cleanup:
        unbindIfBound(defaultSf)
    }

    def "a request whose session for a later datasource fails to be set up and then to close reports both failures"() {
        given: "after the secondary datasource, one whose new session refuses its flush mode, then fails to close"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        IllegalStateException failure = new IllegalStateException('flush mode refused')
        FakeSession refusing = new FakeSession(failure: failure, closeFailure: closeFailure ?: failure)
        SessionFactory refusingSf = new FakeSessionFactory(session: refusing)
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, secondary: datastore.getDatastoreForConnection('secondary').sessionFactory, refusing: refusingSf))

        when:
        interceptor.preHandle(webRequest())

        then: "the failure to set it up is thrown, with a different failure to close attached"
        def thrownFailure = thrown(IllegalStateException)
        thrownFailure.is(failure)
        thrownFailure.suppressed.toList() == (closeFailure ? [closeFailure] : [])
        refusing.closeCount == 1
        !TransactionSynchronizationManager.hasResource(refusingSf)
        !TransactionSynchronizationManager.hasResource(datastore.getDatastoreForConnection('secondary').sessionFactory)
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)

        where: "the session fails to close with another failure, or with the very failure it refused its flush mode with"
        closeFailure << [new IOException('cannot close'), null]
    }

    def "a request whose default session fails to be set up with #setupFailure.class.simpleName and then to close reports both failures"() {
        given: "a default session that refuses its flush mode, then fails to close"
        IOException closeFailure = new IOException('cannot close')
        FakeSession refusing = new FakeSession(failure: setupFailure, closeFailure: closeFailure)
        SessionFactory defaultSf = new FakeSessionFactory(session: refusing)
        def interceptor = new GrailsOpenSessionInViewInterceptor(sessionFactory: defaultSf)

        when:
        interceptor.preHandle(webRequest())

        then: "the failure to set it up is thrown, with the failure to close attached"
        def thrownFailure = thrown(expected)
        (thrownFailure.is(setupFailure) || thrownFailure.cause.is(setupFailure))
        thrownFailure.suppressed.toList() == [closeFailure]
        refusing.closeCount == 1
        !TransactionSynchronizationManager.hasResource(defaultSf)

        where:
        setupFailure                                     || expected
        new IllegalStateException('flush mode refused')  || IllegalStateException
        new HibernateException('flush mode refused')     || DataAccessResourceFailureException
    }

    def "a request that completes with a session that fails to close does not throw, and leaves it unbound"() {
        given: "another datasource whose session fails to close"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        WebRequest webRequest = webRequest()
        FakeSession unclosable = new FakeSession(closeFailure: new IOException('cannot close'))
        SessionFactory unclosableSf = new FakeSessionFactory(session: unclosable)
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, secondary: datastore.getDatastoreForConnection('secondary').sessionFactory, unclosable: unclosableSf))
        interceptor.preHandle(webRequest)

        when:
        interceptor.afterCompletion(webRequest, null)

        then: "with no failure to attach it to, the failure to close is logged, as the parent class does"
        noExceptionThrown()
        unclosable.closeCount == 1
        !TransactionSynchronizationManager.hasResource(unclosableSf)
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)

        cleanup:
        unbindIfBound(unclosableSf)
    }

    def "a request that goes asynchronous with a failing flush attaches the failures to close its sessions to it"() {
        given: "a datastore that flushes in AUTO mode and allows updates outside a transaction, with a book on its secondary datasource"
        HibernateDatastore autoFlush = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config + [
                'hibernate.flush.mode': 'AUTO',
                'hibernate.allow_update_outside_transaction': 'true',
                'dataSource.url': 'jdbc:h2:mem:unclosableDefaultDb;LOCK_TIMEOUT=10000',
                'dataSources.secondary': [url: 'jdbc:h2:mem:unclosableSecondaryDb;LOCK_TIMEOUT=10000'],
        ]), RefusedSecondaryBook)
        SessionFactory secondarySf = autoFlush.getDatastoreForConnection('secondary').sessionFactory
        Serializable secondaryId = RefusedSecondaryBook.withNewTransaction { new RefusedSecondaryBook(title: 'Before').save(flush: true).id }

        and: "a default session and another datasource's session that both fail to close"
        IOException defaultCloseFailure = new IOException('cannot close the default session')
        FakeSession unclosableDefault = new FakeSession(closeFailure: defaultCloseFailure)
        SessionFactory defaultSf = new FakeSessionFactory(session: unclosableDefault)
        IOException otherCloseFailure = new IOException('cannot close another session')
        FakeSession unclosableOther = new FakeSession(closeFailure: otherCloseFailure)
        SessionFactory unclosableSf = new FakeSessionFactory(session: unclosableOther)

        and: "a request, made asynchronous by Grails, whose change to the secondary book fails its flush"
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(defaultSf, secondary: secondarySf, unclosable: unclosableSf))
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        boundSession(secondarySf).get(RefusedSecondaryBook, secondaryId).title = 'Refused'
        webRequest.setAttribute('org.grails.ASYNC_STARTED', true, WebRequest.SCOPE_REQUEST)

        when: "the request goes asynchronous"
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the flush failure is thrown, with both failures to close attached"
        AssertionError failure = thrown()
        failure.suppressed.toList() as Set == [otherCloseFailure, defaultCloseFailure] as Set

        and: "every session is unbound and closed"
        [defaultSf, secondarySf, unclosableSf].every { !TransactionSynchronizationManager.hasResource(it) }
        unclosableDefault.closeCount == 1
        unclosableOther.closeCount == 1

        cleanup:
        unbindIfBound(defaultSf, secondarySf, unclosableSf)
        autoFlush?.close()
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

    def "a request that goes asynchronous closes the sessions it opened and leaves the thread without them"() {
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

        then: "the change is written, as postHandle would write it, and the session is closed"
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

        then: "nothing is flushed or thrown, and the session is closed"
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

        then: "every session the request opened is unbound and closed"
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

    def "a request that Grails makes asynchronous closes its default session too"() {
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

    def "a request that Spring makes asynchronous leaves its default session open for Spring to close"() {
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

    def "a request that goes asynchronous closes the session of another datasource without a flush"() {
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

    /**
     * A datastore whose default session factory and other datasources, in the order given, are the ones given, so that
     * a fake factory reaches the interceptor through setHibernateDatastore, as a real datasource's does. Each other
     * datasource flushes in AUTO mode.
     */
    private HibernateDatastore datastoreWith(Map<String, SessionFactory> others, SessionFactory defaultFactory) {
        List<ConnectionSource> stubbedSources = [Stub(ConnectionSource) { getName() >> ConnectionSource.DEFAULT }]
        Map<String, HibernateDatastore> stubbedChildren = [:]
        others.each { String childName, SessionFactory childFactory ->
            stubbedSources << Stub(ConnectionSource) { getName() >> childName }
            stubbedChildren[childName] = Stub(HibernateDatastore) {
                isOsivReadOnly() >> false
                getDefaultFlushModeName() >> 'AUTO'
                getSessionFactory() >> childFactory
            }
        }
        ConnectionSources stubbedConnectionSources = Stub(ConnectionSources) { getAllConnectionSources() >> stubbedSources }
        Stub(HibernateDatastore) {
            isOsivReadOnly() >> false
            getDefaultFlushModeName() >> 'COMMIT'
            getSessionFactory() >> defaultFactory
            getConnectionSources() >> stubbedConnectionSources
            getDatastoreForConnection(_ as String) >> { String childName -> stubbedChildren[childName] }
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

    private static void unbindIfBound(SessionFactory... sessionFactories) {
        sessionFactories.each { SessionFactory sessionFactory ->
            if (TransactionSynchronizationManager.hasResource(sessionFactory)) {
                TransactionSynchronizationManager.unbindResource(sessionFactory)
            }
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

/**
 * A session that never flushes, counts its closes, refuses its flush mode with the failure it is given, and fails to
 * close with the close failure it is given, a checked exception too.
 */
@AutoImplement
class FakeSession implements Session {
    Throwable failure
    Throwable closeFailure
    int closeCount

    @Override
    boolean isOpen() {
        closeCount == 0
    }

    @Override
    void setHibernateFlushMode(FlushMode flushMode) {
        if (failure != null) {
            throw failure
        }
    }

    @Override
    FlushMode getHibernateFlushMode() {
        FlushMode.MANUAL
    }

    @Override
    void close() {
        closeCount++
        if (closeFailure != null) {
            throw closeFailure
        }
    }
}

/**
 * A session factory that hands out the session it is given, or throws the failure it is given, a checked exception too,
 * and prints as it is told to, as two factories can: {@code Object.toString()} is not unique.
 */
@AutoImplement
class FakeSessionFactory implements SessionFactory {
    Session session
    Throwable failure
    String printed

    @Override
    String toString() {
        printed ?: super.toString()
    }

    @Override
    Session openSession() {
        if (failure != null) {
            throw failure
        }
        session
    }
}
