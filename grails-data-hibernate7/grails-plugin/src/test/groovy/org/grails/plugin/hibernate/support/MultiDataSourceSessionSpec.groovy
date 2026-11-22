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

import java.lang.reflect.UndeclaredThrowableException
import java.util.concurrent.Callable

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
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.engine.spi.SessionImplementor
import org.grails.orm.hibernate.support.hibernate7.LocalSessionFactoryBuilder
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.springframework.core.InfrastructureProxy
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils
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

        then: "the failure is thrown as it is, and the default session is closed and not bound"
        def thrownFailure = thrown(Throwable)
        thrownFailure.is(failure)
        refused != null
        !refused.open
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)

        where: "a checked exception too, which a Groovy override can throw without declaring it, and a HibernateException, which is not wrapped"
        failure << [new IllegalStateException('flush mode refused'), new IOException('flush mode refused'), new HibernateException('flush mode refused')]
    }

    def "an interceptor whose applyFlushMode leaves the flush mode alone opens its default session in MANUAL mode, as the parent class does"() {
        given: "an override that does not set the flush mode"
        def interceptor = new GrailsOpenSessionInViewInterceptor() {
            @Override
            protected void applyFlushMode(Session session) {
            }
        }
        interceptor.setHibernateDatastore(datastore)
        WebRequest webRequest = webRequest()

        when:
        interceptor.preHandle(webRequest)

        then:
        boundSession(datastore.sessionFactory).hibernateFlushMode == FlushMode.MANUAL

        cleanup:
        interceptor.afterCompletion(webRequest, null)
    }

    def "a session bound for the default factory while its new session is set up is left bound, and the new one is closed"() {
        given: "a default factory whose opening of a session binds another one for it first"
        FakeSession boundMeanwhile = new FakeSession()
        FakeSession opened = new FakeSession()
        FakeSessionFactory defaultSf = new FakeSessionFactory()
        defaultSf.onOpen = { ->
            TransactionSynchronizationManager.bindResource(defaultSf, new SessionHolder(boundMeanwhile))
            opened
        }
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith([:], defaultSf))

        when:
        interceptor.preHandle(webRequest())

        then: "the request fails, and the session bound meanwhile stays bound and open"
        thrown(IllegalStateException)
        boundSession(defaultSf).is(boundMeanwhile)
        boundMeanwhile.closeCount == 0
        opened.closeCount == 1

        cleanup:
        unbindIfBound(defaultSf)
    }

    def "a session bound for another datasource while its new session is set up is left bound, and the new one is closed"() {
        given: "after the secondary datasource, one whose opening of a session binds another one for it first"
        FakeSession boundMeanwhile = new FakeSession()
        FakeSession opened = new FakeSession()
        FakeSessionFactory meddlingSf = new FakeSessionFactory()
        meddlingSf.onOpen = { ->
            TransactionSynchronizationManager.bindResource(meddlingSf, new SessionHolder(boundMeanwhile))
            opened
        }
        SessionFactory secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, secondary: secondarySf, meddling: meddlingSf))

        when:
        interceptor.preHandle(webRequest())

        then: "the request fails, the session bound meanwhile stays bound and open, and the others are released"
        thrown(IllegalStateException)
        boundSession(meddlingSf).is(boundMeanwhile)
        boundMeanwhile.closeCount == 0
        opened.closeCount == 1
        !TransactionSynchronizationManager.hasResource(secondarySf)
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)

        cleanup:
        unbindIfBound(meddlingSf)
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

    def "a request that goes asynchronous with two flushes failing with the same exception throws it once, and releases every session"() {
        given: "two other datasources whose sessions fail their flush with one and the same exception"
        IllegalStateException failure = new IllegalStateException('cannot flush')
        FakeSession first = new FakeSession(flushFailure: failure)
        FakeSession second = new FakeSession(flushFailure: failure)
        SessionFactory firstSf = new FakeSessionFactory(session: first)
        SessionFactory secondSf = new FakeSessionFactory(session: second)
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, first: firstSf, second: secondSf))
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        webRequest.setAttribute('org.grails.ASYNC_STARTED', true, WebRequest.SCOPE_REQUEST)

        when:
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the failure is thrown, not attached to itself, and every session is unbound and closed"
        def thrownFailure = thrown(IllegalStateException)
        thrownFailure.is(failure)
        thrownFailure.suppressed.length == 0
        first.closeCount == 1
        second.closeCount == 1
        [firstSf, secondSf, datastore.sessionFactory].every { !TransactionSynchronizationManager.hasResource(it) }

        cleanup:
        unbindIfBound(firstSf, secondSf)
    }

    def "a request that goes asynchronous releases every session when a flush takes its own session off the thread and fails"() {
        given: "two other datasources, the later one's flush unbinding its own session before it fails"
        IllegalStateException failure = new IllegalStateException('cannot flush')
        FakeSession first = new FakeSession()
        SessionFactory firstSf = new FakeSessionFactory(session: first)
        FakeSessionFactory secondSf = new FakeSessionFactory()
        FakeSession second = new FakeSession(flushFailure: failure, onFlush: { -> TransactionSynchronizationManager.unbindResource(secondSf) })
        secondSf.session = second
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, first: firstSf, second: secondSf))
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        webRequest.setAttribute('org.grails.ASYNC_STARTED', true, WebRequest.SCOPE_REQUEST)

        when:
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the flush failure is thrown, and every session is unbound and closed"
        def thrownFailure = thrown(IllegalStateException)
        thrownFailure.is(failure)
        first.closeCount == 1
        second.closeCount == 1
        [firstSf, secondSf, datastore.sessionFactory].every { !TransactionSynchronizationManager.hasResource(it) }

        cleanup:
        unbindIfBound(firstSf, secondSf)
    }

    def "a request that goes asynchronous throws the flush failure when the default session cannot be released after it"() {
        given: "another datasource whose flush takes the default session off the thread before it fails"
        IllegalStateException failure = new IllegalStateException('cannot flush')
        FakeSession defaultSession = new FakeSession()
        SessionFactory defaultSf = new FakeSessionFactory(session: defaultSession)
        FakeSession other = new FakeSession(flushFailure: failure, onFlush: { -> TransactionSynchronizationManager.unbindResource(defaultSf) })
        SessionFactory otherSf = new FakeSessionFactory(session: other)
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(defaultSf, other: otherSf))
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)

        when: "the request goes asynchronous, and the parent class finds no default session to unbind"
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the flush failure is thrown, with the failure to release the default session attached"
        def thrownFailure = thrown(IllegalStateException)
        thrownFailure.is(failure)
        thrownFailure.suppressed.length == 1
        thrownFailure.suppressed[0] instanceof IllegalStateException
        other.closeCount == 1
        !TransactionSynchronizationManager.hasResource(otherSf)

        cleanup:
        unbindIfBound(defaultSf, otherSf)
    }

    def "a request that goes asynchronous with a checked flush failure throws it wrapped, with every failure to close attached to the wrapper"() {
        given: "another datasource whose flush fails with a checked exception, and sessions that fail to close"
        IOException failure = new IOException('cannot flush')
        IOException defaultCloseFailure = new IOException('cannot close the default session')
        IOException otherCloseFailure = new IOException('cannot close another session')
        FakeSession defaultSession = new FakeSession(closeFailure: defaultCloseFailure)
        SessionFactory defaultSf = new FakeSessionFactory(session: defaultSession)
        FakeSession unclosable = new FakeSession(closeFailure: otherCloseFailure)
        SessionFactory unclosableSf = new FakeSessionFactory(session: unclosable)
        FakeSession failing = new FakeSession(flushFailure: failure)
        SessionFactory failingSf = new FakeSessionFactory(session: failing)
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(defaultSf, unclosable: unclosableSf, failing: failingSf))
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        webRequest.setAttribute('org.grails.ASYNC_STARTED', true, WebRequest.SCOPE_REQUEST)

        when:
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the wrapper that is thrown carries every failure to close"
        def thrownFailure = thrown(UndeclaredThrowableException)
        thrownFailure.cause.is(failure)
        thrownFailure.suppressed.toList() as Set == [otherCloseFailure, defaultCloseFailure] as Set
        [defaultSf, unclosableSf, failingSf].every { !TransactionSynchronizationManager.hasResource(it) }

        cleanup:
        unbindIfBound(defaultSf, unclosableSf, failingSf)
    }

    def "a request that fails in its handler gets the failures to close its sessions attached to that failure"() {
        given: "a default session and another datasource's session that both fail to close"
        IOException defaultCloseFailure = new IOException('cannot close the default session')
        IOException otherCloseFailure = new IOException('cannot close another session')
        SessionFactory defaultSf = new FakeSessionFactory(session: new FakeSession(closeFailure: defaultCloseFailure))
        SessionFactory otherSf = new FakeSessionFactory(session: new FakeSession(closeFailure: otherCloseFailure))
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(defaultSf, other: otherSf))
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        IllegalStateException handlerFailure = new IllegalStateException('the action failed')

        when: "Spring completes the request with the failure it throws on afterwards"
        interceptor.afterCompletion(webRequest, handlerFailure)

        then: "both failures to close are attached to it, and the sessions are unbound"
        noExceptionThrown()
        handlerFailure.suppressed.toList() as Set == [otherCloseFailure, defaultCloseFailure] as Set
        !TransactionSynchronizationManager.hasResource(defaultSf)
        !TransactionSynchronizationManager.hasResource(otherSf)

        cleanup:
        unbindIfBound(defaultSf, otherSf)
    }

    def "a failure to close that cannot be attached to the failure being thrown is logged"() {
        given: "another datasource whose session fails to close, then one that cannot open, with a failure that keeps no suppressed exceptions"
        SessionFactory unclosableSf = new FakeSessionFactory(session: new FakeSession(closeFailure: new IOException('cannot close the unclosable session')))
        UnsuppressibleFailure failure = new UnsuppressibleFailure('cannot open')
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, unclosable: unclosableSf, failing: new FakeSessionFactory(failure: failure)))
        PrintStream systemErr = System.err
        ByteArrayOutputStream logged = new ByteArrayOutputStream()
        System.err = new PrintStream(logged, true)

        when:
        interceptor.preHandle(webRequest())

        then: "the failure is thrown, and the failure to close is logged instead of attached"
        def thrownFailure = thrown(UnsuppressibleFailure)
        thrownFailure.is(failure)
        logged.toString().contains('cannot close the unclosable session')
        !TransactionSynchronizationManager.hasResource(unclosableSf)

        cleanup:
        System.err = systemErr
        unbindIfBound(unclosableSf)
    }

    def "postHandle throws a flush failure of two datasources' sessions once when it is one and the same exception"() {
        given: "two other datasources whose sessions fail their flush with one and the same exception"
        IllegalStateException failure = new IllegalStateException('cannot flush')
        SessionFactory firstSf = new FakeSessionFactory(session: new FakeSession(flushFailure: failure))
        SessionFactory secondSf = new FakeSessionFactory(session: new FakeSession(flushFailure: failure))
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, first: firstSf, second: secondSf))
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)

        when:
        interceptor.postHandle(webRequest, null)

        then: "the failure is thrown, not attached to itself"
        def thrownFailure = thrown(IllegalStateException)
        thrownFailure.is(failure)
        thrownFailure.suppressed.length == 0

        cleanup:
        interceptor.afterCompletion(webRequest, null)
        unbindIfBound(firstSf, secondSf)
    }

    def "an include that fails in preHandle gives up its part in the default session, so the request that opened it closes it"() {
        given: "a request that opened its sessions, and another datasource whose factory cannot open a second one"
        int opens = 0
        FakeSessionFactory flakySf = new FakeSessionFactory()
        flakySf.onOpen = { ->
            if (opens++ > 0) {
                throw new IllegalStateException('cannot open')
            }
            new FakeSession()
        }
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(datastore.sessionFactory, flaky: flakySf))
        WebRequest webRequest = webRequest()
        interceptor.preHandle(webRequest)
        Session defaultSession = boundSession(datastore.sessionFactory)

        and: "includes within it, the last of which finds the other datasource's session gone and fails to open one"
        includes.times { interceptor.preHandle(webRequest) }
        unbindIfBound(flakySf)

        when:
        interceptor.preHandle(webRequest)

        then: "the include fails, and the default session stays bound and open"
        thrown(IllegalStateException)
        boundSession(datastore.sessionFactory).is(defaultSession)
        defaultSession.open

        when: "the includes that succeeded complete"
        includes.times { interceptor.afterCompletion(webRequest, null) }

        then: "the default session still belongs to the request"
        boundSession(datastore.sessionFactory).is(defaultSession)
        defaultSession.open

        when: "the request completes"
        interceptor.afterCompletion(webRequest, null)

        then: "it unbinds and closes the default session"
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        !defaultSession.open

        cleanup:
        unbindIfBound(flakySf)

        where: "the failed include is the only one, or one of two"
        includes << [0, 1]
    }

    def "a request that Spring redispatches after its Callable rebinds its default session through another interceptor using the same factory"() {
        given: "a request that supports asynchronous processing, whose Callable runs when the test runs it"
        MockHttpServletRequest servletRequest = new MockHttpServletRequest()
        servletRequest.asyncSupported = true
        MockHttpServletResponse servletResponse = new MockHttpServletResponse()
        ServletWebRequest webRequest = new ServletWebRequest(servletRequest, servletResponse)
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest)
        asyncManager.asyncWebRequest = new StandardServletAsyncWebRequest(servletRequest, servletResponse)
        List<Runnable> tasks = []
        asyncManager.taskExecutor = { Runnable task -> tasks << task } as AsyncTaskExecutor

        and: "one interceptor for the first dispatch, and another for the default factory, for the redispatch"
        def dispatching = new GrailsOpenSessionInViewInterceptor(sessionFactory: datastore.sessionFactory)
        def redispatching = new GrailsOpenSessionInViewInterceptor(sessionFactory: datastore.sessionFactory)

        when: "the action returns a Callable, which Spring runs, then redispatches the request"
        dispatching.preHandle(webRequest)
        Session opened = boundSession(datastore.sessionFactory)
        asyncManager.startCallableProcessing({ -> 'done' } as Callable)
        dispatching.afterConcurrentHandlingStarted(webRequest)
        tasks*.run()
        redispatching.preHandle(webRequest)

        then: "the redispatch uses the session the first dispatch opened"
        asyncManager.hasConcurrentResult()
        boundSession(datastore.sessionFactory).is(opened)

        cleanup:
        redispatching.afterCompletion(webRequest, null)
        unbindIfBound(datastore.sessionFactory)
        if (opened?.open) {
            opened.close()
        }
    }

    def "a request that Spring redispatches after its Callable rebinds its default session when one interceptor has the bootstrap proxy of the factory and the other #redispatchVia"() {
        given: "a session factory bootstrapped in the background, whose proxy and target are one resource to Spring"
        def bootstrapExecutor = new SimpleAsyncTaskExecutor()
        SessionFactory proxy = new LocalSessionFactoryBuilder(new DriverManagerDataSource('jdbc:h2:mem:bootstrapProxyDb;LOCK_TIMEOUT=10000'))
                .setProperty('hibernate.dialect', H2Dialect.name)
                .buildSessionFactory(bootstrapExecutor)
        SessionFactory target = (SessionFactory) ((InfrastructureProxy) proxy).wrappedObject
        Map<String, SessionFactory> factories = [proxy: proxy, target: target]

        and: "a request that supports asynchronous processing, whose Callable runs when the test runs it"
        MockHttpServletRequest servletRequest = new MockHttpServletRequest()
        servletRequest.asyncSupported = true
        MockHttpServletResponse servletResponse = new MockHttpServletResponse()
        ServletWebRequest webRequest = new ServletWebRequest(servletRequest, servletResponse)
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest)
        asyncManager.asyncWebRequest = new StandardServletAsyncWebRequest(servletRequest, servletResponse)
        List<Runnable> tasks = []
        asyncManager.taskExecutor = { Runnable task -> tasks << task } as AsyncTaskExecutor
        def dispatching = new GrailsOpenSessionInViewInterceptor(sessionFactory: factories[dispatchVia])
        def redispatching = new GrailsOpenSessionInViewInterceptor(sessionFactory: factories[redispatchVia])

        when: "the action returns a Callable, which Spring runs, then redispatches the request"
        dispatching.preHandle(webRequest)
        Session opened = boundSession(target)
        asyncManager.startCallableProcessing({ -> 'done' } as Callable)
        dispatching.afterConcurrentHandlingStarted(webRequest)
        tasks*.run()
        redispatching.preHandle(webRequest)

        then: "the redispatch uses the session the first dispatch opened, still open"
        boundSession(target).is(opened)
        opened.open

        when: "the redispatch completes"
        redispatching.afterCompletion(webRequest, null)

        then: "that session is unbound and closed"
        !TransactionSynchronizationManager.hasResource(target)
        !opened.open

        cleanup:
        unbindIfBound(target)
        if (opened?.open) {
            opened.close()
        }
        target?.close()

        where:
        dispatchVia | redispatchVia
        'proxy'     | 'target'
        'target'    | 'proxy'
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

    def "a request that goes asynchronous with a failing flush keeps its sessions open until the asynchronous request completes, then closes every one"() {
        given: "a servlet request whose default session, another datasource's failing flush and a third's failing close are open"
        IllegalStateException failure = new IllegalStateException('cannot flush')
        FakeSession defaultSession = new FakeSession()
        FakeSession failing = new FakeSession(flushFailure: failure)
        FakeSession unclosable = new FakeSession(closeFailure: new IOException('cannot close'))
        SessionFactory defaultSf = new FakeSessionFactory(session: defaultSession)
        SessionFactory failingSf = new FakeSessionFactory(session: failing)
        SessionFactory unclosableSf = new FakeSessionFactory(session: unclosable)
        def interceptor = new GrailsOpenSessionInViewInterceptor()
        interceptor.setHibernateDatastore(datastoreWith(defaultSf, unclosable: unclosableSf, failing: failingSf))
        ServletWebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest(asyncSupported: true), new MockHttpServletResponse())
        interceptor.preHandle(webRequest)

        when: "the action returns a promise, and the flush as the request goes asynchronous fails"
        def asyncContext = webRequest.request.startAsync()
        webRequest.setAttribute('org.grails.ASYNC_STARTED', true, WebRequest.SCOPE_REQUEST)
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: "the flush failure is thrown, and the sessions are unbound but open for the work the request continues with"
        def thrownFailure = thrown(IllegalStateException)
        thrownFailure.is(failure)
        [defaultSf, failingSf, unclosableSf].every { !TransactionSynchronizationManager.hasResource(it) }
        [defaultSession, failing, unclosable].every { it.closeCount == 0 }

        when: "the asynchronous request completes"
        asyncContext.complete()

        then: "every session is closed, the one that fails to close too, and nothing is thrown at the container"
        noExceptionThrown()
        [defaultSession, failing, unclosable].every { it.closeCount == 1 }

        cleanup:
        unbindIfBound(defaultSf, failingSf, unclosableSf)
    }

    def "a request that Spring makes asynchronous closes the session of another datasource when the asynchronous request completes, and leaves its default session to Spring"() {
        given: 'a servlet request that has opened its sessions'
        var webRequest = new ServletWebRequest(new MockHttpServletRequest(asyncSupported: true), new MockHttpServletResponse())
        final interceptor = new GrailsOpenSessionInViewInterceptor(hibernateDatastore: datastore).tap {
            preHandle(webRequest)
        }
        final secondarySf = datastore.getDatastoreForConnection('secondary').sessionFactory
        final defaultSession = boundSession(datastore.sessionFactory)
        final secondarySession = boundSession(secondarySf)

        when: 'the action returns a Callable or DeferredResult, for which Spring starts the asynchronous request'
        final asyncContext = webRequest.request.startAsync()
        interceptor.afterConcurrentHandlingStarted(webRequest)

        then: 'the sessions are unbound, and open for the work the request continues with'
        !TransactionSynchronizationManager.hasResource(datastore.sessionFactory)
        !TransactionSynchronizationManager.hasResource(secondarySf)
        defaultSession.open
        secondarySession.open

        when: 'the asynchronous request completes'
        asyncContext.complete()

        then: 'the session of the other datasource is closed'
        !secondarySession.open

        and: 'the default session is left to the interceptors Spring registers for the Callable or DeferredResult'
        defaultSession.open

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
 * A session inside a transaction that counts its closes, keeps the flush mode it is given unless it refuses it with the
 * failure it is given, runs {@code onFlush} and then fails with the flush failure it is given when flushed, and fails to
 * close with the close failure it is given: a checked exception too, for each.
 */
@AutoImplement
class FakeSession implements SessionImplementor {
    Throwable failure
    Throwable flushFailure
    Throwable closeFailure
    Closure<?> onFlush
    FlushMode flushMode = FlushMode.MANUAL
    int closeCount

    @Override
    boolean isOpen() {
        closeCount == 0
    }

    @Override
    boolean isTransactionInProgress() {
        true
    }

    @Override
    SessionFactoryImplementor getSessionFactory() {
        null
    }

    @Override
    void setHibernateFlushMode(FlushMode flushMode) {
        if (failure != null) {
            throw failure
        }
        this.flushMode = flushMode
    }

    @Override
    FlushMode getHibernateFlushMode() {
        flushMode
    }

    @Override
    void flush() {
        onFlush?.call()
        if (flushFailure != null) {
            throw flushFailure
        }
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
 * and prints as it is told to, as two factories can: {@code Object.toString()} is not unique. When given
 * {@code onOpen}, opening a session runs it instead.
 */
@AutoImplement
class FakeSessionFactory implements SessionFactory {
    Session session
    Throwable failure
    String printed
    Closure<Session> onOpen

    @Override
    String toString() {
        printed ?: super.toString()
    }

    @Override
    Session openSession() {
        if (onOpen != null) {
            return onOpen()
        }
        if (failure != null) {
            throw failure
        }
        session
    }
}

/** A failure that keeps no suppressed exceptions, as a Throwable built with suppression disabled does not. */
class UnsuppressibleFailure extends RuntimeException {

    UnsuppressibleFailure(String message) {
        super(message, null, false, true)
    }
}
