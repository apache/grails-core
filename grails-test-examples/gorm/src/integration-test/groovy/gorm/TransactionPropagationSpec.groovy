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
package gorm

import spock.lang.Specification

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.IllegalTransactionStateException

/**
 * Tests for service transaction management including:
 * - Default transactional behavior
 * - Transaction rollback on exception
 * - Read-only transactions
 * - Propagation modes (REQUIRES_NEW, MANDATORY, SUPPORTS, NOT_SUPPORTED)
 * - rollbackFor and noRollbackFor
 * - Programmatic transactions
 */
@Integration
class TransactionPropagationSpec extends Specification {

    @Autowired
    TransactionTestService transactionTestService

    def setup() {
        // Clean up before each test - delete books first due to FK constraint
        Author.withNewTransaction {
            Book.executeUpdate('delete from Book')
            Author.executeUpdate('delete from Author')
        }
    }

    // ========== Default Transaction Tests ==========

    void "test default transaction commits on success"() {
        when: "creating an author in transaction"
        def author = transactionTestService.createAuthor('Test Author', 'test@example.com')

        then: "author is persisted"
        author.id != null
        Author.withNewTransaction {
            Author.count() == 1 && Author.findByName('Test Author') != null
        }
    }

    void "test transaction rolls back on exception"() {
        when: "creating author with exception"
        transactionTestService.createAuthorWithException('Rollback Author', 'rollback@example.com')

        then: "exception thrown and transaction rolled back"
        thrown(RuntimeException)
        Author.withNewTransaction {
            Author.count() == 0 && Author.findByName('Rollback Author') == null
        }
    }

    // ========== Read-Only Transaction Tests ==========

    void "test readOnly transaction for queries"() {
        given: "existing author"
        Author.withNewTransaction {
            new Author(name: 'Query Author', email: 'query@example.com').save(flush: true)
        }

        when: "querying with read-only transaction"
        def author = transactionTestService.findAuthorByName('Query Author')

        then: "author found"
        author != null
        author.name == 'Query Author'
    }

    void "test readOnly transaction returns list"() {
        given: "multiple authors"
        Author.withNewTransaction {
            new Author(name: 'Author 1', email: 'a1@example.com').save(flush: true)
            new Author(name: 'Author 2', email: 'a2@example.com').save(flush: true)
        }

        when: "listing all authors"
        def authors = transactionTestService.listAllAuthors()

        then: "all authors returned"
        authors.size() == 2
    }

    // ========== REQUIRES_NEW Propagation Tests ==========

    void "test REQUIRES_NEW creates independent transaction"() {
        when: "creating in new transaction"
        def author = transactionTestService.createAuthorInNewTransaction('New Tx Author', 'newtx@example.com')

        then: "author persisted"
        author.id != null
        Author.withNewTransaction {
            Author.count() == 1
        }
    }

    void "test REQUIRES_NEW survives outer transaction rollback"() {
        when: "outer transaction fails but inner REQUIRES_NEW succeeds"
        transactionTestService.outerMethodWithInnerRequiresNew('Outer', 'Inner')

        then: "outer exception thrown"
        thrown(RuntimeException)

        and: "inner author survived (created in REQUIRES_NEW)"
        Author.withNewTransaction {
            Author.findByName('Inner') != null
        }

        and: "outer author rolled back"
        Author.withNewTransaction {
            Author.findByName('Outer') == null
        }
    }

    // ========== MANDATORY Propagation Tests ==========

    void "test MANDATORY fails without existing transaction"() {
        when: "calling MANDATORY method without transaction - using NOT_SUPPORTED to suspend any existing tx"
        transactionTestService.executeWithoutTransaction {
            // Now there's no transaction
            transactionTestService.requiresExistingTransaction('Mandatory', 'mandatory@test.com')
        }

        then: "IllegalTransactionStateException thrown"
        thrown(IllegalTransactionStateException)
    }

    void "test MANDATORY succeeds with existing transaction"() {
        when: "calling MANDATORY method within transaction"
        def author = null
        Author.withNewTransaction {
            author = transactionTestService.requiresExistingTransaction('Mandatory OK', 'mandatory.ok@test.com')
        }

        then: "author created successfully"
        author != null
        Author.withNewTransaction {
            Author.findByName('Mandatory OK') != null
        }
    }

    // ========== SUPPORTS Propagation Tests ==========

    void "test SUPPORTS works with transaction"() {
        given: "existing author"
        Author.withNewTransaction {
            new Author(name: 'Supports Author', email: 'supports@example.com').save(flush: true)
        }

        when: "querying with SUPPORTS within a transaction"
        def author = null
        Author.withNewTransaction {
            author = transactionTestService.supportsTransaction('Supports Author')
        }

        then: "query succeeds"
        author != null
    }

    // ========== Rollback Rules Tests ==========

    void "test rollbackFor triggers rollback for specified exception"() {
        when: "method throws exception specified in rollbackFor"
        transactionTestService.createWithRollbackForIllegalArg('RollbackFor', true)

        then: "exception thrown and rolled back"
        thrown(IllegalArgumentException)
        Author.withNewTransaction {
            Author.count() == 0
        }
    }

    void "test rollbackFor commits when no exception"() {
        when: "method succeeds without exception"
        def author = transactionTestService.createWithRollbackForIllegalArg('NoRollback', false)

        then: "committed successfully"
        author.id != null
        Author.withNewTransaction {
            Author.count() == 1
        }
    }

    void "test noRollbackFor does not rollback for specified exception"() {
        when: "method throws exception in noRollbackFor"
        transactionTestService.createWithNoRollbackForIllegalState('NoRollbackFor', true)

        then: "exception thrown but data committed"
        thrown(IllegalStateException)
        
        // The author should be committed despite exception
        // Note: This depends on exception being thrown after flush
        Author.withNewTransaction {
            Author.findByName('NoRollbackFor') != null
        }
    }

    // ========== Programmatic Transaction Tests ==========

    void "test programmatic withTransaction"() {
        when: "using withTransaction block"
        def author = transactionTestService.programmaticTransaction('Programmatic', 'prog@test.com')

        then: "author created"
        author.id != null
        Author.withNewTransaction {
            Author.count() == 1
        }
    }

    void "test programmatic withNewTransaction"() {
        when: "using withNewTransaction block"
        def author = transactionTestService.programmaticNewTransaction('New Programmatic', 'newprog@test.com')

        then: "author created in new transaction"
        author.id != null
        Author.withNewTransaction {
            Author.count() == 1
        }
    }

    // ========== Nested Transaction Tests ==========

    void "test nested transaction calls in same service"() {
        when: "calling method that makes nested transactional calls"
        def authors = transactionTestService.nestedTransactionCalls('Nested1', 'Nested2')

        then: "both authors created"
        authors.size() == 2
        Author.withNewTransaction {
            Author.count() == 2 &&
            Author.findByName('Nested1') != null &&
            Author.findByName('Nested2') != null
        }
    }

    // ========== Non-Transactional Tests ==========

    void "test non-transactional method"() {
        when: "calling non-transactional method"
        def result = transactionTestService.nonTransactionalOperation()

        then: "method executes normally"
        result == "No transaction here"
    }
}
