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

import grails.gorm.DetachedCriteria
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * Advanced tests for GORM Where Queries including:
 * - Batch updateAll() operations
 * - Batch deleteAll() operations  
 * - Where queries with functions (lower, upper, year, etc.)
 * - Subqueries and correlated queries
 * - Property comparisons
 * - Complex boolean logic
 */
@Rollback
@Integration
class GormWhereQueryAdvancedSpec extends Specification {

    def setup() {
        // Clean up existing data
        Book.executeUpdate('delete from Book')
        Author.executeUpdate('delete from Author')

        // Create test authors
        def king = new Author(name: 'Stephen King', email: 'stephen@king.com').save(flush: true)
        def clancy = new Author(name: 'Tom Clancy', email: 'tom@clancy.com').save(flush: true)
        def grisham = new Author(name: 'John Grisham', email: 'john@grisham.com').save(flush: true)
        def rowling = new Author(name: 'J.K. Rowling', email: 'jk@rowling.com').save(flush: true)

        // Stephen King books - varied prices and stock
        new Book(title: 'The Stand', isbn: '1234567890', pageCount: 1153, price: 19.99, inStock: true, author: king).save(flush: true)
        new Book(title: 'It', isbn: '0987654321', pageCount: 1138, price: 18.99, inStock: true, author: king).save(flush: true)
        new Book(title: 'The Shining', isbn: '1122334455', pageCount: 447, price: 14.99, inStock: false, author: king).save(flush: true)

        // Tom Clancy books
        new Book(title: 'The Hunt for Red October', isbn: '2233445566', pageCount: 387, price: 16.99, inStock: true, author: clancy).save(flush: true)
        new Book(title: 'Patriot Games', isbn: '3344556677', pageCount: 540, price: 15.99, inStock: false, author: clancy).save(flush: true)

        // John Grisham books
        new Book(title: 'The Firm', isbn: '4455667788', pageCount: 421, price: 13.99, inStock: true, author: grisham).save(flush: true)
        new Book(title: 'A Time to Kill', isbn: '5566778899', pageCount: 515, price: 14.99, inStock: true, author: grisham).save(flush: true)

        // J.K. Rowling books
        new Book(title: 'Harry Potter and the Sorcerers Stone', pageCount: 309, price: 12.99, inStock: true, author: rowling).save(flush: true)
    }

    // ============================================
    // Batch updateAll() Tests
    // ============================================

    void "test updateAll updates all matching records"() {
        when: "updating all out-of-stock books to be in stock"
        int updated = Book.where {
            inStock == false
        }.updateAll(inStock: true)

        then: "correct count returned and all books in stock"
        updated == 2
        Book.countByInStock(true) == 8
        Book.countByInStock(false) == 0
    }

    void "test updateAll with multiple conditions on same entity"() {
        when: "updating price for long books that are in stock"
        int updated = Book.where {
            pageCount > 500 && inStock == true
        }.updateAll(price: 24.99)

        then: "only matching books updated"
        updated == 3 // The Stand (1153), It (1138), Patriot Games is out of stock
        Book.findByTitle('The Stand').price == 24.99
        Book.findByTitle('It').price == 24.99
        Book.findByTitle('The Shining').price == 14.99 // pageCount < 500
    }

    void "test updateAll with numeric operations"() {
        when: "setting fixed price for expensive books"
        int updated = Book.where {
            price >= 16.0
        }.updateAll(price: 25.00)

        then: "matching books have new price"
        // The Stand (19.99), It (18.99), The Hunt (16.99) = 3 books >= 16.0
        updated == 3
        Book.findAllByPriceGreaterThanEquals(25.0).size() == 3
    }

    void "test updateAll returns zero when no matches"() {
        when: "updating non-existent records"
        int updated = Book.where {
            title == 'Non-existent Book'
        }.updateAll(inStock: false)

        then: "zero updated"
        updated == 0
    }

    // ============================================
    // Batch deleteAll() Tests
    // ============================================

