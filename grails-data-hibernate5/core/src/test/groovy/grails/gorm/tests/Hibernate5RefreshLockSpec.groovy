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

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLTimeoutException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import jakarta.persistence.LockModeType
import jakarta.persistence.TransactionRequiredException

import org.hibernate.FlushMode
import org.hibernate.Hibernate
import org.hibernate.LockMode
import org.hibernate.Session

import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.support.TransactionSynchronizationManager

import grails.gorm.annotation.Entity
import grails.gorm.dirty.checking.DirtyCheck

class Hibernate5RefreshLockSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(Hibernate5RefreshLockBook, Hibernate5RefreshLockRoutedBook,
                Hibernate5RefreshLockNonversionedBook, Hibernate5RefreshLockEmbeddedBook,
                Hibernate5RefreshLockCascadeParent, Hibernate5RefreshLockCascadeChild,
                Hibernate5RefreshLockCollectionParent, Hibernate5RefreshLockEmbeddedOwner, Hibernate5RefreshLockJoinedRoot, Hibernate5RefreshLockJoinedSub,
                Hibernate5RefreshLockUnionRoot, Hibernate5RefreshLockUnionSub)
        // Let the template preserve the session flush mode instead of downgrading AUTO to COMMIT.
        manager.grailsConfig['hibernate.flush.mode'] = 'AUTO'
        // ConfigObject needs the parent map for named connection discovery.
        manager.grailsConfig.dataSources.secondary = [
            url: 'jdbc:h2:mem:hibernate5RefreshLockSecondary;LOCK_TIMEOUT=10000'
        ]
    }

    void 'ordinary lock preserves pending changes and takes a write lock'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate5RefreshLockBook.withSession { it.clear() }
        book = Hibernate5RefreshLockBook.get(book.id)
        book.title = 'pending'

        when:
        def result = book.lock()

        then:
        result.is(book)
        book.title == 'pending'
        book.version == 0
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
        }
    }

    void 'refresh(lock: true) discards pending changes under #flushMode and takes a write lock'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate5RefreshLockBook.withSession { it.clear() }
        book = Hibernate5RefreshLockBook.get(book.id)
        FlushMode previousFlushMode = Hibernate5RefreshLockBook.withSession { Session session ->
            FlushMode previous = session.hibernateFlushMode
            session.setHibernateFlushMode(flushMode)
            previous
        }
        book.title = 'pending'
        assert book.isDirty('title')

        when:
        def result = book.refresh(lock: true)

        then:
        result.is(book)
        book.title == 'original'
        book.version == 0
        !book.isDirty('title')
        !book.isDirty()
        book.getDirtyPropertyNames().isEmpty()
        book.listDirtyPropertyNames().isEmpty()
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE && session.hibernateFlushMode == flushMode
        }

        when: 'the refreshed entity is flushed without further edits'
        Hibernate5RefreshLockBook.withSession { it.flush() }

        then:
        book.version == 0

        when:
        Hibernate5RefreshLockBook.withSession { it.clear() }

        then:
        Hibernate5RefreshLockBook.get(book.id).title == 'original'
        Hibernate5RefreshLockBook.get(book.id).version == 0

        cleanup:
        if (previousFlushMode != null) {
            Hibernate5RefreshLockBook.withSession { Session session ->
                session.setHibernateFlushMode(previousFlushMode)
            }
        }

        where:
        flushMode << [FlushMode.AUTO, FlushMode.COMMIT]
    }

    void 'refresh(lock: true) discards scalar and embedded edits without a version bump under #flushMode'() {
        given:
        def book = new Hibernate5RefreshLockEmbeddedBook(
                title: 'original',
                details: new Hibernate5RefreshLockDetails(summary: 'original summary', language: 'English')
        ).save(flush: true, failOnError: true)
        Hibernate5RefreshLockEmbeddedBook.withSession { it.clear() }
        book = Hibernate5RefreshLockEmbeddedBook.get(book.id)
        FlushMode previousFlushMode = Hibernate5RefreshLockEmbeddedBook.withSession { Session session ->
            FlushMode previous = session.hibernateFlushMode
            session.setHibernateFlushMode(flushMode)
            previous
        }
        book.title = 'pending title'
        book.details.summary = 'pending summary'
        assert book.isDirty('title')
        assert book.details.hasChanged('summary')

        when:
        def result = book.refresh(lock: true)

        then:
        result.is(book)
        book.title == 'original'
        book.details.summary == 'original summary'
        book.details.language == 'English'
        !book.isDirty('title')
        !book.isDirty()
        book.getDirtyPropertyNames().isEmpty()
        book.listDirtyPropertyNames().isEmpty()
        !book.details.hasChanged('summary')
        !book.details.hasChanged()
        book.details.listDirtyPropertyNames().isEmpty()
        Hibernate5RefreshLockEmbeddedBook.withSession { Session session ->
            session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE && session.hibernateFlushMode == flushMode
        }

        when: 'the discarded edits must not schedule an update at flush'
        Hibernate5RefreshLockEmbeddedBook.withSession { it.flush() }

        then:
        book.version == 0

        when:
        Hibernate5RefreshLockEmbeddedBook.withSession { it.clear() }
        book = Hibernate5RefreshLockEmbeddedBook.get(book.id)

        then:
        book.version == 0
        book.title == 'original'
        book.details.summary == 'original summary'
        book.details.language == 'English'

        when: 'subsequent scalar and embedded edits are still tracked'
        book.title = 'saved title'
        book.details.summary = 'saved summary'
        book.save(flush: true, failOnError: true)
        Hibernate5RefreshLockEmbeddedBook.withSession { it.clear() }
        book = Hibernate5RefreshLockEmbeddedBook.get(book.id)

        then:
        book.version == 1
        book.title == 'saved title'
        book.details.summary == 'saved summary'
        book.details.language == 'English'

        cleanup:
        if (previousFlushMode != null) {
            Hibernate5RefreshLockEmbeddedBook.withSession { Session session ->
                session.setHibernateFlushMode(previousFlushMode)
            }
        }

        where:
        flushMode << [FlushMode.AUTO, FlushMode.COMMIT]
    }

    void 'mutex reloads the committed state under the lock instead of failing on the version already loaded'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()
        def seen = [:]

        when:
        Hibernate5RefreshLockBook.withNewSession { Session session ->
            Hibernate5RefreshLockBook.withTransaction {
                def book = Hibernate5RefreshLockBook.get(id)
                assert book.version == 0

                executor.submit({
                    Hibernate5RefreshLockBook.withNewSession {
                        Hibernate5RefreshLockBook.withTransaction {
                            def competing = Hibernate5RefreshLockBook.get(id)
                            competing.title = 'competing commit'
                            competing.save(flush: true, failOnError: true)
                        }
                    }
                } as Callable).get(10, TimeUnit.SECONDS)

                // The instance still holds the version it was loaded with, which lock() would reject.
                assert book.version == 0
                seen.closureResult = book.mutex { book.title }
                seen.version = book.version
                seen.lockMode = session.getCurrentLockMode(book)
            }
        }

        then: 'the closure runs on the committed state, behind an exclusive lock, rather than throwing'
        noExceptionThrown()
        seen.closureResult == 'competing commit'
        seen.version == 1
        seen.lockMode == LockMode.PESSIMISTIC_WRITE

        cleanup:
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)
    }

    void 'an instance operation reached through the named-connection static api runs on that connection'() {
        given: 'no session open for the default connection, so only the named one can serve the save'
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null

        when:
        Long id = Hibernate5RefreshLockRoutedBook.secondary.withNewSession {
            Hibernate5RefreshLockRoutedBook.secondary.withTransaction {
                def book = new Hibernate5RefreshLockRoutedBook(title: 'via static secondary')
                Hibernate5RefreshLockRoutedBook.secondary.save(book, [flush: true, failOnError: true])
                book.id
            }
        }

        then: 'the static api resolves its own connection, whose session is the only one open'
        id != null

        and: 'the row is on that connection and not on the default one'
        Hibernate5RefreshLockRoutedBook.secondary.withNewSession {
            Hibernate5RefreshLockRoutedBook.secondary.get(id)?.title == 'via static secondary'
        }
        Hibernate5RefreshLockRoutedBook.withNewSession {
            Hibernate5RefreshLockRoutedBook.findAllByTitle('via static secondary').isEmpty()
        }
    }

    void 'ordinary lock rejects a stale committed version'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()

        when:
        Hibernate5RefreshLockBook.withNewSession {
            Hibernate5RefreshLockBook.withTransaction {
                def book = Hibernate5RefreshLockBook.get(id)
                assert book.version == 0

                executor.submit({
                    Hibernate5RefreshLockBook.withNewSession {
                        Hibernate5RefreshLockBook.withTransaction {
                            def competingBook = Hibernate5RefreshLockBook.get(id)
                            competingBook.title = 'competing commit'
                            competingBook.save(flush: true, failOnError: true)
                            assert competingBook.version == 1
                        }
                    }
                } as Callable).get(10, TimeUnit.SECONDS)

                assert book.title == 'original'
                assert book.version == 0
                book.lock()
            }
        }

        then:
        thrown(OptimisticLockingFailureException)

        cleanup:
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)
    }

    void 'refresh(lock: true) reloads a committed stale version and allows a subsequent save'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()

        when:
        Hibernate5RefreshLockBook.withNewSession { Session session ->
            Hibernate5RefreshLockBook.withTransaction {
                def book = Hibernate5RefreshLockBook.get(id)
                assert book.version == 0

                executor.submit({
                    Hibernate5RefreshLockBook.withNewSession {
                        Hibernate5RefreshLockBook.withTransaction {
                            def competingBook = Hibernate5RefreshLockBook.get(id)
                            competingBook.title = 'competing commit'
                            competingBook.save(flush: true, failOnError: true)
                            assert competingBook.version == 1
                        }
                    }
                } as Callable).get(10, TimeUnit.SECONDS)

                assert book.title == 'original'
                assert book.version == 0
                book.title = 'stale pending edit'
                assert book.isDirty('title')
                assert book.refresh(lock: true).is(book)
                assert book.title == 'competing commit'
                assert book.version == 1
                assert !book.isDirty('title')
                assert !book.isDirty()
                assert book.getDirtyPropertyNames().isEmpty()
                assert book.listDirtyPropertyNames().isEmpty()
                assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE

                book.title = 'saved after refresh'
                assert book.save(flush: true, failOnError: true).is(book)
                assert book.version == 2
            }
        }

        then:
        Hibernate5RefreshLockBook.withNewSession {
            def book = Hibernate5RefreshLockBook.get(id)
            assert book.title == 'saved after refresh'
            assert book.version == 2
            true
        }

        cleanup:
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)
    }

    void 'public operations refresh(lock: true) locks and refreshes a proxy (initialized: #initialized)'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def allOperations = Hibernate5RefreshLockBook.'default'
        def executor = Executors.newSingleThreadExecutor()

        expect:
        allOperations.withNewSession { Session session ->
            allOperations.withTransaction {
                def proxy = allOperations.load(id)
                assert !Hibernate.isInitialized(proxy)
                if (initialized) {
                    proxy.title = 'pending'
                }

                executor.submit({
                    allOperations.withNewSession {
                        allOperations.withTransaction {
                            def competingBook = allOperations.get(id)
                            competingBook.title = 'competing commit'
                            competingBook.save(flush: true, failOnError: true)
                            assert competingBook.version == 1
                        }
                    }
                } as Callable).get(10, TimeUnit.SECONDS)

                assert Hibernate.isInitialized(proxy) == initialized
                def result = allOperations.refresh(proxy, [lock: true])
                assert result.is(proxy)
                assert Hibernate.isInitialized(proxy)
                assert session.getCurrentLockMode(proxy) == LockMode.PESSIMISTIC_WRITE
                assert proxy.title == 'competing commit'
                assert proxy.version == 1

                session.flush()
                assert proxy.version == 1
                proxy.title = 'saved after proxy refresh'
                allOperations.save(proxy, [flush: true, failOnError: true])
                assert proxy.version == 2
                true
            }
        }
        allOperations.withNewSession {
            def book = allOperations.get(id)
            assert book.title == 'saved after proxy refresh'
            assert book.version == 2
            true
        }

        cleanup:
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)

        where:
        initialized << [false, true]
    }

    void 'public operations refresh(lock: true) rejects a missing transaction before initializing a proxy'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def allOperations = Hibernate5RefreshLockBook.'default'
        Hibernate5RefreshLockBook proxy

        when:
        allOperations.withNewSession { Session session ->
            assert !session.getTransaction().isActive()
            proxy = allOperations.load(id)
            assert !Hibernate.isInitialized(proxy)
            allOperations.refresh(proxy, [lock: true])
        }

        then:
        thrown(TransactionRequiredException)
        !Hibernate.isInitialized(proxy)
    }

    void 'static lock(id, type: PESSIMISTIC_READ) without a refresh request rejects a missing transaction'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null

        when:
        Hibernate5RefreshLockBook.withNewSession { Session session ->
            assert !session.getTransaction().isActive()
            Hibernate5RefreshLockBook.lock(id, type: LockModeType.PESSIMISTIC_READ)
        }

        then:
        def exception = thrown(TransactionRequiredException)
        exception.message == 'An active transaction is required.'
    }

    void 'refresh(lock: true) discards changes and permits saving a nonversioned entity'() {
        given:
        def book = new Hibernate5RefreshLockNonversionedBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate5RefreshLockNonversionedBook.withSession { it.clear() }
        book = Hibernate5RefreshLockNonversionedBook.get(book.id)
        book.title = 'pending'
        assert book.isDirty('title')

        when:
        def result = book.refresh(lock: true)

        then:
        result.is(book)
        book.title == 'original'
        !book.isDirty('title')
        !book.isDirty()
        book.getDirtyPropertyNames().isEmpty()
        book.listDirtyPropertyNames().isEmpty()
        Hibernate5RefreshLockNonversionedBook.withSession { Session session ->
            session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
        }

        when:
        book.title = 'saved after refresh'
        book.save(flush: true, failOnError: true)
        Hibernate5RefreshLockNonversionedBook.withSession { it.clear() }

        then:
        Hibernate5RefreshLockNonversionedBook.get(book.id).title == 'saved after refresh'
    }

    void 'refresh(lock: true) requires an active transaction even with an open bound session and managed entity'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        assert !TransactionSynchronizationManager.isActualTransactionActive()

        when:
        Hibernate5RefreshLockBook.withNewSession { Session session ->
            assert session.isOpen()
            assert TransactionSynchronizationManager.hasResource(manager.sessionFactory)
            assert !TransactionSynchronizationManager.isActualTransactionActive()
            assert !session.getTransaction().isActive()
            book = Hibernate5RefreshLockBook.get(book.id)
            assert session.contains(book)
            book.title = 'pending'
            book.refresh(lock: true)
        }

        then:
        def exception = thrown(TransactionRequiredException)
        exception.message == 'An active transaction is required.'
        book.title == 'pending'
        book.version == 0
    }

    void 'refresh(lock: true) uses the named connection rather than the default database'() {
        given:
        def defaultBook = new Hibernate5RefreshLockRoutedBook(title: 'default').save(flush: true, failOnError: true)
        Long defaultId = defaultBook.id
        Long defaultVersion = defaultBook.version

        expect:
        Hibernate5RefreshLockRoutedBook.secondary.withTransaction {
            Hibernate5RefreshLockRoutedBook.secondary.withSession { Session session ->
                assert session.getTransaction().isActive()
                def url = session.doReturningWork { connection -> connection.metaData.URL }
                assert url == 'jdbc:h2:mem:hibernate5RefreshLockSecondary'
                def book = new Hibernate5RefreshLockRoutedBook(title: 'secondary')
                book.secondary.save(flush: true, failOnError: true)
                Long secondaryId = book.id
                session.clear()
                book = Hibernate5RefreshLockRoutedBook.secondary.get(secondaryId)
                assert session.contains(book)
                book.title = 'pending'

                assert book.secondary.refresh(lock: true).is(book)
                assert book.title == 'secondary'
                assert book.version == 0
                assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE

                session.flush()
                session.clear()
                def reloadedSecondary = Hibernate5RefreshLockRoutedBook.secondary.get(secondaryId)
                assert reloadedSecondary.title == 'secondary'
                assert reloadedSecondary.version == 0
                true
            }
        }
        defaultBook.title == 'default'
        Hibernate5RefreshLockRoutedBook.withSession { Session session ->
            session.flush()
            session.clear()
            def reloadedDefault = Hibernate5RefreshLockRoutedBook.get(defaultId)
            assert reloadedDefault.title == 'default'
            assert reloadedDefault.version == defaultVersion
            assert Hibernate5RefreshLockRoutedBook.countByTitle('secondary') == 0
            assert Hibernate5RefreshLockRoutedBook.countByTitle('pending') == 0
            true
        }
    }

    void 'refresh(lock: true) requires a transaction on the named connection even when the default transaction is active'() {
        given:
        Long id = Hibernate5RefreshLockRoutedBook.secondary.withNewSession {
            Hibernate5RefreshLockRoutedBook.secondary.withTransaction {
                new Hibernate5RefreshLockRoutedBook(title: 'secondary').secondary.save(flush: true, failOnError: true).id
            }
        }
        Hibernate5RefreshLockRoutedBook book

        when:
        Hibernate5RefreshLockRoutedBook.secondary.withNewSession { Session session ->
            book = Hibernate5RefreshLockRoutedBook.secondary.get(id)
            assert session.isOpen()
            assert session.contains(book)
            assert !session.getTransaction().isActive()
            Hibernate5RefreshLockRoutedBook.withSession { Session defaultSession ->
                assert defaultSession.getTransaction().isActive()
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
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
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
            Hibernate5RefreshLockBook.withNewSession { Session session ->
                Hibernate5RefreshLockBook.withTransaction {
                    def book = Hibernate5RefreshLockBook.get(id)
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
            Hibernate5RefreshLockBook.withNewSession {
                Hibernate5RefreshLockBook.withTransaction {
                    assert loaded.await(10, TimeUnit.SECONDS)
                    def book = Hibernate5RefreshLockBook.get(id)
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
            Hibernate5RefreshLockBook.withNewSession {
                Hibernate5RefreshLockBook.withTransaction {
                    contenderStarted.countDown()
                    def book = Hibernate5RefreshLockBook.lock(id)
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
        Hibernate5RefreshLockBook.withNewSession {
            def book = Hibernate5RefreshLockBook.get(id)
            assert book.title == 'last commit'
            assert book.version == 2
            true
        }

        cleanup:
        allowCompetingCommit?.countDown()
        allowRefreshCommit?.countDown()
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)
    }

    void 'static lock(id, refresh: true) reloads a committed stale version of the managed instance and allows a subsequent save'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()

        when:
        Hibernate5RefreshLockBook.withNewSession { Session session ->
            Hibernate5RefreshLockBook.withTransaction {
                def book = Hibernate5RefreshLockBook.get(id)
                assert book.version == 0

                executor.submit({
                    Hibernate5RefreshLockBook.withNewSession {
                        Hibernate5RefreshLockBook.withTransaction {
                            def competingBook = Hibernate5RefreshLockBook.get(id)
                            competingBook.title = 'competing commit'
                            competingBook.save(flush: true, failOnError: true)
                            assert competingBook.version == 1
                        }
                    }
                } as Callable).get(10, TimeUnit.SECONDS)

                assert book.title == 'original'
                assert book.version == 0
                book.title = 'stale pending edit'

                def locked = Hibernate5RefreshLockBook.lock(id, refresh: true)
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
        Hibernate5RefreshLockBook.withNewSession {
            def book = Hibernate5RefreshLockBook.get(id)
            book.title == 'saved after locked refresh' && book.version == 2
        }

        cleanup:
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)
    }

    void 'refresh(lock: #type) on a managed instance issues #statements statements: the locked reload#increment'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        Hibernate5RefreshLockBook.withSession { it.clear() }
        def book = Hibernate5RefreshLockBook.get(id)

        def statistics = manager.sessionFactory.statistics
        statistics.statisticsEnabled = true
        long statementsBefore = statistics.prepareStatementCount

        when:
        book.refresh(lock: type)

        then: 'Hibernate 5 reloads and locks in one statement, so no extra round trip records the mode'
        statistics.prepareStatementCount == statementsBefore + statements
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) == expectedLockMode
        }

        where:
        type                                     | expectedLockMode           | statements
        LockModeType.PESSIMISTIC_READ            | LockMode.PESSIMISTIC_READ  | 1
        LockModeType.PESSIMISTIC_WRITE           | LockMode.PESSIMISTIC_WRITE | 1
        LockModeType.PESSIMISTIC_FORCE_INCREMENT | LockMode.FORCE             | 2
        increment = statements == 2 ? ' and the version increment' : ''
    }

    void 'static lock(id, refresh: true) loads and locks an instance that is not in the session'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        Hibernate5RefreshLockBook.withSession { it.clear() }

        def statistics = manager.sessionFactory.statistics
        statistics.statisticsEnabled = true
        long statementsBefore = statistics.prepareStatementCount

        when:
        def book = Hibernate5RefreshLockBook.lock(id, refresh: true)

        then: 'a single locked load, not an unlocked get followed by a locked refresh'
        statistics.prepareStatementCount == statementsBefore + 1
        book != null
        Hibernate.isInitialized(book)
        book.title == 'original'
        book.version == 0
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.contains(book) && session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
        }
    }

    void 'static lock(id, refresh: true) returns null for an entity deleted in this session'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Long id = book.id
        book.delete()

        expect: 'the deleted row is reported as gone, the way lock(id) reports it'
        Hibernate5RefreshLockBook.lock(id, refresh: true) == null
    }

    void 'refresh(lock: #requested) after #held keeps the version behaviour of the mode actually in force'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null

        when: 'an optimistic increment is requested while a stronger lock is already held'
        Hibernate5RefreshLockBook.withNewSession {
            Hibernate5RefreshLockBook.withTransaction {
                def book = Hibernate5RefreshLockBook.get(id)
                acquire(book)
                book.refresh(lock: requested)
            }
        }

        then: 'the held lock stands, and only a request that is not superseded increments the version'
        Hibernate5RefreshLockBook.withNewSession { Hibernate5RefreshLockBook.get(id).version } == expectedVersion

        where:
        held                  | acquire        | requested                                    || expectedVersion
        'nothing'             | { }            | LockModeType.OPTIMISTIC_FORCE_INCREMENT      || 1
        'a pessimistic lock'  | { it.lock() }  | LockModeType.OPTIMISTIC_FORCE_INCREMENT      || 0
    }

    void 'static lock(id, refresh: true) returns null for an unknown identifier'() {
        expect:
        Hibernate5RefreshLockBook.lock(-1L, refresh: true) == null
    }

    void 'static lock(id, args) without a refresh request locks the managed instance and preserves pending changes (#description)'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate5RefreshLockBook.withSession { it.clear() }
        book = Hibernate5RefreshLockBook.get(book.id)
        book.title = 'pending'

        when:
        def result = Hibernate5RefreshLockBook.lock(args, book.id)

        then:
        result.is(book)
        book.title == 'pending'
        book.version == 0
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
        }

        where:
        description      | args
        'empty map'      | [:]
        'refresh: false' | [refresh: false]
    }

    void 'static lock(id, refresh: true) requires an active transaction'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null

        when:
        Hibernate5RefreshLockBook.withNewSession { Session session ->
            assert !session.getTransaction().isActive()
            Hibernate5RefreshLockBook.lock(id, refresh: true)
        }

        then:
        def exception = thrown(TransactionRequiredException)
        exception.message == 'An active transaction is required.'
    }

    void 'static lock(id, refresh: true) uses the named connection rather than the default database'() {
        given:
        def defaultBook = new Hibernate5RefreshLockRoutedBook(title: 'default').save(flush: true, failOnError: true)
        Long defaultId = defaultBook.id

        expect:
        Hibernate5RefreshLockRoutedBook.secondary.withTransaction {
            Hibernate5RefreshLockRoutedBook.secondary.withSession { Session session ->
                assert session.getTransaction().isActive()
                assert session.doReturningWork { connection -> connection.metaData.URL } == 'jdbc:h2:mem:hibernate5RefreshLockSecondary'
                def book = new Hibernate5RefreshLockRoutedBook(title: 'secondary')
                book.secondary.save(flush: true, failOnError: true)
                Long secondaryId = book.id
                session.clear()
                book = Hibernate5RefreshLockRoutedBook.secondary.get(secondaryId)
                assert session.contains(book)
                book.title = 'pending'

                def locked = Hibernate5RefreshLockRoutedBook.secondary.lock(secondaryId, refresh: true)
                assert locked.is(book)
                assert book.title == 'secondary'
                assert book.version == 0
                assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE

                session.flush()
                session.clear()
                def loaded = Hibernate5RefreshLockRoutedBook.secondary.lock(secondaryId, refresh: true)
                assert loaded != null && !loaded.is(book)
                assert loaded.title == 'secondary'
                assert session.contains(loaded)
                assert session.getCurrentLockMode(loaded) == LockMode.PESSIMISTIC_WRITE
                true
            }
        }
        Hibernate5RefreshLockRoutedBook.withSession { Session session ->
            session.flush()
            session.clear()
            def reloadedDefault = Hibernate5RefreshLockRoutedBook.get(defaultId)
            assert reloadedDefault.title == 'default'
            assert reloadedDefault.version == 0
            assert Hibernate5RefreshLockRoutedBook.countByTitle('secondary') == 0
            assert Hibernate5RefreshLockRoutedBook.countByTitle('pending') == 0
            true
        }
    }

    void 'static lock(id, refresh: true) rejects a missing transaction before initializing a proxy'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        Hibernate5RefreshLockBook proxy

        when:
        Hibernate5RefreshLockBook.withNewSession { Session session ->
            assert !session.getTransaction().isActive()
            proxy = Hibernate5RefreshLockBook.load(id)
            assert !Hibernate.isInitialized(proxy)
            Hibernate5RefreshLockBook.lock(id, refresh: true)
        }

        then:
        def exception = thrown(TransactionRequiredException)
        exception.message == 'An active transaction is required.'
        !Hibernate.isInitialized(proxy)
    }

    void 'refresh(lock: true) rejects a detached instance without changing it (proxy: #useProxy)'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        Hibernate5RefreshLockBook.withSession { it.clear() }
        def book = useProxy ? Hibernate5RefreshLockBook.load(id) : Hibernate5RefreshLockBook.get(id)
        Hibernate5RefreshLockBook.withSession { Session session ->
            assert session.contains(book)
            session.evict(book)
            assert !session.contains(book)
        }
        if (!useProxy) {
            book.title = 'pending'
        }

        when: 'a detached proxy is refreshed through the operations api, as any call on the proxy itself would initialize it'
        useProxy ? Hibernate5RefreshLockBook.'default'.refresh(book, [lock: true]) : book.refresh(lock: true)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == 'The instance must be attached to the current session.'
        useProxy ? !Hibernate.isInitialized(book) : book.title == 'pending'
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.clear()
            Hibernate5RefreshLockBook.get(id).title == 'original'
        }

        where:
        useProxy << [false, true]
    }

    void 'refresh(lock: #description) reloads state and version under the requested lock mode'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate5RefreshLockBook.withSession { it.clear() }
        book = Hibernate5RefreshLockBook.get(book.id)
        book.title = 'pending'

        when:
        def result = book.refresh(lock: lock)

        then:
        result.is(book)
        book.title == 'original'
        book.version == 0
        !book.isDirty()
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) == expectedLockMode
        }

        where:
        description                    | lock                             | expectedLockMode
        'LockModeType.PESSIMISTIC_READ'  | LockModeType.PESSIMISTIC_READ    | LockMode.PESSIMISTIC_READ
        'LockModeType.PESSIMISTIC_WRITE' | LockModeType.PESSIMISTIC_WRITE   | LockMode.PESSIMISTIC_WRITE
        "'PESSIMISTIC_READ'"             | 'PESSIMISTIC_READ'               | LockMode.PESSIMISTIC_READ
        "'true'"                         | 'true'                           | LockMode.PESSIMISTIC_WRITE
    }

    void 'refresh rejects a lock argument that is neither a boolean nor a lock mode without changing the entity'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        book.title = 'pending'

        when:
        book.refresh(lock: 'SHARED')

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "The 'lock' argument must be a boolean or a jakarta.persistence.LockModeType but was 'SHARED'"
        book.title == 'pending'
    }

    void 'lock(refresh: true) on an instance explains that the instance form is refresh(lock: true)'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)

        when:
        book.lock(refresh: true)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == 'lock was called with named arguments but no identifier. ' +
                'Use DomainClass.lock(id, refresh: true) to lock by identifier, ' +
                'or instance.refresh(lock: true) to reload an instance under a lock'
    }

    void 'static lock(id, type: #description) locks the managed instance under that mode and preserves pending changes'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate5RefreshLockBook.withSession { it.clear() }
        book = Hibernate5RefreshLockBook.get(book.id)
        book.title = 'pending'

        when:
        def result = Hibernate5RefreshLockBook.lock(book.id, type: type)

        then:
        result.is(book)
        book.title == 'pending'
        book.version == 0
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) == expectedLockMode
        }

        where:
        description                      | type                           | expectedLockMode
        'LockModeType.PESSIMISTIC_READ'  | LockModeType.PESSIMISTIC_READ  | LockMode.PESSIMISTIC_READ
        'LockModeType.PESSIMISTIC_WRITE' | LockModeType.PESSIMISTIC_WRITE | LockMode.PESSIMISTIC_WRITE
        "'pessimistic_read'"             | 'pessimistic_read'             | LockMode.PESSIMISTIC_READ
    }

    void 'static lock(id, type: PESSIMISTIC_READ) loads an instance that is not in the session under that mode (refresh: #refresh)'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        Hibernate5RefreshLockBook.withSession { it.clear() }

        when:
        def book = Hibernate5RefreshLockBook.lock(id, type: LockModeType.PESSIMISTIC_READ, refresh: refresh)

        then:
        book != null
        Hibernate.isInitialized(book)
        book.title == 'original'
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.contains(book) && session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_READ
        }
        Hibernate5RefreshLockBook.lock(-1L, type: LockModeType.PESSIMISTIC_READ, refresh: refresh) == null

        where:
        refresh << [false, true]
    }

    void 'static lock(id, refresh: true, type: PESSIMISTIC_READ) reloads the managed instance under that mode'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate5RefreshLockBook.withSession { it.clear() }
        book = Hibernate5RefreshLockBook.get(book.id)
        book.title = 'pending'

        when:
        def result = Hibernate5RefreshLockBook.lock(book.id, refresh: true, type: LockModeType.PESSIMISTIC_READ)

        then:
        result.is(book)
        book.title == 'original'
        book.version == 0
        !book.isDirty()
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_READ
        }
    }

    void 'static lock(id, type: #description) is rejected without touching the entity'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        book.title = 'pending'

        when:
        Hibernate5RefreshLockBook.lock(book.id, type: type, refresh: true)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == message
        book.title == 'pending'

        where:
        description | type              | message
        'NONE'      | LockModeType.NONE | "The 'type' argument must name a lock but was NONE"
        "'SHARED'"  | 'SHARED'          | "The 'type' argument must be a jakarta.persistence.LockModeType but was 'SHARED'"
    }

    void 'refresh(lock: true) discards cascaded child edits without a spurious child update at flush'() {
        given:
        def child = new Hibernate5RefreshLockCascadeChild(title: 'original child')
        def parent = new Hibernate5RefreshLockCascadeParent(title: 'original parent', child: child)
                .save(flush: true, failOnError: true)
        Long parentId = parent.id
        Long childId = child.id
        Hibernate5RefreshLockCascadeParent.withSession { it.clear() }
        parent = Hibernate5RefreshLockCascadeParent.get(parentId)
        child = parent.child
        assert Hibernate.isInitialized(child)
        parent.title = 'pending parent'
        child.title = 'pending child'
        assert child.isDirty('title')

        when:
        def result = parent.refresh(lock: true)

        then: 'the configured refresh cascade reloads the child as well'
        result.is(parent)
        parent.title == 'original parent'
        child.title == 'original child'
        !parent.isDirty()
        !child.isDirty()
        child.listDirtyPropertyNames().isEmpty()
        Hibernate5RefreshLockCascadeParent.withSession { Session session ->
            session.getCurrentLockMode(parent) == LockMode.PESSIMISTIC_WRITE
        }

        when: 'the discarded edits must not schedule an update of either entity at flush'
        Hibernate5RefreshLockCascadeParent.withSession { it.flush() }

        then:
        parent.version == 0
        child.version == 0

        when:
        Hibernate5RefreshLockCascadeParent.withSession { it.clear() }

        then:
        Hibernate5RefreshLockCascadeChild.get(childId).version == 0
        Hibernate5RefreshLockCascadeChild.get(childId).title == 'original child'
    }

    void 'refresh(lock: true) discards cascaded edits to the elements of a collection without a spurious update at flush'() {
        given:
        def parent = new Hibernate5RefreshLockCollectionParent(title: 'original parent')
                .addToChildren(title: 'original first')
                .addToChildren(title: 'original second')
                .save(flush: true, failOnError: true)
        Long parentId = parent.id
        Hibernate5RefreshLockCollectionParent.withSession { it.clear() }
        parent = Hibernate5RefreshLockCollectionParent.get(parentId)
        def children = parent.children.sort { it.title }
        assert Hibernate.isInitialized(parent.children)
        parent.title = 'pending parent'
        children.each { it.title = 'pending ' + it.id }
        assert children.every { it.isDirty('title') }

        when:
        def result = parent.refresh(lock: true)

        then: 'the refresh cascade reloads every element and the dirty state follows it'
        result.is(parent)
        parent.title == 'original parent'
        children*.title.sort() == ['original first', 'original second']
        !parent.isDirty()
        children.every { !it.isDirty() && it.listDirtyPropertyNames().isEmpty() }
        Hibernate5RefreshLockCollectionParent.withSession { Session session ->
            session.getCurrentLockMode(parent) == LockMode.PESSIMISTIC_WRITE
        }

        when: 'the discarded edits must not schedule an update of the parent or any element at flush'
        Hibernate5RefreshLockCollectionParent.withSession { it.flush() }

        then:
        parent.version == 0
        children.every { it.version == 0 }
    }

    void 'refresh with arguments that do not request a lock reloads state without a write lock (#description)'() {
        given:
        def book = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true)
        Hibernate5RefreshLockBook.withSession { it.clear() }
        book = Hibernate5RefreshLockBook.get(book.id)
        book.title = 'pending'

        when:
        def result = book.refresh(args)

        then:
        result.is(book)
        book.title == 'original'
        book.version == 0
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) != LockMode.PESSIMISTIC_WRITE
        }

        where:
        description         | args
        'empty map'         | [:]
        'lock: false'       | [lock: false]
        'lock: NONE'        | [lock: LockModeType.NONE]
        "lock: 'NONE'"      | [lock: 'NONE']
        'lock: null'        | [lock: null]
    }
    void 'refresh(lock: true) discards edits reached through an embedded component that cascades to an association'() {
        given:
        def child = new Hibernate5RefreshLockCascadeChild(title: 'original child')
        def owner = new Hibernate5RefreshLockEmbeddedOwner(title: 'original owner',
                details: new Hibernate5RefreshLockOwnerDetails(note: 'original note', child: child))
                .save(flush: true, failOnError: true)
        Long ownerId = owner.id
        Long childId = child.id
        Hibernate5RefreshLockEmbeddedOwner.withSession { it.clear() }
        owner = Hibernate5RefreshLockEmbeddedOwner.get(ownerId)
        child = owner.details.child
        assert child.title == 'original child'
        owner.title = 'pending owner'
        owner.details.note = 'pending note'
        child.title = 'pending child'
        assert owner.details.hasChanged('note')
        assert child.isDirty('title')

        when:
        def result = owner.refresh(lock: true)

        then: 'the refresh cascade through the component reloads the child and the dirty state follows it'
        result.is(owner)
        owner.title == 'original owner'
        owner.details.note == 'original note'
        child.title == 'original child'
        !owner.isDirty()
        !owner.details.hasChanged()
        !child.isDirty()
        Hibernate5RefreshLockEmbeddedOwner.withSession { Session session ->
            session.getCurrentLockMode(owner) == LockMode.PESSIMISTIC_WRITE
        }

        when: 'the discarded edits must not schedule an update of either entity at flush'
        Hibernate5RefreshLockEmbeddedOwner.withSession { it.flush() }

        then:
        owner.version == 0
        child.version == 0

        when:
        Hibernate5RefreshLockEmbeddedOwner.withSession { it.clear() }

        then:
        Hibernate5RefreshLockCascadeChild.get(childId).version == 0
        Hibernate5RefreshLockCascadeChild.get(childId).title == 'original child'
    }

    void 'refresh(lock: #type) reloads state under that lock mode and #versionOutcome'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null

        when:
        Map outcome = Hibernate5RefreshLockBook.withNewSession { Session session ->
            Hibernate5RefreshLockBook.withTransaction {
                def book = Hibernate5RefreshLockBook.get(id)
                book.title = 'pending'
                def result = book.refresh(lock: type)
                [same: result.is(book), title: book.title, lockMode: session.getCurrentLockMode(book)]
            }
        }

        then:
        outcome.same
        outcome.title == 'original'
        outcome.lockMode == expectedLockMode
        Hibernate5RefreshLockBook.withNewSession { Hibernate5RefreshLockBook.get(id).version } == expectedVersionAfterCommit

        where:
        type                                     | expectedLockMode                    | expectedVersionAfterCommit
        LockModeType.READ                        | LockMode.OPTIMISTIC                 | 0
        LockModeType.WRITE                       | LockMode.OPTIMISTIC_FORCE_INCREMENT | 1
        LockModeType.OPTIMISTIC                  | LockMode.OPTIMISTIC                 | 0
        LockModeType.OPTIMISTIC_FORCE_INCREMENT  | LockMode.OPTIMISTIC_FORCE_INCREMENT | 1
        LockModeType.PESSIMISTIC_READ            | LockMode.PESSIMISTIC_READ           | 0
        LockModeType.PESSIMISTIC_WRITE           | LockMode.PESSIMISTIC_WRITE          | 0
        LockModeType.PESSIMISTIC_FORCE_INCREMENT | LockMode.FORCE                      | 1 // Hibernate 5 records its legacy alias
        versionOutcome = expectedVersionAfterCommit ? 'increments the version at commit' : 'leaves the version alone'
    }

    void 'static lock(id, type: #type) loads an instance that is not in the session under that lock mode and #versionOutcome'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null

        when:
        Map outcome = Hibernate5RefreshLockBook.withNewSession { Session session ->
            Hibernate5RefreshLockBook.withTransaction {
                def book = Hibernate5RefreshLockBook.lock(id, type: type)
                [loaded: book != null && Hibernate.isInitialized(book), title: book?.title, lockMode: session.getCurrentLockMode(book)]
            }
        }

        then:
        outcome.loaded
        outcome.title == 'original'
        outcome.lockMode == expectedLockMode
        Hibernate5RefreshLockBook.withNewSession { Hibernate5RefreshLockBook.get(id).version } == expectedVersionAfterCommit

        where:
        type                                     | expectedLockMode                    | expectedVersionAfterCommit
        LockModeType.READ                        | LockMode.OPTIMISTIC                 | 0
        LockModeType.WRITE                       | LockMode.OPTIMISTIC_FORCE_INCREMENT | 1
        LockModeType.OPTIMISTIC                  | LockMode.OPTIMISTIC                 | 0
        LockModeType.OPTIMISTIC_FORCE_INCREMENT  | LockMode.OPTIMISTIC_FORCE_INCREMENT | 1
        LockModeType.PESSIMISTIC_READ            | LockMode.PESSIMISTIC_READ           | 0
        LockModeType.PESSIMISTIC_WRITE           | LockMode.PESSIMISTIC_WRITE          | 0
        LockModeType.PESSIMISTIC_FORCE_INCREMENT | LockMode.FORCE                      | 1 // Hibernate 5 records its legacy alias
        versionOutcome = expectedVersionAfterCommit ? 'increments the version at commit' : 'leaves the version alone'
    }

    void 'static lock(id, refresh: true, type: #type) reloads the managed instance under that lock mode and #versionOutcome'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null

        when:
        Map outcome = Hibernate5RefreshLockBook.withNewSession { Session session ->
            Hibernate5RefreshLockBook.withTransaction {
                def book = Hibernate5RefreshLockBook.get(id)
                book.title = 'pending'
                def result = Hibernate5RefreshLockBook.lock(id, refresh: true, type: type)
                [same: result.is(book), title: book.title, lockMode: session.getCurrentLockMode(book)]
            }
        }

        then:
        outcome.same
        outcome.title == 'original'
        outcome.lockMode == expectedLockMode
        Hibernate5RefreshLockBook.withNewSession { Hibernate5RefreshLockBook.get(id).version } == expectedVersionAfterCommit

        where:
        type                                     | expectedLockMode                    | expectedVersionAfterCommit
        LockModeType.READ                        | LockMode.OPTIMISTIC                 | 0
        LockModeType.WRITE                       | LockMode.OPTIMISTIC_FORCE_INCREMENT | 1
        LockModeType.OPTIMISTIC                  | LockMode.OPTIMISTIC                 | 0
        LockModeType.OPTIMISTIC_FORCE_INCREMENT  | LockMode.OPTIMISTIC_FORCE_INCREMENT | 1
        LockModeType.PESSIMISTIC_READ            | LockMode.PESSIMISTIC_READ           | 0
        LockModeType.PESSIMISTIC_WRITE           | LockMode.PESSIMISTIC_WRITE          | 0
        LockModeType.PESSIMISTIC_FORCE_INCREMENT | LockMode.FORCE                      | 1 // Hibernate 5 records its legacy alias
        versionOutcome = expectedVersionAfterCommit ? 'increments the version at commit' : 'leaves the version alone'
    }

    void 'refresh(lock: true) holds a database lock on the parent row while a refresh-cascaded association is join-fetched'() {
        given:
        Long parentId = new Hibernate5RefreshLockCascadeParent(title: 'original parent',
                child: new Hibernate5RefreshLockCascadeChild(title: 'original child')).save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()
        def refreshed = new CountDownLatch(1)
        def allowRefreshCommit = new CountDownLatch(1)

        when: 'a transaction reloads the parent under a lock while its child is already loaded'
        def refreshing = executor.submit({
            Hibernate5RefreshLockCascadeParent.withNewSession { Session session ->
                Hibernate5RefreshLockCascadeParent.withTransaction {
                    def parent = Hibernate5RefreshLockCascadeParent.get(parentId)
                    assert Hibernate.isInitialized(parent.child)
                    assert parent.refresh(lock: true).is(parent)
                    assert session.getCurrentLockMode(parent) == LockMode.PESSIMISTIC_WRITE
                    refreshed.countDown()
                    assert allowRefreshCommit.await(10, TimeUnit.SECONDS)
                }
            }
        } as Callable)

        then: 'a competing SELECT ... FOR UPDATE on another connection is refused while that transaction is open'
        refreshed.await(10, TimeUnit.SECONDS)
        !rowLockGranted(Hibernate5RefreshLockCascadeParent, parentId)

        when:
        allowRefreshCommit.countDown()
        refreshing.get(10, TimeUnit.SECONDS)

        then: 'and granted once it has ended'
        rowLockGranted(Hibernate5RefreshLockCascadeParent, parentId)

        cleanup:
        allowRefreshCommit?.countDown()
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)
    }

    void 'static lock(id, refresh: true) returns the initialized proxy the caller holds'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        Hibernate5RefreshLockBook.withSession { it.clear() }
        def proxy = Hibernate5RefreshLockBook.load(id)
        assert !Hibernate.isInitialized(proxy)
        assert proxy.title == 'original'
        proxy.title = 'pending'

        when:
        def result = Hibernate5RefreshLockBook.lock(id, refresh: true)

        then:
        result.is(proxy)
        Hibernate.isInitialized(proxy)
        proxy.title == 'original'
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(proxy) == LockMode.PESSIMISTIC_WRITE
        }
    }

    void 'static lock(id, refresh: true) through the root of a #description hierarchy locks the row behind an initialized root-typed proxy'() {
        given: 'a subclass row that the session knows only as a proxy of the root type, as a lazy association declared with the root type holds'
        Long id = saved.save(flush: true, failOnError: true).id
        rootClass.withSession { it.clear() }
        def proxy = rootClass.load(id)
        assert !Hibernate.isInitialized(proxy)
        assert proxy.title == 'original'
        proxy.title = 'pending'

        when:
        def result = rootClass.lock(id, refresh: true)

        then: 'the proxy the caller holds is reloaded and the row that holds its state is locked'
        result.is(proxy)
        subClass.isInstance(Hibernate.unproxy(proxy))
        proxy.title == 'original'
        Hibernate.unproxy(proxy).extra == 'subclass state'
        rootClass.withSession { Session session ->
            session.getCurrentLockMode(proxy) == LockMode.PESSIMISTIC_WRITE
        }
        !rowLockGranted(lockedTableClass, id)

        where:
        description                | rootClass                       | subClass                       | lockedTableClass
        'table-per-concrete-class' | Hibernate5RefreshLockUnionRoot  | Hibernate5RefreshLockUnionSub  | Hibernate5RefreshLockUnionSub
        'joined-table'             | Hibernate5RefreshLockJoinedRoot | Hibernate5RefreshLockJoinedSub | Hibernate5RefreshLockJoinedRoot
        saved = rootClass == Hibernate5RefreshLockUnionRoot ?
                new Hibernate5RefreshLockUnionSub(title: 'original', extra: 'subclass state') :
                new Hibernate5RefreshLockJoinedSub(title: 'original', extra: 'subclass state')
    }

    void 'refresh(lock: #requested) after #held reloads the state but never weakens the lock this transaction already holds'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        Hibernate5RefreshLockBook.withSession { it.clear() }
        def book = Hibernate5RefreshLockBook.get(id)
        acquire(book)
        book.title = 'pending'

        when:
        book.refresh(lock: requested)

        then:
        book.title == 'original'
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.getCurrentLockMode(book) == expectedLockMode
        }

        where:
        held                                         | acquire                                                       | requested                      || expectedLockMode
        'lock()'                                     | { it.lock() }                                                 | LockModeType.PESSIMISTIC_READ  || LockMode.PESSIMISTIC_WRITE
        'refresh(lock: true)'                        | { it.refresh(lock: true) }                                    | LockModeType.PESSIMISTIC_READ  || LockMode.PESSIMISTIC_WRITE
        'refresh(lock: PESSIMISTIC_FORCE_INCREMENT)' | { it.refresh(lock: LockModeType.PESSIMISTIC_FORCE_INCREMENT) } | LockModeType.PESSIMISTIC_WRITE || LockMode.FORCE // Hibernate 5 records its legacy alias
        'refresh(lock: PESSIMISTIC_READ)'            | { it.refresh(lock: LockModeType.PESSIMISTIC_READ) }           | LockModeType.PESSIMISTIC_WRITE || LockMode.PESSIMISTIC_WRITE
    }

    void 'refresh(lock: true) on a joined-table subclass waits for a competing commit to the root row (#description)'() {
        given:
        Long id = new Hibernate5RefreshLockJoinedSub(title: 'original', extra: 'subclass state')
                .save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()
        def loaded = new CountDownLatch(1)
        def refreshed = new CountDownLatch(1)
        Connection competitor = openCompetingConnection(10000)
        String rootTable = tableName(Hibernate5RefreshLockJoinedRoot)
        Class entityClass = loadThroughRoot ? Hibernate5RefreshLockJoinedRoot : Hibernate5RefreshLockJoinedSub

        when: 'a competing connection updates the root row, which holds the version, and keeps its transaction open'
        competitor.prepareStatement("update ${rootTable} set title = 'competing commit', version = version + 1 where id = ?".toString())
                .withCloseable { statement ->
                    statement.setLong(1, id)
                    assert statement.executeUpdate() == 1
                }
        def refreshing = executor.submit({
            Hibernate5RefreshLockJoinedSub.withNewSession { Session session ->
                Hibernate5RefreshLockJoinedSub.withTransaction {
                    def book = entityClass.get(id)
                    assert book instanceof Hibernate5RefreshLockJoinedSub
                    assert book.version == 0
                    loaded.countDown()
                    def result = useStatic ? entityClass.lock(id, refresh: true) : book.refresh(lock: true)
                    assert result.is(book)
                    assert book.title == 'competing commit'
                    assert book.version == 1
                    assert book.extra == 'subclass state'
                    assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
                    refreshed.countDown()
                    book.title = 'saved after refresh'
                    assert book.save(flush: true, failOnError: true).is(book)
                    assert book.version == 2
                }
            }
        } as Callable)

        then: 'the locked refresh waits on the root row instead of reading it while the competitor holds it'
        loaded.await(10, TimeUnit.SECONDS) || refreshing.get(1, TimeUnit.SECONDS)
        !refreshed.await(200, TimeUnit.MILLISECONDS)
        !refreshing.isDone()

        when:
        competitor.commit()
        refreshing.get(10, TimeUnit.SECONDS)

        then: 'and then reloads the committed root state and version under its own lock'
        refreshed.count == 0
        Hibernate5RefreshLockJoinedSub.withNewSession {
            def book = Hibernate5RefreshLockJoinedSub.get(id)
            book.title == 'saved after refresh' && book.version == 2 && book.extra == 'subclass state'
        }

        cleanup:
        competitor?.rollback()
        competitor?.close()
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)

        where:
        description                                       | loadThroughRoot | useStatic
        'loaded as the subclass'                          | false           | false
        'loaded polymorphically through the root'         | true            | false
        'static lock(id, refresh: true) on the subclass'  | false           | true
        'static lock(id, refresh: true) on the root'      | true            | true
    }

    void 'refresh(lock: true) on a table-per-concrete-class subclass waits for a competing commit to its row (#description)'() {
        given:
        Long id = new Hibernate5RefreshLockUnionSub(title: 'original', extra: 'subclass state')
                .save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()
        def loaded = new CountDownLatch(1)
        def refreshed = new CountDownLatch(1)
        Connection competitor = openCompetingConnection(10000)
        String concreteTable = tableName(Hibernate5RefreshLockUnionSub)
        Class entityClass = loadThroughRoot ? Hibernate5RefreshLockUnionRoot : Hibernate5RefreshLockUnionSub

        when: 'a competing connection updates the concrete table, which holds the whole row, and keeps its transaction open'
        competitor.prepareStatement("update ${concreteTable} set title = 'competing commit', version = version + 1 where id = ?".toString())
                .withCloseable { statement ->
                    statement.setLong(1, id)
                    assert statement.executeUpdate() == 1
                }
        def refreshing = executor.submit({
            Hibernate5RefreshLockUnionSub.withNewSession { Session session ->
                Hibernate5RefreshLockUnionSub.withTransaction {
                    def book = entityClass.get(id)
                    assert book instanceof Hibernate5RefreshLockUnionSub
                    assert book.version == 0
                    loaded.countDown()
                    def result = useStatic ? entityClass.lock(id, refresh: true) : book.refresh(lock: true)
                    assert result.is(book)
                    assert book.title == 'competing commit'
                    assert book.version == 1
                    assert book.extra == 'subclass state'
                    assert session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
                    refreshed.countDown()
                    book.title = 'saved after refresh'
                    assert book.save(flush: true, failOnError: true).is(book)
                    assert book.version == 2
                }
            }
        } as Callable)

        then: 'the locked refresh waits on the concrete row instead of reading it through the union of the hierarchy'
        loaded.await(10, TimeUnit.SECONDS) || refreshing.get(1, TimeUnit.SECONDS)
        !refreshed.await(200, TimeUnit.MILLISECONDS)
        !refreshing.isDone()

        when:
        competitor.commit()
        refreshing.get(10, TimeUnit.SECONDS)

        then: 'and then reloads the committed state and version under its own lock'
        refreshed.count == 0
        Hibernate5RefreshLockUnionSub.withNewSession {
            def book = Hibernate5RefreshLockUnionSub.get(id)
            book.title == 'saved after refresh' && book.version == 2 && book.extra == 'subclass state'
        }

        cleanup:
        competitor?.rollback()
        competitor?.close()
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)

        where:
        description                                       | loadThroughRoot | useStatic
        'loaded as the subclass'                          | false           | false
        'loaded polymorphically through the root'         | true            | false
        'static lock(id, refresh: true) on the subclass'  | false           | true
        'static lock(id, refresh: true) on the root'      | true            | true
    }

    /**
     * Attempts a plain JDBC {@code SELECT ... FOR UPDATE} of the entity's row in the given class's table on a
     * separate connection with a short lock timeout, and reports whether the database granted the lock. Unlike
     * {@code getCurrentLockMode}, which reports what Hibernate recorded, this observes the lock itself.
     */
    private boolean rowLockGranted(Class entityClass, Long id) {
        String table = tableName(entityClass)
        openCompetingConnection(300).withCloseable { Connection connection ->
            try {
                connection.prepareStatement("select id from ${table} where id = ? for update".toString()).withCloseable { statement ->
                    statement.setLong(1, id)
                    statement.executeQuery().withCloseable { it.next() }
                }
            } catch (SQLTimeoutException ignored) {
                false
            } finally {
                connection.rollback()
            }
        }
    }

    /**
     * Opens a separate, non-auto-commit JDBC connection to the default database so a competitor can hold or
     * contend for row locks independently of any Hibernate session.
     */
    private Connection openCompetingConnection(int lockTimeoutMillis) {
        Map<String, String> jdbc = Hibernate5RefreshLockBook.withNewSession { Session session ->
            session.doReturningWork { Connection connection ->
                [url: connection.metaData.URL.tokenize(';')[0], user: connection.metaData.userName]
            }
        }
        Connection connection = DriverManager.getConnection("${jdbc.url};LOCK_TIMEOUT=${lockTimeoutMillis}".toString(), jdbc.user, '')
        connection.autoCommit = false
        connection
    }

    private String tableName(Class entityClass) {
        manager.sessionFactory.metamodel.entityPersister(entityClass).tableName
    }
}

