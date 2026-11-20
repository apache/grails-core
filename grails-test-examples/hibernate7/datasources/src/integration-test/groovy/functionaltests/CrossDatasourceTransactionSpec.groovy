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

package functionaltests

import datasources.Application
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import ds1.Book
import ds2.Book as SecondBook

/**
 * Integration tests for cross-datasource transaction scenarios.
 * Tests chained transaction manager behavior, transaction propagation,
 * and complex multi-datasource operations.
 */
@Integration(applicationClass = Application)
@Rollback
class CrossDatasourceTransactionSpec extends Specification {

    // ========== Chained Transaction Manager Tests ==========

    def "chained transaction manager coordinates transactions across datasources"() {
        given: "initial counts"
        def initialPrimaryCount = Book.count()
        def initialSecondaryCount
        SecondBook.withTransaction {
            initialSecondaryCount = SecondBook.count()
        }

        when: "creating books in both datasources within coordinated transaction"
        Book.withTransaction {
            new Book(title: "Chained Primary").save(flush: true)

            SecondBook.withTransaction {
                new SecondBook(title: "Chained Secondary").save(flush: true)
            }
        }

        then: "both books are saved"
        Book.count() == initialPrimaryCount + 1
        SecondBook.withTransaction { SecondBook.count() } == initialSecondaryCount + 1
    }

    def "nested transactions work correctly"() {
        when: "using nested transactions"
        def primaryId
        def secondaryId

        Book.withTransaction {
            def primary = new Book(title: "Outer Transaction").save(flush: true)
            primaryId = primary.id

            Book.withNewTransaction {
                new Book(title: "Inner New Transaction").save(flush: true)
            }

            SecondBook.withTransaction {
                def secondary = new SecondBook(title: "Nested Secondary").save(flush: true)
                secondaryId = secondary.id
            }
        }

        then: "all books are saved"
        Book.get(primaryId) != null
        Book.findByTitle("Inner New Transaction") != null
        SecondBook.withTransaction { SecondBook.get(secondaryId) } != null
    }

    // ========== Transaction Propagation Tests ==========

    def "REQUIRES_NEW creates independent transaction"() {
        given: "a book that will be saved in outer transaction"
        def outerBook = new Book(title: "Outer Book").save(flush: true)

        when: "creating book in REQUIRES_NEW transaction that fails"
        try {
            Book.withNewTransaction { status ->
                new Book(title: "New Transaction Book").save(flush: true)
                // Force rollback of inner transaction only
                status.setRollbackOnly()
            }
        } catch (ignored) {
            // Expected
        }

        then: "outer book still exists"
        Book.get(outerBook.id) != null

        and: "new transaction book was not saved (rolled back)"
        Book.findByTitle("New Transaction Book") == null
    }

    def "read-only transactions work correctly"() {
        given: "existing books"
        new Book(title: "ReadOnly Test 1").save(flush: true)
        new Book(title: "ReadOnly Test 2").save(flush: true)

        when: "reading in read-only transaction"
        def count
        Book.withTransaction([readOnly: true]) {
            count = Book.countByTitleLike("ReadOnly Test%")
        }

        then: "read succeeds"
        count == 2
    }

    // ========== Error Handling Across Datasources ==========

    def "error in primary datasource does not affect already-committed secondary"() {
        given: "unique titles for this test"
        def uniqueId = UUID.randomUUID().toString()
        def secondaryTitle = "Saved Before Error ${uniqueId}"
        
        and: "secondary book saved first in separate transaction"
        SecondBook.withNewTransaction {
            new SecondBook(title: secondaryTitle).save(flush: true)
        }

        when: "error occurs in primary after secondary commit"
        try {
            Book.withNewTransaction {
                new Book(title: "Will Fail ${uniqueId}").save(flush: true)
                throw new RuntimeException("Simulated failure")
            }
        } catch (RuntimeException ignored) {
            // Expected
        }

        then: "secondary book still exists (was committed before error)"
        SecondBook.withNewTransaction { SecondBook.findByTitle(secondaryTitle) } != null
    }