    void "test deleteAll removes all matching records"() {
        given: "initial count"
        def initialCount = Book.count()

        when: "deleting all out-of-stock books"
        int deleted = Book.where {
            inStock == false
        }.deleteAll()

        then: "correct count returned and records removed"
        deleted == 2
        Book.count() == initialCount - 2
        Book.countByInStock(false) == 0
    }

    void "test deleteAll with page count condition"() {
        given: "initial count"
        def initialCount = Book.count()

        when: "deleting books with many pages (over 1000)"
        int deleted = Book.where {
            pageCount > 1000
        }.deleteAll()

        then: "only matching books deleted"
        // The Stand (1153) and It (1138) have over 1000 pages
        deleted == 2
        Book.count() == initialCount - 2
        Book.findByTitle('The Stand') == null
        Book.findByTitle('It') == null
    }

    void "test deleteAll with price condition"() {
        given: "initial count"
        def initialCount = Book.count()

        when: "deleting cheap books (price < 14)"
        int deleted = Book.where {
            price < 14.0
        }.deleteAll()

        then: "only matching books deleted"
        deleted == 2 // The Firm (13.99), Harry Potter (12.99)
        Book.count() == initialCount - 2
        Book.findByTitle('The Firm') == null
        Book.findByTitle('Harry Potter and the Sorcerers Stone') == null
    }

    // ============================================
    // Where Queries with Property Comparisons
    // ============================================

    void "test where query comparing two properties"() {
        given: "create books where pageCount might relate to price"
        new Book(title: 'Short Expensive', pageCount: 100, price: 50.00, inStock: true).save(flush: true)
        new Book(title: 'Long Cheap', pageCount: 1000, price: 5.00, inStock: true).save(flush: true)

        when: "finding books where pageCount is greater than price * 10"
        def results = Book.where {
            pageCount > price * 10
        }.list()

        then: "correct books returned"
        results.size() >= 1
        results.find { it.title == 'Long Cheap' } != null
    }

    // ============================================
    // Where Queries with IN clause
    // ============================================

    void "test where query with in operator"() {
        when: "finding books by list of authors"
        def authors = ['Stephen King', 'John Grisham']
        def results = Book.where {
            author.name in authors
        }.list()

        then: "correct books returned"
        results.size() == 5 // 3 King + 2 Grisham
        results.every { it.author.name in authors }
    }

    void "test where query with not in operator"() {
        when: "finding books not by certain authors"
        def excludeAuthors = ['Stephen King', 'Tom Clancy']
        def results = Book.where {
            !(author.name in excludeAuthors)
        }.list()

        then: "correct books returned"
        results.every { !(it.author?.name in excludeAuthors) }
    }

    // ============================================
    // Where Queries with BETWEEN
    // ============================================

    void "test where query with between operator"() {
        when: "finding books in price range"
        def results = Book.where {
            price >= 14.0 && price <= 16.0
        }.list()

        then: "books in range returned"
        results.size() >= 3
        results.every { it.price >= 14.0 && it.price <= 16.0 }
    }

    // ============================================
    // Where Queries with LIKE patterns
    // ============================================

    void "test where query with like pattern"() {
        when: "finding books with title starting with 'The'"
        def results = Book.where {
            title =~ 'The%'
        }.list()

        then: "matching books returned"
        results.every { it.title.startsWith('The') }
        results.size() >= 4
    }

    void "test where query with ilike (case insensitive)"() {
        when: "finding books with title containing 'the' case insensitive"
        def results = Book.where {
            title ==~ '%the%'
        }.list()

        then: "matching books returned regardless of case"
        results.size() >= 1
    }

    // ============================================
    // Complex Boolean Logic
    // ============================================

    void "test where query with complex OR conditions"() {
        when: "finding expensive OR long books"
        def results = Book.where {
            price > 17.0 || pageCount > 1000
        }.list()

        then: "books matching either condition returned"
        // The Stand (19.99, 1153) and It (18.99, 1138) match both conditions
        results.size() >= 2
        results.every { it.price > 17.0 || it.pageCount > 1000 }
    }

