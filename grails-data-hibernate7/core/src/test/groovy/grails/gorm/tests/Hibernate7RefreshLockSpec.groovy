/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package grails.gorm.tests

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import jakarta.persistence.TransactionRequiredException

import org.hibernate.FlushMode
import org.hibernate.Hibernate
import org.hibernate.LockMode
import org.hibernate.Session

import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.support.TransactionSynchronizationManager

import grails.gorm.annotation.Entity
import grails.gorm.api.GormAllOperations
import grails.gorm.dirty.checking.DirtyCheck

class Hibernate7RefreshLockSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(Hibernate7RefreshLockBook, Hibernate7RefreshLockRoutedBook,
            Hibernate7RefreshLockNonversionedBook, Hibernate7RefreshLockEmbeddedBook)
        // AUTO leaves an explicitly selected session flush mode intact during template calls.
        manager.grailsConfig['hibernate.flush.mode'] = 'AUTO'
        // ConfigObject needs the parent map for named connection discovery.
        manager.grailsConfig.dataSources.secondary = [
            url: 'jdbc:h2:mem:hibernate7RefreshLockSecondary;LOCK_TIMEOUT=10000'
        ]
    }

    void 'ordinary lock preserves pending changes and takes a write lock'() {
        given:
        def book = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate7RefreshLockBook.withSession { it.clear() }
        book = Hibernate7RefreshLockBook.get(book.id)
        book.title = 'pending'

        when:
        def result = book.lock()

        then:
        result.is(book)
        book.title == 'pending'
        book.version == 0
        Hibernate7RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
        }
    }

    void 'refresh(lock: true) discards pending changes under #flushMode flush mode'() {
        given:
        def book = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate7RefreshLockBook.withSession { it.clear() }
        book = Hibernate7RefreshLockBook.get(book.id)

        expect:
        Hibernate7RefreshLockBook.withSession { Session session ->
            def previousFlushMode = session.hibernateFlushMode
            try {
                session.hibernateFlushMode = flushMode
                book.title = 'pending'
                assert book.isDirty('title')

                assert book.refresh(lock: true).is(book)
                assert book.title == 'original'
                assert book.version == 0
                assert !book.isDirty('title')
                assert book.dirtyPropertyNames.isEmpty()
                assert session.hibernateFlushMode == flushMode
                assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE

                session.flush()
                session.clear()
                def reloaded = Hibernate7RefreshLockBook.get(book.id)
                assert reloaded.title == 'original'
                assert reloaded.version == 0
                true
            } finally {
                session.hibernateFlushMode = previousFlushMode
            }
        }

        where:
        flushMode << [FlushMode.AUTO, FlushMode.COMMIT]
    }

    void 'refresh(lock: true) discards embedded edits without a version bump and tracks subsequent edits under #flushMode'() {
        given:
        def book = new Hibernate7RefreshLockEmbeddedBook(
            title: 'original',
            details: new Hibernate7RefreshLockDetails(summary: 'original summary', language: 'English')
        ).save(flush: true, failOnError: true)
        Hibernate7RefreshLockEmbeddedBook.withSession { it.clear() }
        book = Hibernate7RefreshLockEmbeddedBook.get(book.id)

        expect:
        Hibernate7RefreshLockEmbeddedBook.withSession { Session session ->
            def previousFlushMode = session.hibernateFlushMode
            try {
                session.hibernateFlushMode = flushMode
                book.title = 'pending title'
                book.details.summary = 'pending summary'
                assert book.isDirty('title')
                assert book.details.hasChanged('summary')

                assert book.refresh(lock: true).is(book)
                assert book.title == 'original'
                assert book.details.summary == 'original summary'
                assert book.details.language == 'English'
                assert book.version == 0
                assert !book.isDirty('title')
                assert book.dirtyPropertyNames.isEmpty()
                assert !book.details.hasChanged('summary')
                assert book.details.listDirtyPropertyNames().isEmpty()
                assert session.hibernateFlushMode == flushMode
                assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE

                session.flush()
                assert book.version == 0

                // An embedded-only edit must still be tracked on the same refreshed instance.
                book.details.summary = 'saved summary'
                assert book.save(flush: true, failOnError: true).is(book)
                assert book.version == 1
                session.clear()
                def reloaded = Hibernate7RefreshLockEmbeddedBook.get(book.id)
                assert reloaded.version == 1
                assert reloaded.title == 'original'
                assert reloaded.details.summary == 'saved summary'
                assert reloaded.details.language == 'English'
                true
            } finally {
                session.hibernateFlushMode = previousFlushMode
            }
        }

        where:
        flushMode << [FlushMode.AUTO, FlushMode.COMMIT]
    }

    void 'refresh(lock: true) reloads a committed stale version and allows a subsequent save (proxy: #useProxy)'() {
        given:
        Long id = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()
        GormAllOperations<Hibernate7RefreshLockBook> operations = Hibernate7RefreshLockBook.'default'

        when:
        Hibernate7RefreshLockBook.withNewSession { Session session ->
            Hibernate7RefreshLockBook.withTransaction {
                def book = useProxy ? operations.load(id) : operations.get(id)
                assert Hibernate.isInitialized(book) == !useProxy
                if (!useProxy) {
                    assert book.version == 0
                }

                executor.submit({
                    Hibernate7RefreshLockBook.withNewSession {
                        Hibernate7RefreshLockBook.withTransaction {
                            def competingBook = Hibernate7RefreshLockBook.get(id)
                            competingBook.title = 'competing commit'
                            competingBook.save(flush: true, failOnError: true)
                            assert competingBook.version == 1
                        }
                    }
                } as Callable).get(10, TimeUnit.SECONDS)

                if (!useProxy) {
                    // An already initialized instance keeps its stale state and its pending edit.
                    assert book.title == 'original'
                    assert book.version == 0
                    book.title = 'stale pending edit'
                }
                // Nothing has touched the proxy yet, so the locked refresh is what initializes it.
                assert Hibernate.isInitialized(book) == !useProxy

                def result = useProxy ? operations.refresh(book, [lock: true]) : book.refresh(lock: true)
                assert result.is(book)
                assert Hibernate.isInitialized(book)
                assert book.title == 'competing commit'
                assert book.version == 1
                assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE

                book.title = 'saved after refresh'
                def saved = useProxy ? operations.save(book, [flush: true, failOnError: true]) : book.save(flush: true, failOnError: true)
                assert saved.is(book)
                assert book.version == 2
            }
        }

        then:
        Hibernate7RefreshLockBook.withNewSession {
            def book = Hibernate7RefreshLockBook.get(id)
            book.title == 'saved after refresh' && book.version == 2
        }

        cleanup:
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)

        where:
        useProxy << [false, true]
    }

    void 'public operations refresh(lock: true) initializes a proxy and takes a write lock on the same instance'() {
        given:
        Long id = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        GormAllOperations<Hibernate7RefreshLockBook> operations = Hibernate7RefreshLockBook.'default'
        def executor = Executors.newSingleThreadExecutor()

        expect:
        operations.withNewSession { Session session ->
            operations.withTransaction {
                def proxy = operations.load(id)
                assert !Hibernate.isInitialized(proxy)

                executor.submit({
                    operations.withNewSession {
                        operations.withTransaction {
                            def competingBook = operations.get(id)
                            competingBook.title = 'competing commit'
                            competingBook.save(flush: true, failOnError: true)
                            assert competingBook.version == 1
                        }
                    }
                } as Callable).get(10, TimeUnit.SECONDS)

                assert !Hibernate.isInitialized(proxy)
                def result = operations.refresh(proxy, [lock: true])
                assert result.is(proxy)
                // getCurrentLockMode initializes a proxy, so check initialization first.
                assert Hibernate.isInitialized(proxy)
                assert session.getCurrentLockMode(proxy) == LockMode.PESSIMISTIC_WRITE
                assert proxy.title == 'competing commit'
                assert proxy.version == 1
                true
            }
        }

        cleanup:
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)
    }

    void 'public operations refresh(lock: true) rejects a missing transaction without initializing a proxy'() {
        given:
        Long id = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        GormAllOperations<Hibernate7RefreshLockBook> operations = Hibernate7RefreshLockBook.'default'
        Hibernate7RefreshLockBook proxy

        when:
        operations.withNewSession { Session session ->
            assert session.isOpen()
            assert manager.sessionFactory.currentSession.is(session)
            assert !TransactionSynchronizationManager.isActualTransactionActive()
            assert !session.transaction.isActive()
            proxy = operations.load(id)
            assert !Hibernate.isInitialized(proxy)
            operations.refresh(proxy, [lock: true])
        }

        then:
        def exception = thrown(TransactionRequiredException)
        exception.message == 'An active transaction is required.'
        !Hibernate.isInitialized(proxy)
    }

    void 'refresh(lock: true) discards changes and permits saving a nonversioned entity'() {
        given:
        def book = new Hibernate7RefreshLockNonversionedBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate7RefreshLockNonversionedBook.withSession { it.clear() }
        book = Hibernate7RefreshLockNonversionedBook.get(book.id)
        book.title = 'pending'

        when:
        def result = book.refresh(lock: true)

        then:
        result.is(book)
        book.title == 'original'
        Hibernate7RefreshLockNonversionedBook.withSession { Session session ->
            session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
        }

        when:
        book.title = 'saved after refresh'
        def saved = book.save(flush: true, failOnError: true)
        Hibernate7RefreshLockNonversionedBook.withSession { it.clear() }

        then:
        saved.is(book)
        Hibernate7RefreshLockNonversionedBook.get(book.id).title == 'saved after refresh'
    }

    void 'ordinary lock rejects a committed stale version without refreshing'() {
        given:
        Long id = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()

        when:
        Hibernate7RefreshLockBook.withNewSession {
            Hibernate7RefreshLockBook.withTransaction { status ->
                def book = Hibernate7RefreshLockBook.get(id)
                assert book.version == 0

                executor.submit({
                    Hibernate7RefreshLockBook.withNewSession {
                        Hibernate7RefreshLockBook.withTransaction {
                            def competingBook = Hibernate7RefreshLockBook.get(id)
                            competingBook.title = 'competing commit'
                            competingBook.save(flush: true, failOnError: true)
                            assert competingBook.version == 1
                        }
                    }
                } as Callable).get(10, TimeUnit.SECONDS)

                assert book.title == 'original'
                assert book.version == 0
                book.title = 'stale pending edit'
                // If lock succeeds, roll back instead of letting a commit-time flush satisfy the test.
                try {
                    book.lock()
                } finally {
                    status.setRollbackOnly()
                }
            }
        }

        then:
        thrown(OptimisticLockingFailureException)
        Hibernate7RefreshLockBook.withNewSession {
            def book = Hibernate7RefreshLockBook.get(id)
            book.title == 'competing commit' && book.version == 1
        }

        cleanup:
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)
    }

    void 'refresh(lock: true) requires a transaction even with an open bound session and managed entity'() {
        given:
        Long id = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        assert !TransactionSynchronizationManager.isActualTransactionActive()
        Hibernate7RefreshLockBook book

        when:
        Hibernate7RefreshLockBook.withNewSession { Session session ->
            assert session.isOpen()
            assert TransactionSynchronizationManager.hasResource(manager.sessionFactory)
            assert manager.sessionFactory.currentSession.is(session)
            assert !TransactionSynchronizationManager.isActualTransactionActive()
            assert !session.transaction.isActive()
            book = Hibernate7RefreshLockBook.get(id)
            assert session.contains(book)
            book.title = 'pending'

            try {
                book.refresh(lock: true)
            } finally {
                assert session.isOpen()
                assert !session.transaction.isActive()
            }
        }

        then:
        def exception = thrown(TransactionRequiredException)
        exception.message == 'An active transaction is required.'
        book.title == 'pending'
        book.version == 0
        Hibernate7RefreshLockBook.withNewSession {
            def reloaded = Hibernate7RefreshLockBook.get(id)
            reloaded.title == 'original' && reloaded.version == 0
        }
    }

    void 'refresh(lock: true) uses the named connection rather than the default database'() {
        given:
        def defaultBook = new Hibernate7RefreshLockRoutedBook(title: 'default').save(flush: true, failOnError: true)

        expect:
        Hibernate7RefreshLockRoutedBook.secondary.withNewSession { Session session ->
            Hibernate7RefreshLockRoutedBook.secondary.withTransaction {
                assert session.transaction.isActive()
                def url = session.doReturningWork { connection -> connection.metaData.URL }
                assert url == 'jdbc:h2:mem:hibernate7RefreshLockSecondary'
                def book = new Hibernate7RefreshLockRoutedBook(title: 'secondary')
                book.secondary.save(flush: true, failOnError: true)
                Long secondaryId = book.id
                // Each database generates identity independently, so the databases are told apart by
                // what the default connection holds rather than by assuming the ids coincide.
                Hibernate7RefreshLockRoutedBook.withSession {
                    assert Hibernate7RefreshLockRoutedBook.get(secondaryId)?.title != 'secondary'
                    assert Hibernate7RefreshLockRoutedBook.findByTitle('secondary') == null
                }
                session.clear()
                book = Hibernate7RefreshLockRoutedBook.secondary.get(secondaryId)
                assert session.contains(book)
                book.title = 'pending'

                assert book.secondary.refresh(lock: true).is(book)
                assert book.title == 'secondary'
                assert book.version == 0
                assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
                session.flush()
                session.clear()
                assert Hibernate7RefreshLockRoutedBook.secondary.get(secondaryId).title == 'secondary'
                true
            }
        }
        defaultBook.title == 'default'
        Hibernate7RefreshLockRoutedBook.withSession { Session session ->
            // Reload the default row to prove the named connection work never reached it.
            session.flush()
            session.clear()
            def reloaded = Hibernate7RefreshLockRoutedBook.get(defaultBook.id)
            reloaded.title == 'default' && reloaded.version == 0
        }
    }

    void 'refresh(lock: true) requires a named connection transaction even when the default transaction is active'() {
        given:
        Long id = Hibernate7RefreshLockRoutedBook.secondary.withTransaction {
            new Hibernate7RefreshLockRoutedBook(title: 'secondary').secondary.save(flush: true, failOnError: true).id
        }
        Hibernate7RefreshLockRoutedBook book

        when:
        Hibernate7RefreshLockRoutedBook.secondary.withNewSession { Session session ->
            book = Hibernate7RefreshLockRoutedBook.secondary.get(id)
            assert session.isOpen()
            assert session.contains(book)
            assert !session.transaction.isActive()
            Hibernate7RefreshLockRoutedBook.withSession { Session defaultSession ->
                assert defaultSession.transaction.isActive()
            }
            book.title = 'pending'
            book.secondary.refresh(lock: true)
        }

        then:
        def exception = thrown(TransactionRequiredException)
        exception.message == 'An active transaction is required.'
        book.title == 'pending'
        book.version == 0
    }

    void 'refresh(lock: true) waits for a competing commit and holds the write lock until transaction completion'() {
        given:
        Long id = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newFixedThreadPool(3)
        def loaded = new CountDownLatch(1)
        def competingUpdate = new CountDownLatch(1)
        def allowCompetingCommit = new CountDownLatch(1)
        def refreshStarted = new CountDownLatch(1)
        def refreshed = new CountDownLatch(1)
        def allowRefreshCommit = new CountDownLatch(1)
        def contenderStarted = new CountDownLatch(1)
        def contenderLocked = new CountDownLatch(1)

        when:
        def refreshing = executor.submit({
            Hibernate7RefreshLockBook.withNewSession { Session session ->
                Hibernate7RefreshLockBook.withTransaction {
                    def book = Hibernate7RefreshLockBook.get(id)
                    assert book.version == 0
                    book.title = 'pending edit'
                    loaded.countDown()
                    assert competingUpdate.await(10, TimeUnit.SECONDS)
                    refreshStarted.countDown()
                    assert book.refresh(lock: true).is(book)
                    assert book.title == 'competing commit'
                    assert book.version == 1
                    assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
                    refreshed.countDown()
                    assert allowRefreshCommit.await(10, TimeUnit.SECONDS)
                }
            }
        } as Callable)
        def competing = executor.submit({
            Hibernate7RefreshLockBook.withNewSession {
                Hibernate7RefreshLockBook.withTransaction {
                    assert loaded.await(10, TimeUnit.SECONDS)
                    def book = Hibernate7RefreshLockBook.get(id)
                    book.title = 'competing commit'
                    book.save(flush: true, failOnError: true)
                    competingUpdate.countDown()
                    assert allowCompetingCommit.await(10, TimeUnit.SECONDS)
                }
            }
        } as Callable)

        then: 'refresh cannot finish while the competing transaction owns the row lock'
        refreshStarted.await(10, TimeUnit.SECONDS)
        !refreshed.await(200, TimeUnit.MILLISECONDS)
        !refreshing.isDone()

        when:
        allowCompetingCommit.countDown()
        competing.get(10, TimeUnit.SECONDS)

        then: 'refresh observes the newly committed state while acquiring its write lock'
        refreshed.await(10, TimeUnit.SECONDS)

        when:
        def contender = executor.submit({
            Hibernate7RefreshLockBook.withNewSession {
                Hibernate7RefreshLockBook.withTransaction {
                    contenderStarted.countDown()
                    def book = Hibernate7RefreshLockBook.lock(id)
                    contenderLocked.countDown()
                    assert book.title == 'competing commit'
                    book.title = 'last commit'
                    book.save(flush: true, failOnError: true)
                }
            }
        } as Callable)

        then: 'another writer cannot acquire the row until the refreshing transaction ends'
        contenderStarted.await(10, TimeUnit.SECONDS)
        !contenderLocked.await(200, TimeUnit.MILLISECONDS)
        !contender.isDone()

        when:
        allowRefreshCommit.countDown()
        refreshing.get(10, TimeUnit.SECONDS)
        contender.get(10, TimeUnit.SECONDS)

        then:
        contenderLocked.count == 0
        Hibernate7RefreshLockBook.withNewSession {
            def book = Hibernate7RefreshLockBook.get(id)
            book.title == 'last commit' && book.version == 2
        }

        cleanup:
        allowCompetingCommit?.countDown()
        allowRefreshCommit?.countDown()
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)
    }

    void 'static lock(id, refresh: true) reloads a committed stale version of the managed instance and allows a subsequent save'() {
        given:
        Long id = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()

        when:
        Hibernate7RefreshLockBook.withNewSession { Session session ->
            Hibernate7RefreshLockBook.withTransaction {
                def book = Hibernate7RefreshLockBook.get(id)
                assert book.version == 0

                executor.submit({
                    Hibernate7RefreshLockBook.withNewSession {
                        Hibernate7RefreshLockBook.withTransaction {
                            def competingBook = Hibernate7RefreshLockBook.get(id)
                            competingBook.title = 'competing commit'
                            competingBook.save(flush: true, failOnError: true)
                            assert competingBook.version == 1
                        }
                    }
                } as Callable).get(10, TimeUnit.SECONDS)

                assert book.title == 'original'
                assert book.version == 0
                book.title = 'stale pending edit'

                def locked = Hibernate7RefreshLockBook.lock(id, refresh: true)
                assert locked.is(book)
                assert book.title == 'competing commit'
                assert book.version == 1
                assert !book.isDirty('title')
                assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE

                book.title = 'saved after locked refresh'
                assert book.save(flush: true, failOnError: true).is(book)
                assert book.version == 2
            }
        }

        then:
        Hibernate7RefreshLockBook.withNewSession {
            def book = Hibernate7RefreshLockBook.get(id)
            book.title == 'saved after locked refresh' && book.version == 2
        }

        cleanup:
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)
    }

    void 'static lock(id, refresh: true) loads and locks an instance that is not in the session'() {
        given:
        Long id = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        Hibernate7RefreshLockBook.withSession { it.clear() }

        when:
        def book = Hibernate7RefreshLockBook.lock(id, refresh: true)

        then:
        book != null
        Hibernate.isInitialized(book)
        book.title == 'original'
        book.version == 0
        Hibernate7RefreshLockBook.withSession { Session session ->
            session.contains(book) && session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
        }
    }

    void 'static lock(id, refresh: true) returns null for an unknown identifier'() {
        expect:
        Hibernate7RefreshLockBook.lock(-1L, refresh: true) == null
    }

    void 'static lock(id, args) without a refresh request locks the managed instance and preserves pending changes (#description)'() {
        given:
        def book = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate7RefreshLockBook.withSession { it.clear() }
        book = Hibernate7RefreshLockBook.get(book.id)
        book.title = 'pending'

        when:
        def result = Hibernate7RefreshLockBook.lock(args, book.id)

        then:
        result.is(book)
        book.title == 'pending'
        book.version == 0
        Hibernate7RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
        }

        where:
        description      | args
        'empty map'      | [:]
        'refresh: false' | [refresh: false]
    }

    void 'static lock(id, refresh: true) requires an active transaction'() {
        given:
        Long id = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null

        when:
        Hibernate7RefreshLockBook.withNewSession { Session session ->
            assert !session.getTransaction().isActive()
            Hibernate7RefreshLockBook.lock(id, refresh: true)
        }

        then:
        def exception = thrown(TransactionRequiredException)
        exception.message == 'An active transaction is required.'
    }

    void 'refresh with arguments that do not request a lock reloads state without a write lock (#description)'() {
        given:
        def book = new Hibernate7RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate7RefreshLockBook.withSession { it.clear() }
        book = Hibernate7RefreshLockBook.get(book.id)
        book.title = 'pending'

        when:
        def result = book.refresh(args)

        then:
        result.is(book)
        book.title == 'original'
        book.version == 0
        Hibernate7RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) != LockMode.PESSIMISTIC_WRITE
        }

        where:
        description   | args
        'empty map'   | [:]
        'lock: false' | [lock: false]
    }
}

@Entity
class Hibernate7RefreshLockBook {
    Long id
    Long version
    String title
}

@Entity
class Hibernate7RefreshLockRoutedBook {
    Long id
    Long version
    String title

    static mapping = {
        datasource 'ALL'
    }
}

@Entity
class Hibernate7RefreshLockNonversionedBook {
    Long id
    String title

    static mapping = {
        version false
    }
}

@Entity
class Hibernate7RefreshLockEmbeddedBook {
    Long id
    Long version
    String title
    Hibernate7RefreshLockDetails details

    static embedded = ['details']
}

@DirtyCheck
class Hibernate7RefreshLockDetails {
    String summary
    String language
}
