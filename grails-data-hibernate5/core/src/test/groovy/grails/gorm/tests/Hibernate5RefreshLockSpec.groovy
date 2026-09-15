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
import grails.gorm.dirty.checking.DirtyCheck

class Hibernate5RefreshLockSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(Hibernate5RefreshLockBook, Hibernate5RefreshLockRoutedBook,
                Hibernate5RefreshLockNonversionedBook, Hibernate5RefreshLockEmbeddedBook)
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

    void 'static lock(id, refresh: true) loads and locks an instance that is not in the session'() {
        given:
        Long id = new Hibernate5RefreshLockBook(title: 'original').save(flush: true, failOnError: true).id
        Hibernate5RefreshLockBook.withSession { it.clear() }

        when:
        def book = Hibernate5RefreshLockBook.lock(id, refresh: true)

        then:
        book != null
        Hibernate.isInitialized(book)
        book.title == 'original'
        book.version == 0
        Hibernate5RefreshLockBook.withSession { Session session ->
            session.contains(book) && session.getCurrentLockMode(book) == LockMode.PESSIMISTIC_WRITE
        }
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
        description   | args
        'empty map'   | [:]
        'lock: false' | [lock: false]
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