    def "error in secondary datasource can be caught without affecting primary"() {
        given: "unique id for this test"
        def uniqueId = UUID.randomUUID().toString()
        def primaryTitle = "Primary Saved First ${uniqueId}"
        
        and: "primary book saved first in separate transaction"
        def primaryBookId
        Book.withNewTransaction {
            def primaryBook = new Book(title: primaryTitle).save(flush: true)
            primaryBookId = primaryBook.id
        }

        when: "error occurs in secondary"
        def secondaryFailed = false
        try {
            SecondBook.withNewTransaction {
                new SecondBook(title: "Will Fail ${uniqueId}").save(flush: true)
                throw new RuntimeException("Secondary failure")
            }
        } catch (RuntimeException e) {
            secondaryFailed = true
        }

        then: "secondary failed"
        secondaryFailed

        and: "primary book still exists"
        Book.withNewTransaction { Book.get(primaryBookId) } != null
    }

    // ========== Session Management Tests ==========

    def "withNewSession creates new Hibernate session"() {
        when: "using withNewSession"
        def sessionId1
        def sessionId2

        Book.withSession { session ->
            sessionId1 = System.identityHashCode(session)
        }

        Book.withNewSession { session ->
            sessionId2 = System.identityHashCode(session)
        }

        then: "sessions are different"
        sessionId1 != sessionId2
    }

    def "flush mode can be controlled within session"() {
        when: "saving with manual flush"
        def book = new Book(title: "Manual Flush Test")
        book.save() // Not flushed yet

        then: "book has id after save (optimistic id generation)"
        book.id != null

        when: "explicitly flushing"
        book.save(flush: true)

        then: "book is now persisted"
        Book.get(book.id) != null
    }

    // ========== Detached Object Tests ==========

    def "detached objects can be reattached in different transactions"() {
        given: "unique id for this test"
        def uniqueId = UUID.randomUUID().toString()
        def originalTitle = "Will Be Detached ${uniqueId}"
        def modifiedTitle = "Modified After Detach ${uniqueId}"
        
        and: "a book created in a separate transaction"
        def detachedBookId
        Book.withNewTransaction {
            def detachedBook = new Book(title: originalTitle).save(flush: true)
            detachedBookId = detachedBook.id
        }

        when: "loading, modifying and merging in new transaction"
        Book.withNewTransaction {
            def detachedBook = Book.get(detachedBookId)
            detachedBook.title = modifiedTitle
            detachedBook.save(flush: true)
        }

        then: "changes are persisted"
        Book.withNewTransaction { Book.get(detachedBookId).title } == modifiedTitle
    }

    def "refresh reloads from database"() {
        given: "a saved book"
        def book = new Book(title: "Original Title").save(flush: true)
        def bookId = book.id

        when: "modifying without save and refreshing"
        book.title = "Modified But Not Saved"
        book.refresh()

        then: "original value is restored"
        book.title == "Original Title"
    }

    // ========== Multi-datasource Coordination Tests ==========

    def "operations on both datasources within single test method"() {
        when: "creating books in both datasources"
        new Book(title: "Primary Multi-DS").save(flush: true)
        SecondBook.withTransaction {
            new SecondBook(title: "Secondary Multi-DS").save(flush: true)
        }

        then: "both books exist"
        Book.findByTitle("Primary Multi-DS") != null
        SecondBook.withTransaction { SecondBook.findByTitle("Secondary Multi-DS") } != null
    }

    def "count operations are datasource-specific"() {
        given: "books in both datasources"
        5.times { i ->
            new Book(title: "Count Test P${i}").save(flush: true)
        }
        SecondBook.withTransaction {
            3.times { i ->
                new SecondBook(title: "Count Test S${i}").save(flush: true)
            }
        }

        expect: "counts are independent"
        Book.countByTitleLike("Count Test P%") == 5
        SecondBook.withTransaction { SecondBook.countByTitleLike("Count Test S%") } == 3
    }
}