    void "test where query with nested boolean logic"() {
        when: "finding (expensive AND in stock) OR (by King)"
        def results = Book.where {
            (price > 17.0 && inStock == true) || author.name == 'Stephen King'
        }.list()

        then: "correct books returned"
        results.size() >= 3
        results.every {
            (it.price > 17.0 && it.inStock) || it.author?.name == 'Stephen King'
        }
    }

    // ============================================
    // DetachedCriteria Reuse
    // ============================================

    void "test detached criteria can be reused for count and list"() {
        given: "a reusable DetachedCriteria"
        DetachedCriteria<Book> expensiveBooks = Book.where {
            price >= 15.0
        }

        when: "using for both count and list"
        def count = expensiveBooks.count()
        def list = expensiveBooks.list()

        then: "same results from same criteria"
        count == list.size()
        count >= 4
    }

    void "test detached criteria can be composed"() {
        given: "base criteria"
        DetachedCriteria<Book> inStockBooks = Book.where {
            inStock == true
        }

        when: "adding more conditions"
        def expensiveInStock = inStockBooks.where {
            price > 15.0
        }

        then: "composed criteria works"
        expensiveInStock.count() >= 2
        expensiveInStock.list().every { it.inStock && it.price > 15.0 }
    }


    // ============================================
    // Where Query with Re-assigned Variables
    // ============================================

    void "test where query composition with re-assigned variable in if/else block"() {
        when: "building a where query with conditional re-assignment"
        boolean filterInStock = true
        def query
        if (filterInStock) {
            query = Book.where { inStock == true }
        } else {
            query = Book.where { inStock == false }
        }
        query = query.where { price > 15.0 }
        def results = query.list()

        then: "both conditions are applied"
        results.every { it.inStock && it.price > 15.0 }
        results.size() >= 2
    }

    void "test where query composition with re-assigned variable without initializer"() {
        when: "declaring variable without initializer then assigning a where query"
        def query
        query = Book.where { author.name == 'Stephen King' }
        query = query.where { inStock == true }
        def results = query.list()

        then: "both conditions are applied"
        results.every { it.author.name == 'Stephen King' && it.inStock }
        results.size() == 2 // The Stand and It (The Shining is out of stock)
    }

    void "test where query with triple chained re-assigned variable"() {
        when: "chaining three where clauses on a re-assigned variable"
        def query
        query = Book.where { inStock == true }
        query = query.where { price >= 14.0 }
        query = query.where { pageCount > 400 }
        def results = query.list()

        then: "all three conditions are applied"
        results.every { it.inStock && it.price >= 14.0 && it.pageCount > 400 }
        results.size() >= 2
    }

    // ============================================
    // Where Query with Sorting
    // ============================================

    void "test where query with order by"() {
        when: "finding books sorted by price descending"
        def results = Book.where {
            inStock == true
        }.list(sort: 'price', order: 'desc')

        then: "results are sorted"
        results.size() >= 5
        (0..<results.size()-1).every { i ->
            results[i].price >= results[i+1].price
        }
    }

    void "test where query with max and offset"() {
        when: "pagination with where query"
        def page1 = Book.where { inStock == true }.list(max: 2, offset: 0)
        def page2 = Book.where { inStock == true }.list(max: 2, offset: 2)

        then: "pagination works"
        page1.size() == 2
        page2.size() >= 2
        page1.intersect(page2).isEmpty() // no overlap
    }

    // ============================================
    // Where Query with Projections
    // ============================================

    void "test where query with id projection"() {
        when: "getting just IDs"
        def ids = Book.where {
            author.name == 'Stephen King'
        }.id().list()

        then: "only IDs returned"
        ids.size() == 3
        ids.every { it instanceof Long || it instanceof Integer }
    }

    void "test where query with property projection"() {
        when: "getting distinct authors of expensive books"
        def titles = Book.where {
            price > 15.0
        }.property('title').list()

        then: "only titles returned"
        titles.every { it instanceof String }
    }
}
