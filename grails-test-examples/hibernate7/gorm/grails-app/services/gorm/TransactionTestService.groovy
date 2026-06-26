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

import grails.gorm.transactions.NotTransactional
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional

import org.springframework.transaction.annotation.Propagation

/**
 * Service for testing various transaction behaviors and propagation modes.
 */
@Transactional
class TransactionTestService {

    /**
     * Default transactional method - commits on success.
     */
    def createAuthor(String name, String email) {
        def author = new Author(name: name, email: email)
        author.save(flush: true, failOnError: true)
        return author
    }

    /**
     * Transaction that will be rolled back due to exception.
     */
    def createAuthorWithException(String name, String email) {
        def author = new Author(name: name, email: email)
        author.save(flush: true, failOnError: true)
        throw new RuntimeException("Intentional exception for rollback test")
    }

    /**
     * Read-only transaction - optimized for queries.
     */
    @ReadOnly
    def findAuthorByName(String name) {
        Author.findByName(name)
    }

    /**
     * Read-only transaction listing all authors.
     */
    @ReadOnly
    def listAllAuthors() {
        Author.list()
    }

    /**
     * REQUIRES_NEW propagation - always creates new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    def createAuthorInNewTransaction(String name, String email) {
        def author = new Author(name: name, email: email)
        author.save(flush: true, failOnError: true)
        return author
    }

    /**
     * Method that calls another transactional method with REQUIRES_NEW.
     * The inner transaction should commit even if outer fails.
     */
    def outerMethodWithInnerRequiresNew(String outerName, String innerName) {
        // Create author in outer transaction
        def outerAuthor = new Author(name: outerName, email: "${outerName.toLowerCase()}@test.com")
        outerAuthor.save(flush: true, failOnError: true)

        // Create author in new transaction (should survive outer rollback)
        createAuthorInNewTransaction(innerName, "${innerName.toLowerCase()}@test.com")

        // Throw exception to rollback outer transaction
        throw new RuntimeException("Outer transaction fails")
    }

    /**
     * NOT_SUPPORTED propagation - suspends current transaction.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    def executeWithoutTransaction(Closure action) {
        action.call()
    }

    /**
     * MANDATORY propagation - requires existing transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    def requiresExistingTransaction(String name, String email) {
        def author = new Author(name: name, email: email)
        author.save(flush: true, failOnError: true)
        return author
    }

    /**
     * SUPPORTS propagation - uses transaction if available.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    def supportsTransaction(String name) {
        Author.findByName(name)
    }

    /**
     * Transaction with rollback for specific exception.
     */
    @Transactional(rollbackFor = IllegalArgumentException)
    def createWithRollbackForIllegalArg(String name, boolean throwException) {
        def author = new Author(name: name, email: "${name.toLowerCase()}@test.com")
        author.save(flush: true, failOnError: true)
        
        if (throwException) {
            throw new IllegalArgumentException("Rollback for this")
        }
        return author
    }

    /**
     * Transaction with noRollbackFor specific exception.
     */
    @Transactional(noRollbackFor = IllegalStateException)
    def createWithNoRollbackForIllegalState(String name, boolean throwException) {
        def author = new Author(name: name, email: "${name.toLowerCase()}@test.com")
        author.save(flush: true, failOnError: true)
        
        if (throwException) {
            throw new IllegalStateException("No rollback for this")
        }
        return author
    }

    /**
     * Non-transactional method.
     */
    @NotTransactional
    def nonTransactionalOperation() {
        return "No transaction here"
    }

    /**
     * Method using withTransaction programmatically.
     */
    @NotTransactional
    def programmaticTransaction(String name, String email) {
        Author.withTransaction { status ->
            def author = new Author(name: name, email: email)
            author.save(flush: true, failOnError: true)
            return author
        }
    }

    /**
     * Method using withNewTransaction programmatically.
     */
    @NotTransactional
    def programmaticNewTransaction(String name, String email) {
        Author.withNewTransaction { status ->
            def author = new Author(name: name, email: email)
            author.save(flush: true, failOnError: true)
            return author
        }
    }

    /**
     * Nested transaction calls for testing propagation.
     */
    def nestedTransactionCalls(String name1, String name2) {
        def author1 = createAuthor(name1, "${name1.toLowerCase()}@test.com")
        def author2 = createAuthor(name2, "${name2.toLowerCase()}@test.com")
        return [author1, author2]
    }
}
