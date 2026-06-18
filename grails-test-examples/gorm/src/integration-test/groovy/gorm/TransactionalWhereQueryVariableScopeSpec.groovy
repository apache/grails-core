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

import spock.lang.Issue
import spock.lang.Specification

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

import org.springframework.beans.factory.annotation.Autowired

/**
 * Functional tests for where queries with local variable names that match
 * domain property names inside @Transactional methods.
 *
 * Verifies the fix for https://github.com/apache/grails-core/issues/11464
 * where the interaction between DetachedCriteriaTransformer and
 * TransactionalTransform caused VariableScopeVisitor to rewrite
 * delegate.property(closure) calls to variable.call(closure) when
 * a local variable with the same name existed in scope.
 */
@Integration
@Rollback
@Issue('https://github.com/apache/grails-core/issues/11464')
class TransactionalWhereQueryVariableScopeSpec extends Specification {

    @Autowired
    WhereQueryVariableScopeService whereQueryVariableScopeService

    def setup() {
        Book.executeUpdate('delete from Book')
        Author.executeUpdate('delete from Author')

        def king = new Author(name: 'Stephen King', email: 'stephen@king.com').save(flush: true)
        def clancy = new Author(name: 'Tom Clancy', email: 'tom@clancy.com').save(flush: true)

        new Book(title: 'The Stand', isbn: '1234567890', pageCount: 1153, price: 19.99, inStock: true, author: king).save(flush: true)
        new Book(title: 'It', isbn: '0987654321', pageCount: 1138, price: 18.99, inStock: true, author: king).save(flush: true)
        new Book(title: 'The Shining', isbn: '1122334455', pageCount: 447, price: 14.99, inStock: false, author: king).save(flush: true)
        new Book(title: 'The Hunt for Red October', isbn: '2233445566', pageCount: 387, price: 16.99, inStock: true, author: clancy).save(flush: true)
        new Book(title: 'Patriot Games', isbn: '3344556677', pageCount: 540, price: 15.99, inStock: false, author: clancy).save(flush: true)
    }

    void "test where query with local variable named 'title' matching domain property in @Transactional"() {
        when: "querying with a variable named 'title' that matches Book.title"
        def results = whereQueryVariableScopeService.findBooksByTitle('The Stand')

        then: "the query correctly uses the local variable value"
        results.size() == 1
        results[0].title == 'The Stand'
    }

    void "test where query with local variable named 'author' matching association in @Transactional"() {
        given: "an author instance"
        def king = Author.findByName('Stephen King')

        when: "querying with a variable named 'author' that matches Book.author"
        def results = whereQueryVariableScopeService.findBooksByAuthor(king)

        then: "the query correctly uses the local variable value"
        results.size() == 3
        results.every { it.author.name == 'Stephen King' }
    }

    void "test where query with association traversal and variable name collision in @Transactional"() {
        when: "querying by author name with a local variable named 'author' in scope"
        def results = whereQueryVariableScopeService.findBooksByAuthorName('Tom Clancy')

        then: "the query correctly filters by author name"
        results.size() == 2
        results.every { it.author.name == 'Tom Clancy' }
    }

    void "test where query with multiple variable name collisions in @Transactional"() {
        when: "querying with both 'title' and 'inStock' variables matching domain properties"
        def results = whereQueryVariableScopeService.findBooksWithMultipleCollisions('It', true)

        then: "both criteria are correctly applied"
        results.size() == 1
        results[0].title == 'It'
        results[0].inStock == true
    }

    void "test where query with variable name collision in @Transactional(readOnly)"() {
        when: "querying in a read-only transactional method"
        def results = whereQueryVariableScopeService.findBooksByTitleReadOnly('Patriot Games')

        then: "the query correctly uses the local variable value"
        results.size() == 1
        results[0].title == 'Patriot Games'
    }
}