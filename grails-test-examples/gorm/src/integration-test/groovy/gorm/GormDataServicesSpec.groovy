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
import spock.lang.Unroll

import org.springframework.beans.factory.annotation.Autowired

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * Tests for GORM Data Services - abstract class-based services with
 * automatically generated implementations for CRUD and finder methods.
 *
 * Data Services are a powerful feature of GORM that generates
 * service implementations at compile time based on method signatures.
 */
@Rollback
@Integration
class GormDataServicesSpec extends Specification {

    @Autowired
    BookDataService bookDataService

    def setup() {
        // Clean up and create fresh test data
        Book.executeUpdate('delete from Book')
        Author.executeUpdate('delete from Author')

        def author = new Author(name: 'Stephen King', email: 'stephen@king.com').save(flush: true)

        new Book(title: 'The Stand', isbn: '1234567890', pageCount: 1153, price: 19.99, inStock: true, author: author).save(flush: true)
        new Book(title: 'It', isbn: '0987654321', pageCount: 1138, price: 18.99, inStock: true, author: author).save(flush: true)
        new Book(title: 'The Shining', isbn: '1122334455', pageCount: 447, price: 14.99, inStock: false, author: author).save(flush: true)
        new Book(title: 'Carrie', isbn: '5566778899', pageCount: 199, price: 12.99, inStock: true).save(flush: true)
        new Book(title: 'Salem\'s Lot', pageCount: 439, price: 15.99, inStock: false).save(flush: true)
    }

    // ============================================
    // Basic CRUD Operations Tests
    // ============================================

    void "test get by id"() {
        given: "an existing book"
        def book = Book.findByTitle('The Stand')

        when: "retrieving by id through data service"
        def result = bookDataService.get(book.id)

        then: "the book is found"
        result != null
        result.title == 'The Stand'
        result.isbn == '1234567890'
    }

    void "test list with pagination"() {
        when: "listing books with pagination"
        def results = bookDataService.list([max: 2, offset: 0, sort: 'title'])

        then: "correct number of results"
        results.size() == 2
    }

    void "test count"() {
        expect: "correct count of books"
        bookDataService.count() == 5
    }

    void "test save new book"() {
        given: "a new book"
        def book = new Book(title: 'Doctor Sleep', pageCount: 531, price: 16.99, inStock: true)

        when: "saving through data service"
        def saved = bookDataService.save(book)

        then: "book is persisted"
        saved.id != null
        saved.title == 'Doctor Sleep'
        bookDataService.count() == 6
    }

    void "test delete"() {
        given: "an existing book"
        def book = Book.findByTitle('Carrie')
        def bookId = book.id

        when: "deleting through data service"
        bookDataService.delete(bookId)
        Book.withSession { it.flush(); it.clear() }

        then: "book is removed"
        Book.get(bookId) == null
        bookDataService.findByTitle('Carrie') == null
    }

    // ============================================
    // Dynamic Finder Tests
    // ============================================

    void "test findByTitle"() {
        expect: "finding by title works"
        bookDataService.findByTitle('It') != null
        bookDataService.findByTitle('It').isbn == '0987654321'
        bookDataService.findByTitle('NonExistent') == null
    }

    void "test findAllByTitle"() {
        when: "finding all by title"
        def results = bookDataService.findAllByTitle('The Stand')

        then: "returns matching books"
        results.size() == 1
        results[0].title == 'The Stand'
    }

    void "test findAllByInStock"() {
        when: "finding all in stock books"
        def inStock = bookDataService.findAllByInStock(true)
        def outOfStock = bookDataService.findAllByInStock(false)

        then: "correct results"
        inStock.size() == 3
        outOfStock.size() == 2
    }

    void "test findAllByInStock with manual ordering"() {
        when: "finding in stock books and sorting manually"
        def results = bookDataService.findAllByInStock(true).sort { it.title }

        then: "results can be ordered"
        results.size() == 3
        results[0].title < results[1].title
        results[1].title < results[2].title
    }

    // ============================================
    // Count Queries
    // ============================================

    void "test countByInStock"() {
        expect: "count queries work"
        bookDataService.countByInStock(true) == 3
        bookDataService.countByInStock(false) == 2
    }

    // ============================================
    // Manual Implementation Tests
    // ============================================

    void "test existsByTitle (manual implementation)"() {
        expect: "exists queries work via manual implementation"
        bookDataService.existsByTitle('It') == true
        bookDataService.existsByTitle('NonExistent') == false
    }

    void "test findByIsbnValue for nullable field"() {
        expect: "finding by nullable isbn works via manual HQL"
        bookDataService.findByIsbnValue('1234567890') != null
        bookDataService.findByIsbnValue('1234567890').title == 'The Stand'
        bookDataService.findByIsbnValue('0000000000') == null
        bookDataService.findByIsbnValue(null) == null
    }