@Entity
class Hibernate5RefreshLockBook {
    Long id
    Long version
    String title
}

@Entity
class Hibernate5RefreshLockRoutedBook {
    Long id
    Long version
    String title

    static mapping = {
        datasource 'ALL'
    }
}

@Entity
class Hibernate5RefreshLockNonversionedBook {
    Long id
    String title

    static mapping = {
        version false
    }
}

@Entity
class Hibernate5RefreshLockEmbeddedBook {
    Long id
    Long version
    String title
    Hibernate5RefreshLockDetails details

    static embedded = ['details']
}

@DirtyCheck
class Hibernate5RefreshLockDetails {
    String summary
    String language
}

@Entity
class Hibernate5RefreshLockCascadeParent {
    Long id
    Long version
    String title
    Hibernate5RefreshLockCascadeChild child

    static mapping = {
        child cascade: 'all', lazy: false
    }
}

@Entity
class Hibernate5RefreshLockCascadeChild {
    Long id
    Long version
    String title
}

@Entity
class Hibernate5RefreshLockCollectionParent {
    Long id
    Long version
    String title

    static hasMany = [children: Hibernate5RefreshLockCascadeChild]

    static mapping = {
        children cascade: 'all', lazy: false
    }
}

@Entity
class Hibernate5RefreshLockEmbeddedOwner {
    Long id
    Long version
    String title
    Hibernate5RefreshLockOwnerDetails details

    static embedded = ['details']
}

@DirtyCheck
class Hibernate5RefreshLockOwnerDetails {
    String note
    Hibernate5RefreshLockCascadeChild child

    static mapping = {
        child cascade: 'all'
    }
}

@Entity
class Hibernate5RefreshLockJoinedRoot {
    Long id
    Long version
    String title

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class Hibernate5RefreshLockJoinedSub extends Hibernate5RefreshLockJoinedRoot {
    String extra
}

@Entity
class Hibernate5RefreshLockUnionRoot {
    Long id
    Long version
    String title

    static mapping = {
        tablePerConcreteClass true
        id generator: 'increment'
    }
}

@Entity
class Hibernate5RefreshLockUnionSub extends Hibernate5RefreshLockUnionRoot {
    String extra
}