    void "test countByTitleLike"() {
        expect: "count with like pattern works"
        bookDataService.countByTitleLike('The%') == 2  // 'The Stand', 'The Shining'
        bookDataService.countByTitleLike('%Lot') == 1  // "Salem's Lot"
    }

    void "test findByTitleAndOptionalIsbn"() {
        expect: "finding with optional isbn works"
        bookDataService.findByTitleAndOptionalIsbn('The Stand', '1234567890') != null
        bookDataService.findByTitleAndOptionalIsbn('The Stand', 'wrong-isbn') == null
        bookDataService.findByTitleAndOptionalIsbn('The Stand', null)?.title == 'The Stand'
    }

    // ============================================
    // Custom @Where Query Tests
    // ============================================

    void "test findByTitlePattern with regex-like pattern"() {
        when: "searching with pattern"
        def results = bookDataService.findByTitlePattern('The%')

        then: "matching books are found"
        results.size() == 2
        results*.title.every { it.startsWith('The') }
    }

    void "test findByPageRange"() {
        when: "searching by page range"
        def results = bookDataService.findByPageRange(400, 500)

        then: "books in range are found"
        results.size() == 2  // The Shining (447), Salem's Lot (439)
        results.every { it.pageCount >= 400 && it.pageCount <= 500 }
    }

    void "test findAffordableInStockBooks"() {
        when: "searching for affordable in-stock books"
        def results = bookDataService.findAffordableInStockBooks(15.00)

        then: "matching books are found"
        results.size() == 1  // Only Carrie at 12.99 is in stock and under 15
        results[0].title == 'Carrie'
    }

    // ============================================
    // Custom @Query (HQL) Tests
    // ============================================

    void "test searchByTitleHql"() {
        when: "searching with HQL pattern"
        def results = bookDataService.searchByTitleHql('The%')

        then: "results are found and ordered"
        results.size() == 2
        // Ordered by title
        results[0].title == 'The Shining'
        results[1].title == 'The Stand'
    }

    void "test countBooksWithAuthor"() {
        expect: "count books with author works"
        bookDataService.countBooksWithAuthor() == 3  // Stand, It, Shining
    }

    void "test findAllDistinctTitles"() {
        when: "finding distinct titles"
        def titles = bookDataService.findAllDistinctTitles()

        then: "all titles returned"
        titles.size() == 5
        titles.containsAll(['The Stand', 'It', 'The Shining', 'Carrie', 'Salem\'s Lot'])
    }

    // ============================================
    // Update Operations
    // ============================================

    void "test updateStockStatus"() {
        given: "an out of stock book"
        def book = Book.findByTitle('The Shining')
        assert book.inStock == false

        when: "updating stock status"
        def updated = bookDataService.updateStockStatus(book.id, true)
        Book.withSession { it.flush(); it.clear() }
        book = Book.findByTitle('The Shining')

        then: "update is applied"
        updated == 1
        book.inStock == true
    }

    // ============================================
    // Delete Operations
    // ============================================

    void "test deleteByTitle"() {
        when: "deleting by title"
        def deleted = bookDataService.deleteByTitle('Carrie')

        then: "book is deleted"
        deleted == 1
        Book.findByTitle('Carrie') == null
        bookDataService.count() == 4
    }

    // ============================================
    // Projection Queries
    // ============================================

    void "test findTitlesAndPrices projection"() {
        when: "querying title and price projection"
        def results = bookDataService.findTitlesAndPrices()

        then: "projections returned as arrays"
        results.size() == 5
        results.every { it instanceof Object[] && it.length == 2 }
        results.every { it[0] instanceof String && it[1] instanceof BigDecimal }
    }

    void "test findAveragePrice"() {
        when: "calculating average price"
        def avg = bookDataService.findAveragePrice()

        then: "average is calculated"
        avg != null
        // Average of 19.99, 18.99, 14.99, 12.99, 15.99 = 16.59
        Math.abs(avg - 16.59) < 0.01
    }

    void "test findMaxPageCount"() {
        expect: "max page count is found"
        bookDataService.findMaxPageCount() == 1153  // The Stand
    }

    // ============================================
    // Edge Cases and Error Handling
    // ============================================

    void "test get non-existent id returns null"() {
        expect: "null returned for non-existent id"
        bookDataService.get(999999L) == null
    }

    @Unroll
    void "test dynamic finder with various inputs: title=#title, expected=#expectedFound"() {
        expect: "finder returns expected result"
        (bookDataService.findByTitle(title) != null) == expectedFound

        where:
        title           | expectedFound
        'The Stand'     | true
        'It'            | true
        ''              | false
        'NONEXISTENT'   | false
    }
}
